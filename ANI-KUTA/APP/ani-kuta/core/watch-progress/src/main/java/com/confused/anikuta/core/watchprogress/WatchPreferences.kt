package com.confused.anikuta.core.watchprogress

import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * User-configurable watch-progress preferences.
 *
 * Phase WP (PLAN §2.2, Q2 resolved): the auto-mark-watched threshold is
 * **user-configurable** (default 85%, range 50%-95%). The `SqlDelightWatchProgressStore`
 * reads this at save time to decide whether to auto-mark.
 *
 * ROUND 127 (D-789): extracted to an interface so the store is JVM-testable
 * (the old concrete class needed an Android Context via [PreferenceStore];
 * the tests override just the threshold). Behavior unchanged.
 *
 * CORE_RULES §23: preference changes should propagate live. For now this is a
 * simple synchronous read (the threshold is read on each save — no Flow needed
 * since the save loop is periodic). When the Settings UI needs reactive updates,
 * add a Flow.
 */
interface WatchPreferences {

    /**
     * The auto-mark-watched threshold, as a fraction (0.50 to 0.95).
     * Default: 0.85 (85%). If the user's watch progress exceeds this fraction
     * AND the cumulative played time reached [WatchProgress.MIN_PLAYED_FOR_WATCHED_MS],
     * the episode is automatically marked as watched (unless `autoMarkSuppressed`).
     */
    var autoMarkThreshold: Float

    companion object {
        const val KEY_AUTO_MARK_THRESHOLD = "watch_auto_mark_threshold"
        const val DEFAULT_AUTO_MARK_THRESHOLD = 0.85f
    }
}

/** The production implementation — backed by [PreferenceStore]. */
class DefaultWatchPreferences(private val store: PreferenceStore) : WatchPreferences {

    override var autoMarkThreshold: Float
        get() = store.getFloat(
            WatchPreferences.KEY_AUTO_MARK_THRESHOLD,
            WatchPreferences.DEFAULT_AUTO_MARK_THRESHOLD,
        ).coerceIn(0.50f, 0.95f)
        set(value) = store.putFloat(
            WatchPreferences.KEY_AUTO_MARK_THRESHOLD,
            value.coerceIn(0.50f, 0.95f),
        )
}
