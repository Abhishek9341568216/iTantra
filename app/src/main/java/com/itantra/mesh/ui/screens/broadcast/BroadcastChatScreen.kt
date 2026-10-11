package com.itantra.mesh.ui.screens.broadcast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.itantra.mesh.ITantraApp
import com.itantra.mesh.model.MeshPacket
import com.itantra.mesh.ui.components.MessageBubble
import com.itantra.mesh.ui.components.PermissionBanner
import com.itantra.mesh.ui.components.VoiceComposerBar
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBackground
import com.itantra.mesh.ui.theme.DarkSurfaceVariant
import com.itantra.mesh.ui.theme.TextMuted
import com.itantra.mesh.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastChatScreen(
    app: ITantraApp,
    modifier: Modifier = Modifier
) {
    val allMessages by app.database.messagesFlow.collectAsState()
    val broadcastMessages = remember(allMessages) {
        allMessages.filter { it.conversationId == MeshPacket.BROADCAST_DESTINATION }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message
    LaunchedEffect(broadcastMessages.size) {
        if (broadcastMessages.isNotEmpty()) {
            listState.animateScrollToItem(broadcastMessages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Connectivity is now shown once, globally, in MainActivity's persistent header —
        // no need to duplicate it per-screen.

        // Runtime Bluetooth permission prompt if needed
        PermissionBanner()

        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceVariant)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Podcasts,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Mesh Public Broadcast",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "Every node within multi-hop mesh range receives this channel",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        // Messages List or Empty State
        if (broadcastMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No broadcast messages yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Say hello! Your message will propagate to all nearby devices and through multi-hop relays.",
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
                items(broadcastMessages, key = { it.messageId }) { message ->
                    MessageBubble(message = message)
                }
            }
        }

        // Voice-first composer: big push-to-talk button, with live transcript feedback
        // and a fallback text field + send icon for manual typing.
        VoiceComposerBar(
            app = app,
            placeholderIdle = "Hold the mic and speak to broadcast...",
            onSend = { text ->
                app.routingEngine.sendMessage(
                    destinationId = MeshPacket.BROADCAST_DESTINATION,
                    content = text
                )
            }
        )
    }
}