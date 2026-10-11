package com.itantra.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBorder
import com.itantra.mesh.ui.theme.DarkSurface
import com.itantra.mesh.ui.theme.GreenOnline
import com.itantra.mesh.ui.theme.PurpleVirtual
import com.itantra.mesh.ui.theme.TextMuted

/**
 * Persistent connectivity strip shown across every screen (hosted once in [com.itantra.mesh.MainActivity],
 * above the bottom navigation content). Bluetooth and Serial are rendered as two independent
 * chips because both can be active at the same time: Bluetooth mesh is always listening, and the
 * Serial chip lights up the instant a Pico is plugged in, on top of — not instead of — Bluetooth.
 */
@Composable
fun ConnectionStatusBar(
    localNodeId: String,
    bluetoothPeersCount: Int,
    isSerialConnected: Boolean,
    isSerialLinkEnabled: Boolean,
    isSimulatorEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = localNodeId,
            style = MaterialTheme.typography.labelSmall,
            color = CyanPrimary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSimulatorEnabled) {
                StatusChip(
                    icon = null,
                    label = "SIMULATOR",
                    dotColor = null,
                    tint = PurpleVirtual,
                    backgroundTint = PurpleVirtual.copy(alpha = 0.18f)
                )
            }

            // Bluetooth mesh chip - always shown, since Bluetooth is always active.
            StatusChip(
                icon = Icons.Default.Bluetooth,
                label = if (bluetoothPeersCount > 0) {
                    "$bluetoothPeersCount peer${if (bluetoothPeersCount != 1) "s" else ""}"
                } else {
                    "Scanning"
                },
                dotColor = if (bluetoothPeersCount > 0) GreenOnline else TextMuted
            )

            // Serial chip - only rendered when the link is turned on in Settings; dims until a
            // device actually connects so it reads as additive rather than a mode switch.
            if (isSerialLinkEnabled) {
                StatusChip(
                    icon = Icons.Default.Usb,
                    label = if (isSerialConnected) "Serial" else "No serial device",
                    dotColor = if (isSerialConnected) GreenOnline else TextMuted,
                    tint = if (isSerialConnected) Color.White else TextMuted
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    icon: ImageVector?,
    label: String,
    dotColor: Color?,
    tint: Color = Color.White,
    backgroundTint: Color = DarkBorder
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundTint)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        icon?.let {
            Icon(imageVector = it, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
