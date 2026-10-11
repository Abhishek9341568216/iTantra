package com.itantra.mesh.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    BROADCAST("Broadcast", Icons.Default.Podcasts),
    DIRECT("Direct", Icons.Default.ChatBubbleOutline),
    RADAR("Radar", Icons.Default.Radar),
    DIAGNOSTICS("Diagnostics", Icons.Default.BugReport),
    SETTINGS("Settings", Icons.Default.Settings)
}
