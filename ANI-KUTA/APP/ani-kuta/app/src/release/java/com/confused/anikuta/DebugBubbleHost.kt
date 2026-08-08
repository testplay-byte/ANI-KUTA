package com.confused.anikuta

import androidx.compose.runtime.Composable

/**
 * Release counterpart to `:app/src/debug/DebugBubbleHost.kt` (Phase DB).
 *
 * No-op — release builds do not render the debug bubble. Same signature so
 * `:app/src/main` can call [DebugBubbleHost] unconditionally.
 */
@Composable
fun DebugBubbleHost() {
    // No-op in release builds.
}
