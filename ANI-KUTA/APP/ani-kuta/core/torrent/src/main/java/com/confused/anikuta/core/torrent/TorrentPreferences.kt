package com.confused.anikuta.core.torrent

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore

/**
 * The torrent feature's prefs — deliberately scoped to this module (the same
 * pattern AdPreferences uses) so removing the module removes its state with
 * it (TORRENT-REMOVAL.md).
 *
 * - [enabled]: the master toggle, DEFAULT OFF — torrent links stay hidden
 *   (the pre-torrent behavior) until the user turns this on.
 * - [disclaimerAccepted]: the first-use consent gate — set once the user
 *   accepts the disclaimer dialog.
 */
class TorrentPreferences(private val preferenceStore: PreferenceStore) {

    companion object {
        private const val TAG = "Anikuta:Core:Torrent"
        private const val KEY_ENABLED = "torrent_enabled"
        private const val KEY_DISCLAIMER = "torrent_disclaimer_accepted"
    }

    var enabled: Boolean
        get() = preferenceStore.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferenceStore.putBoolean(KEY_ENABLED, value)
            Logger.i(TAG) { "torrent_enabled persisted = $value" }
        }

    var disclaimerAccepted: Boolean
        get() = preferenceStore.getBoolean(KEY_DISCLAIMER, false)
        set(value) {
            preferenceStore.putBoolean(KEY_DISCLAIMER, value)
        }
}
