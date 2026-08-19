package com.confused.anikuta.core.debugapi

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for the bubble to READ the current screen's debug context.
 *
 * Provided by [AppRoot] (hoisted `MutableState<DebugContext?>`) — wraps BOTH
 * the nav content AND the bubble, so the bubble (a sibling of the nav content)
 * is inside the provider's subtree and can read the value.
 *
 * Defaults to `null` — screens that don't call [LocalDebugContextUpdater]
 * leave this null, and the bubble's "Current Screen" tab is hidden.
 *
 * **Why hoisted state + two CompositionLocals (D-162 C1 fix):** CompositionLocal
 * values flow DOWN the subtree, not across siblings. If a screen wrapped its
 * OWN content in `CompositionLocalProvider(LocalDebugContext provides …)`, the
 * bubble (a sibling of the screen in AppRoot's Box) would always read null. The
 * fix: AppRoot owns the state + provides it via [LocalDebugContext] (reader)
 * and [LocalDebugContextUpdater] (writer). Screens call the updater; the bubble
 * reads the value.
 */
val LocalDebugContext = compositionLocalOf<DebugContext?> { null }

/**
 * CompositionLocal for screens to WRITE the current debug context.
 *
 * Screens call `LocalDebugContextUpdater.current.invoke(context)` to set the
 * context (or `invoke(null)` to clear it on exit). The updater writes to the
 * hoisted `MutableState<DebugContext?>` in AppRoot, which triggers the bubble
 * to recompose with the new context.
 *
 * Defaults to a no-op `{}` — when the bubble module isn't present, calls to
 * the updater do nothing (no state to write).
 */
val LocalDebugContextUpdater = compositionLocalOf<(DebugContext?) -> Unit> { {} }
