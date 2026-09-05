package com.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * D-418 (round 34 — the app-icon system): preferences for the App Icon page
 * (Settings → Appearance → App Icon).
 *
 * Backed by [PreferenceStore], replicating the [DebugPreferences] pattern.
 *
 * ## Settings
 * - `app_icon_active_alias`: the launcher icon alias currently ACTIVE (the
 *   manifest component short name, `IconV1`..`IconV8` — see the 8
 *   activity-aliases in AndroidManifest.xml). The alias enable states are
 *   ALSO persisted by PackageManager itself; this preference is the app's
 *   own record (self-heal source + UI state) — written on every switch.
 *   Default `IconV1`.
 * - `app_icon_catalog_json`: the cached GitHub catalog listing (the icons/
 *   folder of Confused-Creature-180/ANI-KUTA) so the grid survives offline.
 * - `app_icon_override_path`: the in-app override (an unbaked GitHub
 *   catalog pick — D-422, round 35: the custom-image import was REMOVED per
 *   the user's instruction; only provided options remain).
 */
class AppIconPreferences(private val store: PreferenceStore) {

    /** The active launcher-alias short name (`IconV1`..`IconV8`). Default IconV1. */
    var activeAlias: String
        get() = store.getString(KEY_ACTIVE_ALIAS, DEFAULT_ALIAS)
        set(value) = store.putString(KEY_ACTIVE_ALIAS, value)

    fun activeAliasFlow(): Flow<String> = store.stringFlow(KEY_ACTIVE_ALIAS, DEFAULT_ALIAS)

    /** The cached GitHub catalog JSON ("" when never fetched). */
    var catalogJson: String
        get() = store.getString(KEY_CATALOG_JSON, "")
        set(value) = store.putString(KEY_CATALOG_JSON, value)

    /**
     * The in-app icon OVERRIDE path — set when the user picks a GitHub
     * catalog icon that isn't baked into this release (Android forbids
     * runtime launcher icons from arbitrary bitmaps — only the baked
     * aliases can switch the home-screen icon; the custom-image import was
     * removed in D-422). "" = the in-app icon follows the active variant.
     */
    var inAppOverridePath: String
        get() = store.getString(KEY_OVERRIDE_PATH, "")
        set(value) = store.putString(KEY_OVERRIDE_PATH, value)

    companion object {
        private const val KEY_ACTIVE_ALIAS = "app_icon_active_alias"
        private const val KEY_CATALOG_JSON = "app_icon_catalog_json"
        private const val KEY_OVERRIDE_PATH = "app_icon_override_path"

        /** The manifest default (the user's kawaii-mouth icon). */
        const val DEFAULT_ALIAS = "IconV1"
    }
}
