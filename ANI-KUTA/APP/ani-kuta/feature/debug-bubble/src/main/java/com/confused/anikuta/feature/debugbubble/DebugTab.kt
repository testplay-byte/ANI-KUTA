package com.confused.anikuta.feature.debugbubble

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The debug panel's tabs (Phase DB).
 *
 * Each tab has a Material [ImageVector] icon (no emojis — per user) + a label.
 *
 * Task 64 (round 24): the CONSOLE tab is REMOVED with the console-logging
 * family (DebugLogBuffer/LogAppender/RingLogBuffer) — the round-24 device
 * instruction was "remove the console logs only", and every other bubble
 * tab keeps working exactly as before.
 */
enum class DebugTab(val label: String, val icon: ImageVector) {
    SCREEN("Screen", Icons.Filled.PhoneAndroid),
    DATABASE("Database", Icons.Filled.Storage),
    NETWORK("Network", Icons.Filled.Wifi),
    APP_INFO("App Info", Icons.Filled.Info),
}

/**
 * The direction the panel expands from the bubble (D-163).
 */
enum class ExpandDirection {
    RIGHT_DOWN,
    LEFT_DOWN,
    RIGHT_UP,
    LEFT_UP,
}

fun expandDirectionFor(
    bubbleX: Float,
    bubbleY: Float,
    screenWidth: Float,
    screenHeight: Float,
): ExpandDirection {
    val right = bubbleX < screenWidth / 2f
    val down = bubbleY < screenHeight / 2f
    return when {
        right && down -> ExpandDirection.RIGHT_DOWN
        !right && down -> ExpandDirection.LEFT_DOWN
        right && !down -> ExpandDirection.RIGHT_UP
        else -> ExpandDirection.LEFT_UP
    }
}
