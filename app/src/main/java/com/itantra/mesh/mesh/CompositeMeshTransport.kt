package com.itantra.mesh.mesh

import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.model.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Combines [BluetoothMeshManager] and [SerialLinkManager] into a single [MeshTransport] so
 * [MeshRoutingEngine] can treat "Bluetooth mesh" and "wired Serial link to a Pico" as one logical
 * link, active at the same time, rather than an exclusive either/or choice.
 *
 * Bluetooth is always started. Serial is opportunistic: it's started whenever the user hasn't
 * turned it off in Settings, and simply sits idle (watching for USB attach) until a device is
 * actually plugged in. The moment a serial device connects, traffic starts flowing over *both*
 * links simultaneously — nothing else needs to change, no mode switch, no restart.
 *
 * Peer addresses from the two transports never collide (Bluetooth uses MAC addresses, Serial
 * always uses the fixed [SerialLinkManager.SERIAL_PEER_ADDRESS]), so loop-avoidance via
 * `excludeAddress` continues to work correctly when fanning a packet out to both links.
 */
class CompositeMeshTransport(
    private val bluetooth: BluetoothMeshManager,
    private val serial: SerialLinkManager,
    private val scope: CoroutineScope,
    initialSerialLinkEnabled: Boolean = true
) : MeshTransport {

    private val _connectedPeersCount = MutableStateFlow(0)
    override val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** True once a Serial device is actively connected — surfaced for UI (status bar, radar). */
    val isSerialConnected: StateFlow<Boolean> get() = serial.isReady

    /** Direct Bluetooth peer count alone — surfaced for UI that wants the Bluetooth-only figure. */
    val bluetoothPeersCount: StateFlow<Int> get() = bluetooth.connectedPeersCount

    override var onPacketReceivedFromPeer: ((MeshPacket, String) -> Unit)? = null
    override var onPeerConnected: ((MeshNode) -> Unit)? = null
    override var onPeerDisconnected: ((String) -> Unit)? = null

    /** Whether the Serial link should be started at all; toggled from Settings at runtime. */
    var isSerialLinkEnabled: Boolean = initialSerialLinkEnabled
        private set

    init {
        bluetooth.onPacketReceivedFromPeer = { packet, address -> onPacketReceivedFromPeer?.invoke(packet, address) }
        bluetooth.onPeerConnected = { node -> onPeerConnected?.invoke(node) }
        bluetooth.onPeerDisconnected = { nodeId -> onPeerDisconnected?.invoke(nodeId) }

        serial.onPacketReceivedFromPeer = { packet, address -> onPacketReceivedFromPeer?.invoke(packet, address) }
        serial.onPeerConnected = { node -> onPeerConnected?.invoke(node) }
        serial.onPeerDisconnected = { nodeId -> onPeerDisconnected?.invoke(nodeId) }

        scope.launch {
            combine(bluetooth.connectedPeersCount, serial.connectedPeersCount) { bt, sr -> bt + sr }
                .collect { _connectedPeersCount.value = it }
        }
        scope.launch {
            combine(bluetooth.isReady, serial.isReady) { bt, sr -> bt || sr }
                .collect { _isReady.value = it }
        }
    }

    /** Always brings Bluetooth up; brings Serial up too unless the user disabled it in Settings. */
    override fun start() {
        bluetooth.start()
        if (isSerialLinkEnabled) {
            serial.start()
        }
    }

    override fun stop() {
        bluetooth.stop()
        serial.stop()
    }

    /**
     * Turns the Serial link on/off at runtime without touching Bluetooth or requiring a
     * transport swap. Called from Settings when the user flips the "USB Serial Link" switch.
     * Safe to call before [start] too (e.g. during app init) — it just records the preference in
     * that case, since [start] itself checks [isSerialLinkEnabled].
     */
    fun setSerialLinkEnabled(enabled: Boolean, startStopImmediately: Boolean = true) {
        isSerialLinkEnabled = enabled
        if (!startStopImmediately) return
        if (enabled) {
            serial.start()
        } else {
            serial.stop()
        }
    }

    override fun broadcastPacket(packet: MeshPacket, excludeAddress: String?) {
        // Each sub-transport only excludes the send if excludeAddress matches one of its own
        // peers, so it's safe to always fan out to both — Bluetooth mesh peers *and* the wired
        // Pico both get the packet whenever they're connected.
        bluetooth.broadcastPacket(packet, excludeAddress)
        if (isSerialLinkEnabled) {
            serial.broadcastPacket(packet, excludeAddress)
        }
    }

    /**
     * Attempts direct delivery over *both* links: the destination might only be reachable
     * through the Bluetooth mesh, only through whatever the Pico's radio can reach, or both.
     * Returns true if either link accepted the send.
     */
    override fun sendPacketToNode(targetNodeId: String, packet: MeshPacket): Boolean {
        val sentOverBluetooth = bluetooth.sendPacketToNode(targetNodeId, packet)
        val sentOverSerial = isSerialLinkEnabled && serial.sendPacketToNode(targetNodeId, packet)
        return sentOverBluetooth || sentOverSerial
    }

    override fun getActivePeerAddresses(): List<String> =
        bluetooth.getActivePeerAddresses() + serial.getActivePeerAddresses()
}
