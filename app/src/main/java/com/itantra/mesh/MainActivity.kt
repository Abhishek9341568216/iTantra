package com.itantra.mesh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.mesh.service.MeshForegroundService
import com.itantra.mesh.ui.components.ConnectionStatusBar
import com.itantra.mesh.ui.components.MeshPermissions
import com.itantra.mesh.ui.navigation.Screen
import com.itantra.mesh.ui.screens.broadcast.BroadcastChatScreen
import com.itantra.mesh.ui.screens.diagnostics.DiagnosticsScreen
import com.itantra.mesh.ui.screens.direct.DirectChatScreen
import com.itantra.mesh.ui.screens.radar.MeshRadarScreen
import com.itantra.mesh.ui.screens.settings.SettingsScreen
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBackground
import com.itantra.mesh.ui.theme.DarkBorder
import com.itantra.mesh.ui.theme.DarkSurface
import com.itantra.mesh.ui.theme.ITantraMeshTheme
import com.itantra.mesh.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ITantraApp

        // Start background service if permitted and enabled
        if (MeshPermissions.hasAllPermissions(this) && app.preferencesRepository.isBackgroundServiceEnabled) {
            MeshForegroundService.start(this)
        }

        setContent {
            ITantraMeshTheme {
                MainScreen(app = app)
            }
        }
    }
}

/** Persistent brand header shown above the status strip on every screen: mark + name. */
@Composable
private fun ITantraTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.itantra_logo),
            contentDescription = "iTantra",
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "iTantra",
                fontSize = 18.sp,
                color = Color.White,
                style = TextStyle(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "by Team KOSMOS \u00b7 Bennett University",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun MainScreen(app: ITantraApp) {
    var currentScreen by remember { mutableStateOf(Screen.BROADCAST) }
    var directChatTargetNodeId by remember { mutableStateOf<String?>(null) }

    // Bluetooth peer count and Serial connection state are observed here, once, so the header
    // reflects both transports live no matter which screen is showing underneath.
    val bluetoothPeersCount by app.compositeTransport.bluetoothPeersCount.collectAsState()
    val isSerialConnected by app.compositeTransport.isSerialConnected.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp
            ) {
                Screen.values().forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            currentScreen = screen
                            if (screen != Screen.DIRECT) {
                                directChatTargetNodeId = null
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = DarkBorder
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            ITantraTopBar()
            ConnectionStatusBar(
                localNodeId = app.preferencesRepository.nodeId,
                bluetoothPeersCount = bluetoothPeersCount,
                isSerialConnected = isSerialConnected,
                isSerialLinkEnabled = app.preferencesRepository.isSerialLinkEnabled,
                isSimulatorEnabled = app.preferencesRepository.isSimulatorEnabled
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    Screen.BROADCAST -> BroadcastChatScreen(app = app)
                    Screen.DIRECT -> DirectChatScreen(
                        app = app,
                        selectedNodeId = directChatTargetNodeId,
                        onBackToList = { directChatTargetNodeId = null }
                    )
                    Screen.RADAR -> MeshRadarScreen(
                        app = app,
                        onOpenDirectChat = { nodeId ->
                            directChatTargetNodeId = nodeId
                            currentScreen = Screen.DIRECT
                        }
                    )
                    Screen.DIAGNOSTICS -> DiagnosticsScreen(app = app)
                    Screen.SETTINGS -> SettingsScreen(app = app)
                }
            }
        }
    }
}
