package com.itantra.mesh.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The app's primary voice-input control: a large circular hold-to-talk mic button.
 * Since this app is built around speaking rather than typing, this button is meant to
 * be the biggest, most obvious tappable element on any chat screen.
 */
@Composable
fun PushToTalkButton(
    modifier: Modifier = Modifier.size(96.dp),
    idleColor: Color,
    activeColor: Color,
    iconTint: Color,
    enabled: Boolean = true,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onPressCancel: () -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.08f else 1f,
        animationSpec = spring(),
        label = "ptt-scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(if (isPressed) activeColor else idleColor)
            .border(
                width = if (isPressed) 3.dp else 1.5.dp,
                color = if (isPressed) activeColor else iconTint.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        onPressStart()
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) onPressEnd() else onPressCancel()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Hold to talk",
            tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp)
        )
    }
}
