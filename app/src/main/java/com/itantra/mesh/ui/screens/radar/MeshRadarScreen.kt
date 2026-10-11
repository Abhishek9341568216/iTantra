package com.itantra.mesh.ui.screens.radar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.mesh.ITantraApp
import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.ui.components.NodeCard
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBackground
import com.itantra.mesh.ui.theme.DarkBorder
import com.itantra.mesh.ui.theme.DarkSurface
import com.itantra.mesh.ui.theme.GreenOnline
import com.itantra.mesh.ui.theme.OrangeRelay
import com.itantra.mesh.ui.theme.PurpleVirtual
import com.itantra.mesh.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import com.itantra.mesh.mesh.BluetoothMeshManager

@Composable
fun MeshRadarScreen(
    app: ITantraApp,
    onOpenDirectChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nodes by app.database.nodesFlow.collectAsState()
    val localNodeId = app.preferencesRepository.nodeId
    val peerNodes = nodes.filter { it.nodeId != localNodeId }
    // Bluetooth is always active, so scanning/manual-connect are always available here too.
    // The Serial banner below is purely informational, on top of the Bluetooth radar.
    val isSerialConnected by app.compositeTransport.isSerialConnected.collectAsState()

    var showConnectDialog by remember { mutableStateOf(false) }
    var macInput by remember { mutableStateOf("") }
    var connectError by remember { mutableStateOf<String?>(null) }

    if (showConnectDialog) {
        AlertDialog(
            onDismissRequest = {
                showConnectDialog = false
                macInput = ""
                connectError = null
            },
            containerColor = DarkSurface,
            title = { Text("Connect by MAC Address", color = Color.White) },
            text = {
                Column {
                    Text(
                        text = "Enter the Bluetooth MAC address of a nearby device to connect directly, without waiting for discovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = macInput,
                        onValueChange = {
                            macInput = it
                            connectError = null
                        },
                        placeholder = { Text("AA:BB:CC:DD:EE:FF", color = TextSecondary) },
                        singleLine = true,
                        isError = connectError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = CyanPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    connectError?.let { error ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = error, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF6B6B))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (app.meshManager.connectToDeviceByAddress(macInput)) {
                        BluetoothMeshManager.ManualConnectResult.CONNECTING -> {
                            showConnectDialog = false
                            macInput = ""
                            connectError = null
                        }
                        BluetoothMeshManager.ManualConnectResult.INVALID_ADDRESS ->
                            connectError = "Not a valid MAC address (format AA:BB:CC:DD:EE:FF)"
                        BluetoothMeshManager.ManualConnectResult.BLUETOOTH_DISABLED ->
                            connectError = "Bluetooth is turned off"
                        BluetoothMeshManager.ManualConnectResult.ALREADY_CONNECTED ->
                            connectError = "Already connected to this device"
                        BluetoothMeshManager.ManualConnectResult.ALREADY_CONNECTING ->
                            connectError = "Already attempting to connect to this device"
                    }
                }) {
                    Text("Connect", color = CyanPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConnectDialog = false
                    macInput = ""
                    connectError = null
                }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Radar pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse"
    )

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
                    text = "Mesh Topology Radar",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "${peerNodes.size} node${if (peerNodes.size != 1) "s" else ""} in reach",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary
                )
            }

            // Bluetooth is always active, so scan/connect controls are always available here,
            // regardless of whether a Serial device is also connected.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showConnectDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBorder, contentColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Connect by MAC",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Connect", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = { app.meshManager.triggerDiscovery() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBorder, contentColor = CyanPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Scan", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (isSerialConnected) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .padding(12.dp)
            ) {
                Text(
                    text = "A wired Serial device is also connected. It has no discovery or " +
                        "scanning of its own — it's a single extra link running alongside the " +
                        "Bluetooth mesh shown below, managed from Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Radar Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(DarkSurface, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val maxRadius = size.width / 2

                // Concentric Hop rings
                val r1 = maxRadius * 0.35f // 1 hop (Direct)
                val r2 = maxRadius * 0.65f // 2 hops
                val r3 = maxRadius * 0.95f // 3+ hops

                drawCircle(color = DarkBorder, radius = r3, center = center, style = Stroke(width = 1.5f))
                drawCircle(color = DarkBorder, radius = r2, center = center, style = Stroke(width = 1.5f))
                drawCircle(color = DarkBorder, radius = r1, center = center, style = Stroke(width = 1.5f))

                // Pulsing sweep wave
                drawCircle(
                    color = CyanPrimary.copy(alpha = (1f - sweepPulse) * 0.4f),
                    radius = maxRadius * sweepPulse,
                    center = center,
                    style = Stroke(width = 2.5f)
                )

                // Center node: Self
                drawCircle(color = CyanPrimary, radius = 7f, center = center)

                // Plot peers on rings
                peerNodes.forEachIndexed { index, peer ->
                    val angle = (index.toFloat() / maxOf(1, peerNodes.size)) * 2 * Math.PI + 0.4
                    val radius = when {
                        peer.isDirectNeighbor -> r1
                        peer.hopDistance == 2 -> r2
                        else -> r3
                    }
                    val x = center.x + (radius * cos(angle)).toFloat()
                    val y = center.y + (radius * sin(angle)).toFloat()
                    val nodeColor = when {
                        peer.isVirtual -> PurpleVirtual
                        peer.isDirectNeighbor -> GreenOnline
                        else -> OrangeRelay
                    }

                    // Draw outer glow and inner node dot
                    drawCircle(color = nodeColor.copy(alpha = 0.3f), radius = 10f, center = Offset(x, y))
                    drawCircle(color = nodeColor, radius = 5f, center = Offset(x, y))
                }
            }

            // Radar Ring Legends
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).background(GreenOnline, CircleShape))
                    Text("1 Hop (Direct)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextSecondary)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(6.dp).background(OrangeRelay, CircleShape))
                    Text("2+ Hops (Relayed)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Discovered Mesh Nodes",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Discovered Nodes List
        if (peerNodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = DarkBorder,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No mesh nodes in range",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(peerNodes, key = { it.nodeId }) { node ->
                    NodeCard(
                        node = node,
                        onChatClick = { onOpenDirectChat(it.nodeId) }
                    )
                }
            }
        }
    }
}
