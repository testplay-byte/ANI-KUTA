package com.confused.anikuta.data.cloudstream.playback

import android.content.Context

/**
 * Task 53 / RC-6 (AnymeX "Remember Server" pattern): remembers the last source
 * the user played PER ANIME (keyed by mainId). The resolve sheet auto-selects
 * the remembered server the moment it streams in — tapping a watched show's
 * episode feels instant — and the watch screen's auto-advance prefers it.
 *
 * Deliberately tiny: the remembered value is the link's NAME WITHOUT the
 * quality suffix (e.g. "SUB (Vidwish)"), so a match survives quality changes
 * between episodes. Quality within a matched server is still picked max-first.
 */
class CsSourceMemory(context: Context) {

    private val prefs = context.getSharedPreferences("cs_source_memory", Context.MODE_PRIVATE)

    /** Remembers [serverLabel] for [mainId] (blank mainId = no-op). */
    fun remember(mainId: String, serverLabel: String) {
        if (mainId.isBlank() || serverLabel.isBlank()) return
        prefs.edit().putString(KEY_PREFIX + mainId, serverLabel).apply()
    }

    /** The remembered server label for [mainId], null when none. */
    fun recall(mainId: String): String? =
        if (mainId.isBlank()) null else prefs.getString(KEY_PREFIX + mainId, null)

    private companion object {
        const val KEY_PREFIX = "server:"
    }
}
