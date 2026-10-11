package com.itantra.mesh.model

import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class PacketType {
    BROADCAST,      // Message to all nodes in the mesh
    DIRECT_CHAT,    // 1-on-1 message routed to a specific nodeId
    DELIVERY_ACK,   // Delivery confirmation returning to sender
    NODE_ANNOUNCE,  // Periodic heartbeat announcing node presence and neighbors
    PING,           // Health check / latency test
    PONG            // Response to ping
}

data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val type: PacketType = PacketType.BROADCAST,
    val senderId: String,
    val senderName: String,
    val destinationId: String = BROADCAST_DESTINATION,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val ttl: Int = DEFAULT_TTL,
    val hopCount: Int = 0,
    val routeHops: List<String> = emptyList()
) {
    companion object {
        const val BROADCAST_DESTINATION = "*"
        const val DEFAULT_TTL = 5
        private val gson = Gson()

        fun fromJson(json: String): MeshPacket? {
            return try {
                gson.fromJson(json, MeshPacket::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun fromBytes(bytes: ByteArray): MeshPacket? {
            return fromJson(String(bytes, StandardCharsets.UTF_8))
        }
    }

    fun toJson(): String = gson.toJson(this)

    fun toBytes(): ByteArray = toJson().toByteArray(StandardCharsets.UTF_8)

    /**
     * Creates a forwarded copy of this packet with decremented TTL,
     * incremented hopCount, and the relaying node appended to routeHops.
     */
    fun createForwardedPacket(relayingNodeId: String): MeshPacket {
        val updatedRoute = routeHops.toMutableList().apply {
            if (!contains(relayingNodeId)) {
                add(relayingNodeId)
            }
        }
        return copy(
            ttl = ttl - 1,
            hopCount = hopCount + 1,
            routeHops = updatedRoute
        )
    }
}
