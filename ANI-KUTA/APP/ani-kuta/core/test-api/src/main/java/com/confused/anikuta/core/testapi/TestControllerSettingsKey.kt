package com.confused.anikuta.core.testapi

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * NavKey for the Test Controller Settings screen (D-198 v3).
 *
 * Declared in `:core:test-api` (always on classpath) so `:app/src/main/MainActivity.kt`
 * can reference it in the `when(currentKey)` dispatch without depending on the debug-only
 * `:core:test-controller` impl. The actual composable is in `:app/src/debug` (real impl)
 * + `:app/src/release` (no-op mirror) — same pattern as [DebugBubbleHost].
 *
 * Reached via: More → Settings → Test Controller (top of the Settings list).
 */
@Serializable
object TestControllerSettingsKey : NavKey
