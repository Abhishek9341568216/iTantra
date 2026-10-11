package com.itantra.mesh.model

enum class PacketAction {
    RECEIVED,
    FORWARDED,
    ORIGINATED,
    DROPPED_DUPLICATE,
    DROPPED_TTL_EXPIRED,
    DELIVERY_CONFIRMED
}

data class PacketLogItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val packetId: String,
    val type: PacketType,
    val senderId: String,
    val senderName: String,
    val destinationId: String,
    val action: PacketAction,
    val hopCount: Int,
    val ttlRemaining: Int,
    val details: String = ""
)
