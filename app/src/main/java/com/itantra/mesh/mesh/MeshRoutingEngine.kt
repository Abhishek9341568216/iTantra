package com.itantra.mesh.mesh

import android.util.Log
import com.itantra.mesh.data.MeshDatabase
import com.itantra.mesh.data.PreferencesRepository
import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.model.MeshPacket
import com.itantra.mesh.model.MessageItem
import com.itantra.mesh.model.MessageStatus
import com.itantra.mesh.model.PacketAction
import com.itantra.mesh.model.PacketLogItem
import com.itantra.mesh.model.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.LinkedHashSet

class MeshRoutingEngine(
    initialTransport: MeshTransport,
    val database: MeshDatabase,
    val prefs: PreferencesRepository,
    private val scope: CoroutineScope
) {
    // Bounded LRU cache of seen packet IDs to eliminate infinite broadcast loops
    private val seenPacketIds: MutableSet<String> = Collections.synchronizedSet(
        object : LinkedHashSet<String>() {
            override fun add(element: String): Boolean {
                if (size >= MAX_SEEN_CACHE_SIZE) {
                    val iterator = iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                return super.add(element)
            }
        }
    )

    /** Currently active transport (Bluetooth mesh or single-device Serial link). */
    var transport: MeshTransport = initialTransport
        private set

    private var transportObserverJob: Job? = null
    private var heartbeatJob: Job? = null
    var simulator: MeshSimulator? = null

    // Stable flow objects that survive transport switches, so UI collectors don't need to
    // re-subscribe whenever the user flips between Bluetooth and Serial in Settings.
    private val _connectedPeersCount = MutableStateFlow(0)
    val connectedPeersCount: StateFlow<Int> = _connectedPeersCount
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    /**
     * Fired whenever a chat message from another node is delivered locally
     * (broadcast or direct). Wired up by ITantraApp to trigger Piper TTS.
     * Never fired for our own outgoing messages or for control packets.
     */
    var onMessageReceived: ((MessageItem) -> Unit)? = null

    init {
        wireTransport()
    }

    private fun wireTransport() {
        transport.onPacketReceivedFromPeer = { packet, ingressAddress ->
            processIncomingPacket(packet, ingressAddress = ingressAddress)
        }

        transport.onPeerConnected = { node ->
            database.upsertNode(node)
        }

        transport.onPeerDisconnected = { nodeId ->
            // Keep node in DB but mark isDirectNeighbor as false
            // Can be updated on next scan/heartbeat
        }

        transportObserverJob?.cancel()
        transportObserverJob = scope.launch {
            launch { transport.connectedPeersCount.collect { _connectedPeersCount.value = it } }
            launch { transport.isReady.collect { _isReady.value = it } }
        }
    }

    /**
     * Switches the active transport at runtime (e.g. the user changed Connection Mode in
     * Settings from Bluetooth to Serial, or vice versa). Stops the old transport, swaps in the
     * new one, and re-wires callbacks/flows.
     */
    fun switchTransport(newTransport: MeshTransport) {
        if (newTransport === transport) return
        transport.onPacketReceivedFromPeer = null
        transport.onPeerConnected = null
        transport.onPeerDisconnected = null
        transport.stop()

        transport = newTransport
        wireTransport()
        transport.start()
    }

    fun start() {
        transport.start()
        startHeartbeat()
    }

    fun stop() {
        heartbeatJob?.cancel()
        transport.stop()
    }

    /**
     * Core routing and controlled flooding logic for an incoming mesh packet.
     */
    fun processIncomingPacket(packet: MeshPacket, ingressAddress: String? = null) {
        val packetId = packet.packetId
        val localNodeId = prefs.nodeId

        // 1. Loop prevention & deduplication check
        if (seenPacketIds.contains(packetId)) {
            database.insertPacketLog(
                PacketLogItem(
                    packetId = packetId,
                    type = packet.type,
                    senderId = packet.senderId,
                    senderName = packet.senderName,
                    destinationId = packet.destinationId,
                    action = PacketAction.DROPPED_DUPLICATE,
                    hopCount = packet.hopCount,
                    ttlRemaining = packet.ttl,
                    details = "Packet already seen. Dropped to prevent mesh loops."
                )
            )
            return
        }

        // Add to deduplication cache
        seenPacketIds.add(packetId)

        // 2. Discover/Update sender node in mesh topology
        if (packet.senderId != localNodeId) {
            val isDirect = packet.hopCount == 0
            database.upsertNode(
                MeshNode(
                    nodeId = packet.senderId,
                    displayName = packet.senderName,
                    isDirectNeighbor = isDirect,
                    hopDistance = packet.hopCount + 1,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            )
        }

        // Log reception
        database.insertPacketLog(
            PacketLogItem(
                packetId = packetId,
                type = packet.type,
                senderId = packet.senderId,
                senderName = packet.senderName,
                destinationId = packet.destinationId,
                action = PacketAction.RECEIVED,
                hopCount = packet.hopCount,
                ttlRemaining = packet.ttl,
                details = "Received from ${packet.senderName} (${packet.hopCount} hops, Route: ${packet.routeHops.joinToString(" -> ")})"
            )
        )

        // 3. Packet Delivery check
        val isForMe = packet.destinationId == localNodeId
        val isBroadcast = packet.destinationId == MeshPacket.BROADCAST_DESTINATION

        if (isForMe || isBroadcast) {
            handlePacketDelivery(packet)
        }

        // 4. Mesh Forwarding / Relaying check
        if (packet.ttl > 1) {
            // Forward if it's a broadcast OR if it's a direct message not meant for us
            val shouldForward = isBroadcast || !isForMe
            if (shouldForward && packet.senderId != localNodeId) {
                val forwardedPacket = packet.createForwardedPacket(localNodeId)

                database.insertPacketLog(
                    PacketLogItem(
                        packetId = packetId,
                        type = packet.type,
                        senderId = packet.senderId,
                        senderName = packet.senderName,
                        destinationId = packet.destinationId,
                        action = PacketAction.FORWARDED,
                        hopCount = forwardedPacket.hopCount,
                        ttlRemaining = forwardedPacket.ttl,
                        details = "Relaying packet (Hop ${forwardedPacket.hopCount}, TTL ${forwardedPacket.ttl})"
                    )
                )

                // Relay over Bluetooth mesh to all neighbors except ingress link
                transport.broadcastPacket(forwardedPacket, excludeAddress = ingressAddress)

                // Also notify simulator if active
                simulator?.onMeshPacketRelayed(forwardedPacket)
            }
        } else if (!isForMe && !isBroadcast) {
            database.insertPacketLog(
                PacketLogItem(
                    packetId = packetId,
                    type = packet.type,
                    senderId = packet.senderId,
                    senderName = packet.senderName,
                    destinationId = packet.destinationId,
                    action = PacketAction.DROPPED_TTL_EXPIRED,
                    hopCount = packet.hopCount,
                    ttlRemaining = 0,
                    details = "TTL expired before reaching destination ${packet.destinationId}"
                )
            )
        }
    }

    private fun handlePacketDelivery(packet: MeshPacket) {
        val localNodeId = prefs.nodeId

        when (packet.type) {
            PacketType.BROADCAST, PacketType.DIRECT_CHAT -> {
                if (packet.senderId != localNodeId) {
                    val conversationId = if (packet.type == PacketType.BROADCAST) {
                        MeshPacket.BROADCAST_DESTINATION
                    } else {
                        packet.senderId
                    }

                    val message = MessageItem(
                        messageId = packet.packetId,
                        conversationId = conversationId,
                        senderId = packet.senderId,
                        senderName = packet.senderName,
                        destinationId = packet.destinationId,
                        content = packet.payload,
                        timestamp = packet.timestamp,
                        status = MessageStatus.DELIVERED,
                        hopCount = packet.hopCount,
                        routeHops = packet.routeHops,
                        isFromSelf = false
                    )
                    database.insertMessage(message)
                    onMessageReceived?.invoke(message)

                    // If it was a 1-on-1 direct message for us, return an ACK
                    if (packet.type == PacketType.DIRECT_CHAT && packet.destinationId == localNodeId) {
                        sendDeliveryAck(targetNodeId = packet.senderId, originalMessageId = packet.packetId)
                    }
                }
            }

            PacketType.DELIVERY_ACK -> {
                // The payload contains the original messageId that was acknowledged
                val acknowledgedMessageId = packet.payload
                database.updateMessageStatus(acknowledgedMessageId, MessageStatus.DELIVERED)

                database.insertPacketLog(
                    PacketLogItem(
                        packetId = packet.packetId,
                        type = PacketType.DELIVERY_ACK,
                        senderId = packet.senderId,
                        senderName = packet.senderName,
                        destinationId = localNodeId,
                        action = PacketAction.DELIVERY_CONFIRMED,
                        hopCount = packet.hopCount,
                        ttlRemaining = packet.ttl,
                        details = "Delivery confirmed for message $acknowledgedMessageId by ${packet.senderName}"
                    )
                )
            }

            PacketType.NODE_ANNOUNCE, PacketType.PING, PacketType.PONG -> {
                // Heartbeat handled via node discovery update
            }
        }
    }

    /**
     * Originate and send a message from this device.
     */
    fun sendMessage(destinationId: String, content: String): MessageItem {
        val localNodeId = prefs.nodeId
        val localName = prefs.displayName
        val isBroadcast = destinationId == MeshPacket.BROADCAST_DESTINATION
        val type = if (isBroadcast) PacketType.BROADCAST else PacketType.DIRECT_CHAT

        val packet = MeshPacket(
            type = type,
            senderId = localNodeId,
            senderName = localName,
            destinationId = destinationId,
            payload = content,
            ttl = prefs.maxTtl,
            hopCount = 0,
            routeHops = listOf(localNodeId)
        )

        // Mark as seen locally
        seenPacketIds.add(packet.packetId)

        // Save to local chat
        val message = MessageItem(
            messageId = packet.packetId,
            conversationId = destinationId,
            senderId = localNodeId,
            senderName = localName,
            destinationId = destinationId,
            content = content,
            timestamp = packet.timestamp,
            status = if (isBroadcast) MessageStatus.SENT else MessageStatus.PENDING,
            hopCount = 0,
            routeHops = listOf(localNodeId),
            isFromSelf = true
        )
        database.insertMessage(message)

        // Log origination
        database.insertPacketLog(
            PacketLogItem(
                packetId = packet.packetId,
                type = packet.type,
                senderId = localNodeId,
                senderName = localName,
                destinationId = destinationId,
                action = PacketAction.ORIGINATED,
                hopCount = 0,
                ttlRemaining = packet.ttl,
                details = "Originated message for $destinationId"
            )
        )

        // Send to physical Bluetooth mesh peers
        if (isBroadcast) {
            transport.broadcastPacket(packet)
        } else {
            transport.sendPacketToNode(destinationId, packet)
        }

        // Also pass to simulator if enabled
        simulator?.onPacketOriginated(packet)

        return message
    }

    private fun sendDeliveryAck(targetNodeId: String, originalMessageId: String) {
        val ackPacket = MeshPacket(
            type = PacketType.DELIVERY_ACK,
            senderId = prefs.nodeId,
            senderName = prefs.displayName,
            destinationId = targetNodeId,
            payload = originalMessageId,
            ttl = prefs.maxTtl,
            hopCount = 0,
            routeHops = listOf(prefs.nodeId)
        )
        seenPacketIds.add(ackPacket.packetId)

        transport.broadcastPacket(ackPacket)
        simulator?.onPacketOriginated(ackPacket)
    }

    private fun startHeartbeat() {
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    val announcePacket = MeshPacket(
                        type = PacketType.NODE_ANNOUNCE,
                        senderId = prefs.nodeId,
                        senderName = prefs.displayName,
                        destinationId = MeshPacket.BROADCAST_DESTINATION,
                        payload = "HEARTBEAT",
                        ttl = 3, // 3 hops for heartbeat
                        hopCount = 0,
                        routeHops = listOf(prefs.nodeId)
                    )
                    seenPacketIds.add(announcePacket.packetId)
                    transport.broadcastPacket(announcePacket)
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat broadcast failed", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "MeshRoutingEngine"
        private const val MAX_SEEN_CACHE_SIZE = 2000
        private const val HEARTBEAT_INTERVAL_MS = 30_000L // 30 seconds
    }
}
