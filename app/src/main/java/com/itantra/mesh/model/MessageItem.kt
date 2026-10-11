package com.itantra.mesh.model

import java.util.UUID

enum class MessageStatus {
    PENDING,        // Waiting for mesh link
    SENT,           // Dispatched to 1+ direct neighbors
    RELAYED,        // Transited through intermediate hops
    DELIVERED,      // Target node received and acknowledged
    FAILED
}

data class MessageItem(
    val messageId: String = UUID.randomUUID().toString(),
    val conversationId: String, // "*" for broadcast channel, or target nodeId for direct chat
    val senderId: String,
    val senderName: String,
    val destinationId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val hopCount: Int = 0,
    val routeHops: List<String> = emptyList(),
    val isFromSelf: Boolean = false
)
