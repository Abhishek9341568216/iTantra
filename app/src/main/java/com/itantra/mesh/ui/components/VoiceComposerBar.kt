package com.itantra.mesh.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itantra.mesh.ITantraApp
import com.itantra.mesh.voice.VoskSpeechRecognizer
import com.itantra.mesh.ui.theme.CyanPrimary
import com.itantra.mesh.ui.theme.DarkBorder
import com.itantra.mesh.ui.theme.DarkSurface
import com.itantra.mesh.ui.theme.DarkSurfaceVariant
import com.itantra.mesh.ui.theme.TextMuted
import com.itantra.mesh.ui.theme.TextSecondary

private val ListeningRed = Color(0xFFFF6B6B)

/**
 * The main input surface for a chat screen, built around voice rather than typing.
 * A large hold-to-talk button is the primary control; whatever is heard is streamed
 * live into a text field above it so the user always has clear visual feedback on
 * what will be sent, and can edit it by hand before sending if it's misheard.
 * Typing directly and using the send icon remains available as a fallback.
 */
@Composable
fun VoiceComposerBar(
    app: ITantraApp,
    placeholderIdle: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val recognizerState by app.voiceRecognizer.stateFlow.collectAsState()
    val recognizerReady = recognizerState != VoskSpeechRecognizer.State.UNINITIALIZED &&
        recognizerState != VoskSpeechRecognizer.State.LOADING &&
        recognizerState != VoskSpeechRecognizer.State.ERROR

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            onSend(trimmed)
        }
        inputText = ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Live transcript / manual-edit field — always visible so the user gets clear
        // feedback on exactly what will be sent, whether spoken or typed.
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = {
                Text(
                    text = when {
                        isListening -> "Listening…"
                        recognizerState == VoskSpeechRecognizer.State.LOADING -> "Loading speech model…"
                        else -> placeholderIdle
                    },
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isListening) ListeningRed else CyanPrimary,
                unfocusedBorderColor = if (isListening) ListeningRed else DarkBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = CyanPrimary
            ),
            trailingIcon = {
                IconButton(
                    onClick = { send(inputText) },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) CyanPrimary else TextMuted
                    )
                }
            },
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Primary control: the large push-to-talk button.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PushToTalkButton(
                    modifier = Modifier.size(92.dp),
                    idleColor = DarkSurfaceVariant,
                    activeColor = ListeningRed,
                    iconTint = if (recognizerReady) CyanPrimary else TextMuted,
                    enabled = recognizerReady,
                    onPressStart = {
                        val hasMicPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasMicPermission && recognizerReady) {
                            isListening = true
                            inputText = ""
                            app.voiceRecognizer.startListening(
                                onFinalText = { text ->
                                    isListening = false
                                    send(text)
                                },
                                onPartialText = { partial -> inputText = partial },
                                onError = {
                                    isListening = false
                                    inputText = ""
                                }
                            )
                        }
                    },
                    onPressEnd = { app.voiceRecognizer.stopListening() },
                    onPressCancel = {
                        app.voiceRecognizer.cancel()
                        isListening = false
                        inputText = ""
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        recognizerState == VoskSpeechRecognizer.State.LOADING -> "Speech model loading…"
                        recognizerState == VoskSpeechRecognizer.State.ERROR -> "Speech recognition unavailable"
                        isListening -> "Listening… release to send"
                        else -> "Hold to talk"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isListening) ListeningRed else TextSecondary
                )
            }
        }
    }
}
