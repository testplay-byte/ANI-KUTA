package com.confused.anikuta.core.preferences

import kotlinx.coroutines.flow.Flow

/**
 * D-562 (round 74 — the REAL launcher-icon switch): preferences for the
 * App Icon page (Settings → Appearance → App Icon).
 *
 * Backed by [PreferenceStore], replicating the [DebugPreferences] pattern.
 *
 * ## The D-562 history (why this class shrank)
 *
 * - D-432 (round 37) built the page catalog-only: the icons/ folder of the
 *   published GitHub repo listed over the network. The round-37 rework also
 *   REMOVED the D-417 launcher-alias system entirely (the user's order at
 *   the time) — from then on a pick only swapped an IN-APP preview
 *   (`inAppOverridePath` + the exported PNG), never the home-screen icon.
 * - D-561 (round 73) added the six BAKED presets riding that same in-app
 *   preview machinery.
 * - The v1.1.35 device round exposed the gap: "the app icon functionality
 *   is not working… when I change the app icon in the settings, then the
 *   app icon should actually be changed for the application" — and the
 *   GitHub catalog search was ordered removed ("only keep the preset app
 *   icons").
 *
 * So D-562 restores the PROVEN D-417 alias switch (MainActivity's
 * MAIN/LAUNCHER now lives on activity-aliases; a pick enables the picked
 * alias and disables the previous one via PackageManager) and the GitHub
 * catalog is deleted outright. The ONE thing this class now holds is the
 * persisted launcher choice, `launcherIconKey` ("" = the app's default
 * icon).
 *
 * @param store the shared backing store (Koin-injected singleton).
 */
class AppIconPreferences(private val store: PreferenceStore) {

    /**
     * The ACTIVE launcher icon — the [com.confused.anikuta.settings.PresetIcon.key]
     * of the picked preset, or "" for the app's own default icon (the
     * manifest-default `.icons.IconDefault` alias). Persisted so the choice
     * survives process death; [com.confused.anikuta.AnikutaApp] reconciles it
     * against the PackageManager component states on every process start
     * (an app UPDATE restores the manifest defaults but keeps these prefs —
     * without the reconcile the launcher would fall back to the default
     * icon while the page still claimed a preset was active).
     */
    var launcherIconKey: String
        get() = store.getString(KEY_LAUNCHER_ICON, "")
        set(value) = store.putString(KEY_LAUNCHER_ICON, value)

    /** Reactive read for the page's selected-state rendering. */
    fun launcherIconKeyFlow(): Flow<String> = store.stringFlow(KEY_LAUNCHER_ICON, "")

    companion object {
        private const val KEY_LAUNCHER_ICON = "app_icon_launcher_key"
    }
}
