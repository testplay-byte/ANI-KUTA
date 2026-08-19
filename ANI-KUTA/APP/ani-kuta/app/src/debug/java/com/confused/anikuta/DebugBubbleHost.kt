package com.confused.anikuta

import androidx.compose.runtime.Composable
import com.confused.anikuta.feature.debugbubble.DebugBubble
import com.confused.anikuta.feature.debugbubble.DebugBubblePreferences
import org.koin.core.context.GlobalContext

/**
 * Debug-only composable that renders the debug bubble (Phase DB).
 *
 * Lives in `:app/src/debug` so `:app/src/main` can call [DebugBubbleHost]
 * without a compile-time dependency on `:feature:debug-bubble` (which is
 * `debugImplementation`). The release source set provides a no-op counterpart.
 *
 * Fetches [DebugBubblePreferences] from Koin + delegates to [DebugBubble].
 */
@Composable
fun DebugBubbleHost() {
    val prefs = GlobalContext.get().get<DebugBubblePreferences>()
    DebugBubble(preferences = prefs)
}
