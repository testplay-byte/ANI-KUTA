package com.confused.anikuta

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.core.testapi.DebugNavRegistry

/**
 * Binds the Compose nav backstack to [DebugNavRegistry] so the debug-only
 * [com.confused.anikuta.core.testcontroller.TestAccessibilityService] can read + mutate
 * the backstack (push_route / pop / clear_to_root / get_backstack commands — D-197 nav hook).
 *
 * Debug impl. The release source set provides a no-op mirror with the same signature so
 * `:app/src/main` can call [DebugNavBinder] unconditionally (mirrors [DebugBubbleHost]'s pattern).
 *
 * The backstack is a Compose `SnapshotStateList<NavKey>` (typed here as `MutableList<NavKey>` to
 * keep `:core:test-api` Compose-free). Mutations by the test-controller via the registry trigger
 * Compose recomposition automatically — the new screen renders immediately.
 */
@Composable
fun DebugNavBinder(backstack: MutableList<NavKey>) {
    DisposableEffect(backstack) {
        DebugNavRegistry.bind(backstack)
        onDispose { DebugNavRegistry.unbind() }
    }
}
