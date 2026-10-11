package com.itantra.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBackground
import com.itantra.mesh.ui.theme.DarkBorder
import com.itantra.mesh.ui.theme.DarkSurface
import com.itantra.mesh.ui.theme.GreenOnline
import com.itantra.mesh.ui.theme.OrangeRelay
import com.itantra.mesh.ui.theme.PurpleVirtual
import com.itantra.mesh.ui.theme.TextMuted
import com.itantra.mesh.ui.theme.TextSecondary

@Composable
fun NodeCard(
    node: MeshNode,
    onChatClick: (MeshNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val hopLabel = when {
        node.isDirectNeighbor -> "Direct Neighbor (1 hop)"
        node.hopDistance > 1 -> "${node.hopDistance} hops away"
        else -> "Nearby Node"
    }

    val hopColor = if (node.isDirectNeighbor) GreenOnline else OrangeRelay

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Online status indicator avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (node.isVirtual) PurpleVirtual.copy(alpha = 0.2f) else DarkBorder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = if (node.isVirtual) PurpleVirtual else CyanPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = node.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    if (node.isVirtual) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(PurpleVirtual.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "VIRTUAL",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = PurpleVirtual
                            )
                        }
                    }
                }

                Text(
                    text = "MAC: ${node.macAddress}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(hopColor)
                    )
                    Text(
                        text = hopLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = hopColor
                    )
                }
            }
        }

        // Direct Message button
        Button(
            onClick = { onChatClick(node) },
            colors = ButtonDefaults.buttonColors(
                containerColor = DarkBorder,
                contentColor = CyanPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = ButtonDefaults.ContentPadding
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = "Chat",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = "Chat", style = MaterialTheme.typography.labelSmall)
        }
    }
}
