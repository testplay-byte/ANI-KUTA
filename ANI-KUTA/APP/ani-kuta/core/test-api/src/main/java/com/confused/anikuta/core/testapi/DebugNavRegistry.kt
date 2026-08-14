package com.confused.anikuta.core.testapi

import com.confused.anikuta.core.navigation.NavKey

/**
 * Bridge between the Compose nav backstack (owned by `AppRoot` in `:app`) and the
 * debug-only test-controller (which lives outside the Compose tree, in a Service).
 *
 * `AppRoot` binds its backstack to this registry via the `DebugNavBinder` composable
 * (debug-only, no-op in release — mirrors the `DebugBubbleHost` pattern). The
 * test-controller reads the live backstack via [current] and mutates it via standard
 * `MutableList` ops (`add`, `removeAt`). Because the bound backstack is a Compose
 * `SnapshotStateList` (which implements `MutableList`), mutations trigger Compose
 * recomposition automatically — the UI updates immediately.
 *
 * Typed as `MutableList<NavKey>` (not `SnapshotStateList`) to keep this module
 * Compose-free (`:core:test-api` is types-only with minimal deps). The runtime type
 * is still `SnapshotStateList`, so snapshot semantics apply via virtual dispatch.
 *
 * D-197 nav hook. The test-controller MUST dispatch backstack mutations to the main
 * thread (via `withContext(Dispatchers.Main)`) — Compose snapshot mutations are
 * main-thread-affine. [current] reads are safe from any thread.
 *
 * In release builds, `DebugNavBinder` is a no-op so the backstack is never bound —
 * [current] returns null and test-controller nav commands fail gracefully with
 * "NAV_NOT_BOUND" (the test-controller module isn't present in release anyway).
 */
object DebugNavRegistry {

    @Volatile
    private var backstackRef: MutableList<NavKey>? = null

    /** Called by `DebugNavBinder` (debug) when `AppRoot` composes. No-op in release. */
    fun bind(backstack: MutableList<NavKey>) {
        backstackRef = backstack
    }

    /** Called by `DebugNavBinder` (debug) on dispose. No-op in release. */
    fun unbind() {
        backstackRef = null
    }

    /** The live backstack, or null if AppRoot isn't composed / release build. */
    val current: MutableList<NavKey>?
        get() = backstackRef
}
