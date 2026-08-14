package com.confused.anikuta

import androidx.compose.runtime.Composable

/**
 * Release counterpart to
 * `:app/src/debug/java/com/confused/anikuta/TestControllerSettingsScreen.kt`
 * (Task ID 7-UI, D-198 v3+).
 *
 * No-op — release builds contain zero test-controller code
 * (`:core:test-controller` is `debugImplementation` only). Same signature so
 * any nav entry registered in `:app/src/main` can call
 * [TestControllerSettingsScreen] unconditionally — the call site compiles in
 * both variants and resolves to this no-op stub in release builds.
 *
 * @param onBack Pops this screen (ignored in release).
 */
@Composable
fun TestControllerSettingsScreen(onBack: () -> Unit) {
    // No-op in release builds.
}
