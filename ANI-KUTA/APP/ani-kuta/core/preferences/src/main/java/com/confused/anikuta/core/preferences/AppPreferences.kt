package com.confused.anikuta.core.preferences

/**
 * App-level preferences (not player-specific, not tracker-specific).
 *
 * CORE_RULES §23: Changes to preferences should propagate live (Flow-based).
 * For now, this is a simple wrapper. Phase 4 will add Flow support when UI needs it.
 */
class AppPreferences(private val store: PreferenceStore) {

    var contentMode: String
        get() = store.getString(KEY_CONTENT_MODE, "anime")
        set(value) = store.putString(KEY_CONTENT_MODE, value)

    var activityTrackingRetentionDays: Int
        get() = store.getInt(KEY_TRACKING_RETENTION, 365) // 0 = unlimited
        set(value) = store.putInt(KEY_TRACKING_RETENTION, value)

    var animationsEnabled: Boolean
        get() = store.getBoolean(KEY_ANIMATIONS_ENABLED, true)
        set(value) = store.putBoolean(KEY_ANIMATIONS_ENABLED, value)

    var loggingEnabled: Boolean
        get() = store.getBoolean(KEY_LOGGING_ENABLED, true)
        set(value) = store.putBoolean(KEY_LOGGING_ENABLED, value)

    // ── Phase DB-OPT (extension trust fix): per-package enabled flag ──
    // Stores the package names of extensions the user has EXPLICITLY enabled.
    // Trust is by-signer (TrustService); enabled is by-package (this set).
    // Only extensions whose pkgName is in this set have their sources exposed
    // in pickers. New extensions are added here on trust; removed on untrust.
    // Extensions not in this set are "installed + trusted but disabled" — they
    // load (security OK) but their sources don't appear in Search/Details pickers.
    var enabledExtensions: Set<String>
        get() = store.getStringSet(KEY_ENABLED_EXTENSIONS, emptySet())
        set(value) = store.putStringSet(KEY_ENABLED_EXTENSIONS, value)

    fun isExtensionEnabled(pkgName: String): Boolean = pkgName in enabledExtensions

    fun enableExtension(pkgName: String) {
        enabledExtensions = enabledExtensions + pkgName
    }

    fun disableExtension(pkgName: String) {
        enabledExtensions = enabledExtensions - pkgName
    }

    // ── D-236: Details page background customization ──

    /**
     * D-236: Whether to tint the details page background image with the
     * accent color (30% alpha overlay). Default: true.
     */
    var detailsBannerTint: Boolean
        get() = store.getBoolean(KEY_DETAILS_BANNER_TINT, true)
        set(value) = store.putBoolean(KEY_DETAILS_BANNER_TINT, value)

    /**
     * D-236: Background image source for the details page.
     * - `"COVER"` → use the cover image (default).
     * - `"BANNER"` → use the banner image (falls back to cover if null).
     */
    var detailsBackgroundSource: String
        get() = store.getString(KEY_DETAILS_BG_SOURCE, "COVER")
        set(value) = store.putString(KEY_DETAILS_BG_SOURCE, value)

    /**
     * D-236: Whether to animate the details page background (slow pan).
     * Default: false (can be motion-intensive on older devices).
     */
    var detailsBannerAnimation: Boolean
        get() = store.getBoolean(KEY_DETAILS_BANNER_ANIMATION, false)
        set(value) = store.putBoolean(KEY_DETAILS_BANNER_ANIMATION, value)

    companion object {
        private const val KEY_CONTENT_MODE = "content_mode"
        private const val KEY_TRACKING_RETENTION = "tracking_retention_days"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val KEY_ENABLED_EXTENSIONS = "enabled_extensions"
        // D-236: Details page background customization.
        private const val KEY_DETAILS_BANNER_TINT = "details_banner_tint"
        private const val KEY_DETAILS_BG_SOURCE = "details_bg_source"
        private const val KEY_DETAILS_BANNER_ANIMATION = "details_banner_animation"
    }
}
