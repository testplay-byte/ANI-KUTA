package com.confused.anikuta.feature.debugbubble

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
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
 *
 * ROUND 100 (D-683): the CONSOLE tab is BACK — the round-99 device report
 * asked for proper console logging on the debug version. The new console is
 * a different tool from the removed one: it browses the lines the app
 * ALREADY logs (the Logger's in-memory ring + a live logcat feed for this
 * process) instead of adding any new logging. It renders only on the debug
 * line (the bubble itself is debug-only) and captures nothing on release.
 */
enum class DebugTab(val label: String, val icon: ImageVector) {
    SCREEN("Screen", Icons.Filled.PhoneAndroid),
    DATABASE("Database", Icons.Filled.Storage),
    NETWORK("Network", Icons.Filled.Wifi),
    CONSOLE("Console", Icons.Filled.Terminal),
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
