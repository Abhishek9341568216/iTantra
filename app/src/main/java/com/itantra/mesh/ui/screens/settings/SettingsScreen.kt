package com.itantra.mesh.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.itantra.mesh.ITantraApp
import com.itantra.mesh.R
import com.itantra.mesh.mesh.BleDiscoveryManager
import com.itantra.mesh.mesh.SerialLinkManager
import com.itantra.mesh.service.MeshForegroundService
import com.itantra.mesh.voice.SttModelOption
import com.itantra.mesh.voice.TtsVoiceOption
import com.itantra.mesh.voice.VoiceCatalog
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBackground
import com.itantra.mesh.ui.theme.DarkBorder
import com.itantra.mesh.ui.theme.DarkSurface
import com.itantra.mesh.ui.theme.DarkSurfaceVariant
import com.itantra.mesh.ui.theme.PurpleVirtual
import com.itantra.mesh.ui.theme.TextMuted
import com.itantra.mesh.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    app: ITantraApp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var displayName by remember { mutableStateOf(app.preferencesRepository.displayName) }
    var maxTtl by remember { mutableFloatStateOf(app.preferencesRepository.maxTtl.toFloat()) }
    var isSimEnabled by remember { mutableStateOf(app.preferencesRepository.isSimulatorEnabled) }
    var isBgServiceEnabled by remember { mutableStateOf(app.preferencesRepository.isBackgroundServiceEnabled) }
    var isTtsEnabled by remember { mutableStateOf(app.preferencesRepository.isTtsEnabled) }
    var isTtsAnnounceSender by remember { mutableStateOf(app.preferencesRepository.isTtsAnnounceSender) }
    var selectedVoice by remember {
        mutableStateOf(VoiceCatalog.ttsVoiceById(app.preferencesRepository.selectedTtsVoiceId))
    }
    var selectedSttModel by remember {
        mutableStateOf(VoiceCatalog.sttModelById(app.preferencesRepository.selectedSttModelId))
    }
    val ttsState by app.ttsEngine.stateFlow.collectAsState()
    val sttState by app.voiceRecognizer.stateFlow.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Mesh Settings",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Configure your node identity and mesh routing parameters",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Node ID Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(14.dp)
        ) {
            Text(
                text = "My Node Identifier",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = app.preferencesRepository.nodeId,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
                    color = CyanPrimary
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Node ID", app.preferencesRepository.nodeId)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Node ID copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Node ID",
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display Name Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(14.dp)
        ) {
            Text(
                text = "Display Nickname",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                    app.preferencesRepository.displayName = it
                },
                placeholder = { Text("Enter nickname...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = CyanPrimary)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CyanPrimary
                ),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ConnectivityCard(app = app)

        Spacer(modifier = Modifier.height(16.dp))

        // Max TTL / Hop Limit Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Packet Hop Limit (TTL)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Text(
                    text = "${maxTtl.roundToInt()} hops",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyanPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Maximum number of intermediate relays a packet will traverse before being dropped.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Slider(
                value = maxTtl,
                onValueChange = {
                    maxTtl = it
                    app.preferencesRepository.maxTtl = it.roundToInt()
                },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = CyanPrimary,
                    activeTrackColor = CyanPrimary,
                    inactiveTrackColor = DarkBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Virtual Simulator Switch
        Row(
            modifier = Modifier
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
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = PurpleVirtual,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Virtual Mesh Simulator",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Spawns 3 virtual nodes for single-device testing",
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
                    if (it) app.simulator.start() else app.simulator.stop()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PurpleVirtual,
                    checkedTrackColor = PurpleVirtual.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background Service Switch
        Row(
            modifier = Modifier
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
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Background Mesh Service",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Keeps Bluetooth mesh active when screen is locked",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Switch(
                checked = isBgServiceEnabled,
                onCheckedChange = {
                    isBgServiceEnabled = it
                    app.preferencesRepository.isBackgroundServiceEnabled = it
                    if (it) {
                        MeshForegroundService.start(context)
                    } else {
                        MeshForegroundService.stop(context)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyanPrimary,
                    checkedTrackColor = CyanPrimary.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkBorder
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speech-to-Text Language Picker (Vosk, used for push-to-talk)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Push-to-Talk Language",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "The spoken language the mic recognizes offline with Vosk",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            SttModelDropdown(
                options = VoiceCatalog.sttModels,
                selected = selectedSttModel,
                isLoading = sttState == com.itantra.mesh.voice.VoskSpeechRecognizer.State.LOADING,
                onSelect = { option ->
                    selectedSttModel = option
                    app.switchSttModel(option.id)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speak Incoming Messages (Piper TTS) Switch + Voice Picker
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Speak Incoming Messages",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = "Reads new mesh messages aloud automatically, offline with Piper TTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                Switch(
                    checked = isTtsEnabled,
                    onCheckedChange = {
                        isTtsEnabled = it
                        app.preferencesRepository.isTtsEnabled = it
                        if (!it) app.ttsEngine.stopSpeakingAndClear()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = CyanPrimary.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkBorder
                    )
                )
            }

            if (isTtsEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Voice & language",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                TtsVoiceDropdown(
                    options = VoiceCatalog.ttsVoices,
                    selected = selectedVoice,
                    isLoading = ttsState == com.itantra.mesh.voice.PiperTtsEngine.State.LOADING,
                    onSelect = { option ->
                        selectedVoice = option
                        app.switchTtsVoice(option.id)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Announce sender name",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Switch(
                        checked = isTtsAnnounceSender,
                        onCheckedChange = {
                            isTtsAnnounceSender = it
                            app.preferencesRepository.isTtsAnnounceSender = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanPrimary,
                            checkedTrackColor = CyanPrimary.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkBorder
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { app.ttsEngine.speak("This is a test of the mesh voice announcer.") },
                    enabled = app.ttsEngine.isReady,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (app.ttsEngine.isReady) "Test Voice" else "Loading voice model...")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Protocol Info Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                Text(text = "Protocol Specifications", style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• Service UUID: ${BleDiscoveryManager.MESH_SERVICE_UUID}\n" +
                       "• Routing: Controlled Epidemic Flooding with Deduplication\n" +
                       "• Transport: Bluetooth Classic RFCOMM + BLE Advertising\n" +
                       "• Offline Storage: Local SQLite with StateFlow streams",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AboutCard()

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Credits card: the app mark, name/tagline, and attribution to the team and institution behind
 * it. Uses the two logo files dropped into res/drawable — see project notes for exact paths.
 */
@Composable
private fun AboutCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.itantra_logo),
                contentDescription = "iTantra",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "iTantra", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    text = "Offline mesh messaging over Bluetooth & Serial",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        androidx.compose.material3.Divider(color = DarkBorder)
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Built by Team KOSMOS",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Text(
            text = "Knowledge Oriented Sṛṣṭi & Movement for Open Solutions",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Bennett University",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))
        Image(
            painter = painterResource(id = R.drawable.bennett_university_logo),
            contentDescription = "Bennett University",
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )
    }
}

/**
 * Shows both transports as what they now are: always-on Bluetooth mesh, plus an optional Serial
 * (USB) link that runs *alongside* it — not instead of it. Turning the Serial switch on doesn't
 * pick a "mode"; it just lets a plugged-in device (e.g. a Pi Pico W) join the same session that
 * Bluetooth is already carrying. When both are connected, packets flow over both at once.
 */
@Composable
private fun ConnectivityCard(app: ITantraApp) {
    var isSerialEnabled by remember { mutableStateOf(app.preferencesRepository.isSerialLinkEnabled) }
    val availableDevices by app.serialLinkManager.availableDevices.collectAsState()
    val isSerialReady by app.serialLinkManager.isReady.collectAsState()
    val bluetoothPeersCount by app.compositeTransport.bluetoothPeersCount.collectAsState()
    var baudRateText by remember { mutableStateOf(app.preferencesRepository.serialBaudRate.toString()) }

    LaunchedEffect(isSerialEnabled) {
        if (isSerialEnabled) {
            app.serialLinkManager.refreshAvailableDevices()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(14.dp)
    ) {
        Text(
            text = "Connectivity",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Bluetooth forms a multi-hop mesh with nearby phones and is always on. " +
                "Turn on the Serial link to also talk to a single wired device (e.g. a Pi Pico " +
                "W) over USB — when it's connected, messages travel over both at the same time.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Bluetooth row - always on, no toggle, just live status.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.Bluetooth, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(18.dp))
                Column {
                    Text(text = "Bluetooth Mesh", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(
                        text = if (bluetoothPeersCount > 0) "$bluetoothPeersCount peer${if (bluetoothPeersCount != 1) "s" else ""} in range" else "Scanning for nearby nodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Text(text = "Always on", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Serial row - toggleable, additive.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = if (isSerialEnabled && isSerialReady) CyanPrimary else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(text = "Serial (USB) Link", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(
                        text = when {
                            !isSerialEnabled -> "Turned off"
                            isSerialReady -> "Connected — carrying traffic alongside Bluetooth"
                            else -> "On, watching for a USB device"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Switch(
                checked = isSerialEnabled,
                onCheckedChange = {
                    isSerialEnabled = it
                    app.setSerialLinkEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyanPrimary,
                    checkedTrackColor = CyanPrimary.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkBorder
                )
            )
        }

        if (isSerialEnabled) {
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cable,
                    contentDescription = null,
                    tint = if (isSerialReady) CyanPrimary else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (isSerialReady) "Connected to serial device" else "Not connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSerialReady) CyanPrimary else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Baud rate",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = baudRateText,
                onValueChange = { text ->
                    baudRateText = text
                    text.toIntOrNull()?.let { app.preferencesRepository.serialBaudRate = it }
                },
                placeholder = { Text("115200") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = CyanPrimary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "USB devices",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                IconButton(
                    onClick = { app.serialLinkManager.refreshAvailableDevices() },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh device list",
                        tint = CyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (availableDevices.isEmpty()) {
                Text(
                    text = "No USB serial device found. Plug in the Pico W and tap refresh.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            } else {
                availableDevices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .clickable { app.serialLinkManager.connect(device) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = device.displayLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Text(
                                text = "VID:PID ${device.vendorId}:${device.productId}" +
                                    if (device.hasPermission) "" else " — tap to grant permission",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "Connect",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

/** Dropdown for picking which bundled Piper voice (and language) reads messages aloud. */
@Composable
private fun TtsVoiceDropdown(
    options: List<TtsVoiceOption>,
    selected: TtsVoiceOption,
    isLoading: Boolean,
    onSelect: (TtsVoiceOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
                .clickableIfEnabled(enabled = !isLoading) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${selected.displayName} — ${selected.languageLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                if (isLoading) {
                    Text(
                        text = "Loading voice…",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Choose voice",
                tint = CyanPrimary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurfaceVariant)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = "${option.displayName} — ${option.languageLabel}",
                                color = Color.White
                            )
                            Text(
                                text = option.languageCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

/** Dropdown for picking which bundled Vosk model (and language) push-to-talk recognizes. */
@Composable
private fun SttModelDropdown(
    options: List<SttModelOption>,
    selected: SttModelOption,
    isLoading: Boolean,
    onSelect: (SttModelOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceVariant)
                .clickableIfEnabled(enabled = !isLoading) { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = selected.languageLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                if (isLoading) {
                    Text(
                        text = "Loading language model…",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "Choose language",
                tint = CyanPrimary
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(DarkSurfaceVariant)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.languageLabel, color = Color.White) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

/** Small helper so the picker row is disabled (no ripple, no click) while a model loads. */
private fun Modifier.clickableIfEnabled(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, onClick = onClick)
