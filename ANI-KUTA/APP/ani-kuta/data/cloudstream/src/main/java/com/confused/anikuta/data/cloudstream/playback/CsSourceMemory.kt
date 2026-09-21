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
 *
 * D-553: the memory grew its second half — the picked RESOLUTION. The device
 * round proved the sheet's height pin works (480p pinned, decoder 856x480) but
 * dies at the re-entry path (back → tap the same episode) and the auto-advance
 * path: both re-requested play with NO height → ABR climbed to 1080p over the
 * user's 480p pick. The height now rides WITH the server as ONE memory unit:
 * the sheet/in-player picks write both, the re-entry + auto-start readers pin
 * both, and a stream that no longer offers the height self-heals through the
 * D-552 "not offered → ABR" semantics (never worse, never broken playback).
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

    /**
     * D-553: remembers the picked start height for [mainId]. [height] null or
     * non-positive height CLEARS the memory — a pick that cannot pin (a
     * declared chip / raw row, where the label lies) must not fake a height
     * preference.
     */
    fun rememberHeight(mainId: String, height: Int?) {
        if (mainId.isBlank()) return
        prefs.edit().apply {
            if (height != null && height > 0) {
                putInt(HEIGHT_KEY_PREFIX + mainId, height)
            } else {
                remove(HEIGHT_KEY_PREFIX + mainId)
            }
        }.apply()
    }

    /** D-553: the remembered start height for [mainId], null when none. */
    fun recallHeight(mainId: String): Int? =
        if (mainId.isBlank()) null
        else prefs.getInt(HEIGHT_KEY_PREFIX + mainId, 0).takeIf { it > 0 }

    private companion object {
        const val KEY_PREFIX = "server:"
        const val HEIGHT_KEY_PREFIX = "height:"
    }
}
