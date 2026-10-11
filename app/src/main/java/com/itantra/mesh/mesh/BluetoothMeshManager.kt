package com.itantra.mesh.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.itantra.mesh.data.PreferencesRepository
import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.model.MeshPacket
import com.itantra.mesh.model.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BluetoothMeshManager(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope
) : MeshTransport {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Active direct peer connections: Bluetooth Address -> PeerConnection
    private val activeConnections = ConcurrentHashMap<String, PeerConnection>()

    // Pending connection attempts to prevent multiple simultaneous socket connects to same peer
    private val connectingDevices = ConcurrentHashMap.newKeySet<String>()

    private val _connectedPeersCount = MutableStateFlow(0)
    override val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    override val isReady: StateFlow<Boolean> = _isListening.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var discoveryManager: BleDiscoveryManager? = null

    // Routing engine callback
    override var onPacketReceivedFromPeer: ((MeshPacket, String) -> Unit)? = null
    override var onPeerConnected: ((MeshNode) -> Unit)? = null
    override var onPeerDisconnected: ((String) -> Unit)? = null

    init {
        discoveryManager = BleDiscoveryManager(
            context = context,
            bluetoothAdapter = bluetoothAdapter,
            localNodeId = prefs.nodeId,
            onPeerDiscovered = { device, discoveredNodeId, rssi ->
                handleDiscoveredPeer(device, discoveredNodeId, rssi)
            }
        )
    }

    override fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or disabled")
            return
        }
        startServerListening()
        discoveryManager?.start()
    }

    override fun stop() {
        discoveryManager?.stop()
        stopServerListening()
        disconnectAll()
    }

    private fun startServerListening() {
        if (acceptJob != null && acceptJob?.isActive == true) return

        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(
                    APP_NAME,
                    BleDiscoveryManager.MESH_SERVICE_UUID
                )
                _isListening.value = true
                Log.i(TAG, "BluetoothMesh Server listening on RFCOMM UUID: ${BleDiscoveryManager.MESH_SERVICE_UUID}")

                while (isActive && _isListening.value) {
                    try {
                        val socket = serverSocket?.accept()
                        if (socket != null) {
                            handleIncomingSocket(socket)
                        }
                    } catch (e: IOException) {
                        if (_isListening.value) {
                            Log.w(TAG, "Server socket accept error: ${e.message}")
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start RFCOMM server socket", e)
            } finally {
                _isListening.value = false
            }
        }
    }

    private fun stopServerListening() {
        _isListening.value = false
        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing server socket", e)
        }
        acceptJob?.cancel()
        acceptJob = null
    }

    private fun handleIncomingSocket(socket: BluetoothSocket) {
        val remoteAddress = socket.remoteDevice.address
        Log.i(TAG, "Incoming Bluetooth connection accepted from $remoteAddress")

        if (activeConnections.containsKey(remoteAddress)) {
            Log.d(TAG, "Already connected to $remoteAddress, closing duplicate")
            try { socket.close() } catch (e: Exception) {}
            return
        }

        val peer = PeerConnection(
            peerAddress = remoteAddress,
            socket = socket,
            scope = scope,
            onPacketReceived = { conn, packet ->
                onPacketReceivedFromPeer?.invoke(packet, conn.peerAddress)
            },
            onDisconnected = { conn ->
                handlePeerDisconnected(conn)
            }
        )

        activeConnections[remoteAddress] = peer
        peer.start()
        updatePeerCount()

        // Send an initial announce packet to identify ourselves
        sendNodeAnnounce(peer)
    }

    fun connectToDevice(device: BluetoothDevice) {
        val address = device.address
        if (activeConnections.containsKey(address) || connectingDevices.contains(address)) {
            return
        }

        connectingDevices.add(address)
        scope.launch(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                Log.d(TAG, "Initiating RFCOMM connection to $address (${device.name ?: "Unknown"})")
                // Insecure RFCOMM doesn't require OS pairing dialog
                socket = device.createInsecureRfcommSocketToServiceRecord(BleDiscoveryManager.MESH_SERVICE_UUID)

                // Cancel discovery before connecting for optimal bandwidth
                bluetoothAdapter?.cancelDiscovery()

                socket.connect()

                val peer = PeerConnection(
                    peerAddress = address,
                    socket = socket,
                    scope = scope,
                    onPacketReceived = { conn, packet ->
                        onPacketReceivedFromPeer?.invoke(packet, conn.peerAddress)
                    },
                    onDisconnected = { conn ->
                        handlePeerDisconnected(conn)
                    }
                )

                activeConnections[address] = peer
                peer.start()
                updatePeerCount()

                Log.i(TAG, "Connected successfully to peer $address")
                sendNodeAnnounce(peer)
            } catch (e: Exception) {
                Log.d(TAG, "Could not connect to $address: ${e.message}")
                try { socket?.close() } catch (ex: Exception) {}
            } finally {
                connectingDevices.remove(address)
            }
        }
    }

    private fun handleDiscoveredPeer(device: BluetoothDevice, discoveredNodeId: String?, rssi: Int?) {
        val address = device.address
        val node = MeshNode(
            nodeId = discoveredNodeId ?: address,   // full MAC address, not a derived shorthand
            displayName = device.name ?: "Nearby Peer",
            isDirectNeighbor = activeConnections.containsKey(address),
            bluetoothAddress = address,
            hopDistance = 1,
            lastSeenTimestamp = System.currentTimeMillis(),
            rssi = rssi
        )
        onPeerConnected?.invoke(node)

        if (!activeConnections.containsKey(address) && !connectingDevices.contains(address)) {
            connectToDevice(device)
        }
    }

    private fun handlePeerDisconnected(conn: PeerConnection) {
        val address = conn.peerAddress
        activeConnections.remove(address)
        updatePeerCount()
        Log.i(TAG, "Peer disconnected: $address (nodeId: ${conn.peerNodeId})")
        conn.peerNodeId?.let { onPeerDisconnected?.invoke(it) }
    }

    override fun broadcastPacket(packet: MeshPacket, excludeAddress: String?) {
        activeConnections.forEach { (address, connection) ->
            if (address != excludeAddress) {
                connection.send(packet)
            }
        }
    }

    override fun sendPacketToNode(targetNodeId: String, packet: MeshPacket): Boolean {
        // Find if directly connected
        val directPeer = activeConnections.values.firstOrNull { it.peerNodeId == targetNodeId }
        return if (directPeer != null) {
            directPeer.send(packet)
            true
        } else {
            // Flood through mesh to all active neighbors
            broadcastPacket(packet)
            false
        }
    }

    private fun sendNodeAnnounce(connection: PeerConnection) {
        val announcePacket = MeshPacket(
            type = PacketType.NODE_ANNOUNCE,
            senderId = prefs.nodeId,
            senderName = prefs.displayName,
            destinationId = MeshPacket.BROADCAST_DESTINATION,
            payload = "ANNOUNCE",
            ttl = 1 // 1 hop direct hello
        )
        connection.send(announcePacket)
    }

    fun triggerDiscovery() {
        discoveryManager?.startClassicDiscovery()
    }

    fun disconnectAll() {
        activeConnections.values.forEach { it.close() }
        activeConnections.clear()
        updatePeerCount()
    }

    private fun updatePeerCount() {
        _connectedPeersCount.value = activeConnections.size
    }

    override fun getActivePeerAddresses(): List<String> = activeConnections.keys.toList()

    companion object {
        private const val TAG = "BluetoothMeshManager"
        private const val APP_NAME = "iTantraMesh"
    }

    /**
     * Manually connect to a device by its raw Bluetooth MAC address (e.g. "AA:BB:CC:DD:EE:FF"),
     * bypassing discovery. Useful when a peer is known but not currently being advertised/scanned.
     *
     * Returns a [ManualConnectResult] describing whether the connection attempt could be started.
     * The actual socket connection still happens asynchronously on IO dispatcher, same as [connectToDevice].
     */
    fun connectToDeviceByAddress(address: String): ManualConnectResult {
        val normalizedAddress = address.trim().uppercase()

        if (!BluetoothAdapter.checkBluetoothAddress(normalizedAddress)) {
            return ManualConnectResult.INVALID_ADDRESS
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            return ManualConnectResult.BLUETOOTH_DISABLED
        }

        if (activeConnections.containsKey(normalizedAddress)) {
            return ManualConnectResult.ALREADY_CONNECTED
        }

        if (connectingDevices.contains(normalizedAddress)) {
            return ManualConnectResult.ALREADY_CONNECTING
        }

        val device = try {
            adapter.getRemoteDevice(normalizedAddress)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid Bluetooth address for manual connect: $normalizedAddress", e)
            return ManualConnectResult.INVALID_ADDRESS
        }

        // Optimistically register the node so it shows up immediately in the UI/topology,
        // even before the RFCOMM socket handshake completes.
        val manualNode = MeshNode(
            nodeId = normalizedAddress,
            displayName = device.name ?: "Manual Peer ($normalizedAddress)",
            isDirectNeighbor = false,
            bluetoothAddress = normalizedAddress,
            hopDistance = 1,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        onPeerConnected?.invoke(manualNode)

        connectToDevice(device)
        return ManualConnectResult.CONNECTING
    }

    enum class ManualConnectResult {
        CONNECTING,
        ALREADY_CONNECTED,
        ALREADY_CONNECTING,
        INVALID_ADDRESS,
        BLUETOOTH_DISABLED
    }
}
