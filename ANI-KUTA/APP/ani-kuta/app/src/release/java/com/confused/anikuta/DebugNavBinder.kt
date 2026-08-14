package com.confused.anikuta

import androidx.compose.runtime.Composable
import com.confused.anikuta.core.navigation.NavKey

/**
 * Release counterpart to `:app/src/debug/DebugNavBinder.kt` (D-197).
 *
 * No-op — release builds have no test-controller module on the classpath, so there's nothing
 * to bind. Same signature so `:app/src/main` can call [DebugNavBinder] unconditionally.
 */
@Composable
fun DebugNavBinder(backstack: MutableList<NavKey>) {
    // No-op in release builds.
}
