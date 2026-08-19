package com.confused.anikuta.core.watchprogress

import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * User-configurable watch-progress preferences.
 *
 * Phase WP (PLAN §2.2, Q2 resolved): the auto-mark-watched threshold is
 * **user-configurable** (default 85%, range 50%-95%). The `SqlDelightWatchProgressStore`
 * reads this at save time to decide whether to auto-mark.
 *
 * CORE_RULES §23: preference changes should propagate live. For now this is a
 * simple synchronous wrapper (the threshold is read on each save — no Flow needed
 * since the save loop is periodic). When the Settings UI needs reactive updates,
 * add a Flow.
 */
class WatchPreferences(private val store: PreferenceStore) {

    /**
     * The auto-mark-watched threshold, as a fraction (0.50 to 0.95).
     * Default: 0.85 (85%). If the user's watch progress exceeds this fraction,
     * the episode is automatically marked as watched (unless `autoMarkSuppressed`).
     */
    var autoMarkThreshold: Float
        get() = store.getFloat(KEY_AUTO_MARK_THRESHOLD, 0.85f).coerceIn(0.50f, 0.95f)
        set(value) = store.putFloat(KEY_AUTO_MARK_THRESHOLD, value.coerceIn(0.50f, 0.95f))

    companion object {
        private const val KEY_AUTO_MARK_THRESHOLD = "watch_auto_mark_threshold"
    }
}
