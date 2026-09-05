package com.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * D-432 (round 37 — the app-icon system, catalog-only): preferences for the
 * App Icon page (Settings → Appearance → App Icon).
 *
 * Backed by [PreferenceStore], replicating the [DebugPreferences] pattern.
 *
 * The round-37 rework: the 8 PREMADE BAKED app icons are REMOVED completely
 * (the user: "There are 8 premade app icons on the app icon page but I told
 * you to remove all of them and you did not. Remove them properly.") — no
 * activity-aliases, no `activeAlias` machinery. The page shows ONLY the
 * GitHub repository's icons/ catalog (the user curates that folder; icons
 * can be added/removed there at any time without an app release).
 *
 * ## Settings
 * - `app_icon_catalog_json`: the cached GitHub catalog listing (the icons/
 *   folder of Confused-Creature-180/ANI-KUTA) so the grid survives offline.
 * - `app_icon_override_path`: the in-app override (a catalog pick — applied
 *   inside the app; a catalog icon becomes the HOME-SCREEN launcher icon
 *   only when it is baked into a release).
 */
class AppIconPreferences(private val store: PreferenceStore) {

    /** The cached GitHub catalog JSON ("" when never fetched). */
    var catalogJson: String
        get() = store.getString(KEY_CATALOG_JSON, "")
        set(value) = store.putString(KEY_CATALOG_JSON, value)

    /**
     * The in-app icon OVERRIDE path — set when the user picks a GitHub
     * catalog icon (Android forbids runtime launcher icons from arbitrary
     * bitmaps; the custom-image import was removed in D-422 and stays
     * removed). "" = the in-app icon shows the launcher artwork
     * (drawable-nodpi/icon_current).
     */
    var inAppOverridePath: String
        get() = store.getString(KEY_OVERRIDE_PATH, "")
        set(value) = store.putString(KEY_OVERRIDE_PATH, value)

    companion object {
        private const val KEY_CATALOG_JSON = "app_icon_catalog_json"
        private const val KEY_OVERRIDE_PATH = "app_icon_override_path"
    }
}
