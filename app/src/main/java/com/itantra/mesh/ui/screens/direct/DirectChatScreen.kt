package com.itantra.mesh.ui.screens.direct

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itantra.mesh.ITantraApp
import com.itantra.mesh.model.MeshPacket
import com.itantra.mesh.ui.components.MessageBubble
import com.itantra.mesh.ui.components.NodeCard
import com.itantra.mesh.ui.components.VoiceComposerBar
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBackground
import com.itantra.mesh.ui.theme.DarkSurfaceVariant
import com.itantra.mesh.ui.theme.GreenOnline
import com.itantra.mesh.ui.theme.OrangeRelay
import com.itantra.mesh.ui.theme.TextMuted
import com.itantra.mesh.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectChatScreen(
    app: ITantraApp,
    selectedNodeId: String? = null,
    onBackToList: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val nodes by app.database.nodesFlow.collectAsState()
    val allMessages by app.database.messagesFlow.collectAsState()

    var activeTargetNodeId by remember(selectedNodeId) { mutableStateOf(selectedNodeId) }
    val listState = rememberLazyListState()

    if (activeTargetNodeId == null) {
        // Show List of Discovered Peers to chat with
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(16.dp)
        ) {
            Text(
                text = "Direct Mesh Chats",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "1-on-1 private messaging routed specifically across the mesh",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            val peerNodes = nodes.filter { it.nodeId != app.preferencesRepository.nodeId }

            if (peerNodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No peers discovered yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ensure nearby devices have Bluetooth turned on and iTantra Mesh open, or enable Virtual Mesh Simulator in Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(peerNodes, key = { it.nodeId }) { node ->
                        NodeCard(
                            node = node,
                            onChatClick = { activeTargetNodeId = it.nodeId }
                        )
                    }
                }
            }
        }
    } else {
        // Active 1-on-1 Conversation
        val targetNode = nodes.firstOrNull { it.nodeId == activeTargetNodeId }
        val directMessages = remember(allMessages, activeTargetNodeId) {
            allMessages.filter {
                (it.conversationId == activeTargetNodeId) ||
                (it.senderId == activeTargetNodeId && it.destinationId == app.preferencesRepository.nodeId) ||
                (it.senderId == app.preferencesRepository.nodeId && it.destinationId == activeTargetNodeId)
            }
        }

        LaunchedEffect(directMessages.size) {
            if (directMessages.isNotEmpty()) {
                listState.animateScrollToItem(directMessages.size - 1)
            }
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            // Chat Header with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    activeTargetNodeId = null
                    onBackToList()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = CyanPrimary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = targetNode?.displayName ?: "Peer $activeTargetNodeId",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    val statusText = when {
                        targetNode?.isDirectNeighbor == true -> "Direct connection • 1 hop"
                        (targetNode?.hopDistance ?: 1) > 1 -> "Mesh route • ${targetNode?.hopDistance} hops"
                        else -> "Node ID: $activeTargetNodeId"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (targetNode?.isDirectNeighbor == true) GreenOnline else OrangeRelay
                    )
                }
            }

            // Message Feed
            if (directMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Start a direct conversation",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Messages are addressed directly to this node ID and confirmed via mesh delivery acknowledgments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    items(directMessages, key = { it.messageId }) { message ->
                        MessageBubble(message = message)
                    }
                }
            }

            // Voice-first composer: big push-to-talk button, with live transcript
            // feedback and a fallback text field + send icon for manual typing.
            VoiceComposerBar(
                app = app,
                placeholderIdle = "Hold the mic and speak to ${targetNode?.displayName ?: "peer"}...",
                onSend = { text ->
                    val target = activeTargetNodeId
                    if (target != null) {
                        app.routingEngine.sendMessage(
                            destinationId = target,
                            content = text
                        )
                    }
                }
            )
        }
    }
}
