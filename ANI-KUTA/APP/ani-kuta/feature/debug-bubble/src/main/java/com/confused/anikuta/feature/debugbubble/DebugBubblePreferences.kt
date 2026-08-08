package com.confused.anikuta.feature.debugbubble

import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow

/**
 * Preferences for the debug bubble (Phase DB).
 *
 * Backed by [PreferenceStore]. Only one preference in DB-1: the visibility
 * toggle (default `true` — visible by default in debug builds, per D-163).
 *
 * Note: the bubble's drag position is NOT persisted (D-163) — it returns to
 * the default (bottom-end) every time the app is reopened. So there are no
 * position keys here.
 */
class DebugBubblePreferences(private val store: PreferenceStore) {

    /** Whether the debug bubble is shown. Default `true` (visible by default). */
    var visible: Boolean
        get() = store.getBoolean(KEY_VISIBLE, true)
        set(value) = store.putBoolean(KEY_VISIBLE, value)

    fun visibleFlow(): Flow<Boolean> = store.booleanFlow(KEY_VISIBLE, true)

    companion object {
        private const val KEY_VISIBLE = "debug_bubble_visible"
    }
}
