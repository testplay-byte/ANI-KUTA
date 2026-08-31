package com.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * Task 57 (round 17): preferences for the dedicated Debug settings page.
 *
 * Backed by [PreferenceStore], replicating the DebugBubblePreferences pattern
 * (feature/debug-bubble). Both toggles are opt-in diagnostics and default to
 * OFF — they surface raw resolve data only when the user explicitly asks.
 *
 * ## Settings
 * - `debug_show_resolve_sources`: reveal the raw source details (url/type)
 *   under each row of the CloudStream resolved-video lists. Default false.
 * - `debug_resolve_copy_button`: per-row copy button on the resolve lists —
 *   copies that link's full resolve details for bug reports. Default false.
 */
class DebugPreferences(private val store: PreferenceStore) {

    /** Whether CloudStream resolve lists reveal the raw source details (url/type) under each row. Default false. */
    var showResolveSources: Boolean
        get() = store.getBoolean(KEY_SHOW_SOURCES, false)
        set(value) = store.putBoolean(KEY_SHOW_SOURCES, value)

    fun showResolveSourcesFlow(): Flow<Boolean> = store.booleanFlow(KEY_SHOW_SOURCES, false)

    /** Whether resolve lists show a per-row copy button (copies that link's full resolve details for bug reports). Default false. */
    var resolveCopyButton: Boolean
        get() = store.getBoolean(KEY_COPY_BUTTON, false)
        set(value) = store.putBoolean(KEY_COPY_BUTTON, value)

    fun resolveCopyButtonFlow(): Flow<Boolean> = store.booleanFlow(KEY_COPY_BUTTON, false)

    companion object {
        private const val KEY_SHOW_SOURCES = "debug_show_resolve_sources"
        private const val KEY_COPY_BUTTON = "debug_resolve_copy_button"
    }
}
