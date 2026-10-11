package com.itantra.mesh.ui.screens.diagnostics

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.mesh.ITantraApp
import com.itantra.mesh.model.MeshPacket
import com.itantra.mesh.model.PacketAction
import com.itantra.mesh.model.PacketLogItem
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBackground
import com.itantra.mesh.ui.theme.DarkBorder
import com.itantra.mesh.ui.theme.DarkSurface
import com.itantra.mesh.ui.theme.DarkSurfaceVariant
import com.itantra.mesh.ui.theme.GreenOnline
import com.itantra.mesh.ui.theme.OrangeRelay
import com.itantra.mesh.ui.theme.PurpleVirtual
import com.itantra.mesh.ui.theme.RedDrop
import com.itantra.mesh.ui.theme.TextMuted
import com.itantra.mesh.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    app: ITantraApp,
    modifier: Modifier = Modifier
) {
    val packetLogs by app.database.packetLogsFlow.collectAsState()
    var isSimEnabled by remember { mutableStateOf(app.preferencesRepository.isSimulatorEnabled) }
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Packet Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "Live multi-hop routing & deduplication monitor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            IconButton(onClick = { app.database.clearAllLogs() }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear logs",
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Virtual Simulator Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = PurpleVirtual,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "Virtual Mesh Simulator",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = Color.White
                    )
                    Text(
                        text = "Simulates 3 virtual relay hops & echo responses",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Switch(
                checked = isSimEnabled,
                onCheckedChange = {
                    isSimEnabled = it
                    app.preferencesRepository.isSimulatorEnabled = it
                    if (it) {
                        app.simulator.start()
                    } else {
                        app.simulator.stop()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PurpleVirtual,
                    checkedTrackColor = PurpleVirtual.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Ping Button
        Button(
            onClick = {
                app.routingEngine.sendMessage(
                    destinationId = MeshPacket.BROADCAST_DESTINATION,
                    content = "Diagnostics Ping #${System.currentTimeMillis() % 10000}"
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkBorder, contentColor = CyanPrimary),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Inject Test Broadcast Packet", style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Packet Log List
        if (packetLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = DarkBorder,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Waiting for mesh packet activity...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(packetLogs, key = { it.id }) { log ->
                    PacketLogRow(log = log, timeFormat = timeFormat)
                }
            }
        }
    }
}

@Composable
private fun PacketLogRow(
    log: PacketLogItem,
    timeFormat: SimpleDateFormat
) {
    val (actionColor, actionLabel) = when (log.action) {
        PacketAction.ORIGINATED -> CyanPrimary to "ORIGINATED"
        PacketAction.RECEIVED -> GreenOnline to "RECEIVED"
        PacketAction.FORWARDED -> OrangeRelay to "RELAYED"
        PacketAction.DROPPED_DUPLICATE -> RedDrop to "DROP_DUP"
        PacketAction.DROPPED_TTL_EXPIRED -> RedDrop to "DROP_TTL"
        PacketAction.DELIVERY_CONFIRMED -> GreenOnline to "DELIVERED"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Action Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(actionColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = actionColor
                    )
                }

                Text(
                    text = log.type.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White
                )
            }

            Text(
                text = timeFormat.format(Date(log.timestamp)),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "From: ${log.senderName} (${log.senderId}) -> To: ${log.destinationId}",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = Color.White
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = "Hops: ${log.hopCount}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = OrangeRelay
            )
            Text(
                text = "TTL Left: ${log.ttlRemaining}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextSecondary
            )
            Text(
                text = "ID: ${log.packetId.take(8)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextMuted
            )
        }

        if (log.details.isNotBlank()) {
            Text(
                text = log.details,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
