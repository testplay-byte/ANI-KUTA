package com.confused.anikuta.core.preferences

/**
 * Preferences for the auto-link system (Phase B).
 *
 * Backed by [PreferenceStore]. Three layers:
 * 1. **Global toggle** — master switch ([autoLinkEnabled]).
 * 2. **Match strategy + threshold** — controls SmartMatcher behavior.
 * 3. **Per-source overrides** — each source can be Default / Always / Never.
 * 4. **Link cache** — persists successful (sourceId, animeUrl) → anilistId
 *    mappings so re-opening an anime doesn't re-search AniList every time.
 *
 * CORE_RULES §23: The UI toggles flip mutableStateOf snapshots; the underlying
 * SharedPreferences write happens immediately. Reactive consumers (SettingsScreen)
 * recompose on state change.
 *
 * Per-source override is keyed by `sourceId` (Long) — stable across reinstalls
 * because source IDs are derived from the extension package name + source name
 * (see ExtensionLoader).
 */
class AutoLinkPreferences(private val store: PreferenceStore) {

    /** Master switch — when false, auto-link never runs (manual sheet still works). */
    var autoLinkEnabled: Boolean
        get() = store.getBoolean(KEY_AUTO_LINK_ENABLED, true)
        set(value) = store.putBoolean(KEY_AUTO_LINK_ENABLED, value)

    /** Match strategy: "fuzzy" / "strict" / "manual". */
    var strategy: String
        get() = store.getString(KEY_STRATEGY, "fuzzy")
        set(value) = store.putString(KEY_STRATEGY, value)

    /** FUZZY threshold (0.0..1.0). Stored as a Float. Default 0.80. */
    var threshold: Float
        get() = store.getFloat(KEY_THRESHOLD, 0.80f)
        set(value) = store.putFloat(KEY_THRESHOLD, value)

    // ── Per-source overrides ──────────────────────────────────────────────────

    /**
     * Per-source override: "default" / "on" / "off".
     *
     * - "default" → use the global [autoLinkEnabled] setting.
     * - "on"      → always auto-link (overrides global OFF).
     * - "off"     → never auto-link (overrides global ON).
     */
    fun getPerSourceOverride(sourceId: Long): String {
        return store.getString(keyPerSourceOverride(sourceId), "default")
    }

    fun setPerSourceOverride(sourceId: Long, value: String) {
        store.putString(keyPerSourceOverride(sourceId), value)
    }

    /**
     * Resolve the effective auto-link setting for a source:
     * global ANDed with the per-source override.
     */
    fun isAutoLinkEnabledForSource(sourceId: Long): Boolean {
        return when (getPerSourceOverride(sourceId)) {
            "on" -> true
            "off" -> false
            else -> autoLinkEnabled
        }
    }

    // ── Link cache (sourceId, animeUrl) → anilistId ───────────────────────────

    /**
     * Cached AniList ID for a given extension anime entry.
     * Returns 0 if no cache entry exists.
     */
    fun getCachedAniListId(sourceId: Long, animeUrl: String): Int {
        return store.getInt(keyLinkCache(sourceId, animeUrl), 0)
    }

    /**
     * Persist a successful link. After this, re-opening the same extension anime
     * will skip the AniList search and use the cached ID directly.
     */
    fun cacheAniListId(sourceId: Long, animeUrl: String, anilistId: Int) {
        store.putInt(keyLinkCache(sourceId, animeUrl), anilistId)
    }

    /** Clear the cache for a specific entry (used when unlinking). */
    fun clearCachedAniListId(sourceId: Long, animeUrl: String) {
        store.putInt(keyLinkCache(sourceId, animeUrl), 0)
    }

    // ── D-225b: Reverse auto-link (AniList → extensions) ──

    /** Master toggle for reverse auto-link (searching extensions when opening AniList anime). */
    var reverseAutoLinkEnabled: Boolean
        get() = store.getBoolean(KEY_REVERSE_ENABLED, true)
        set(value) = store.putBoolean(KEY_REVERSE_ENABLED, value)

    /**
     * Ordered list of extension package names for reverse auto-link search priority.
     * The first extension in this list is searched first; if no match, the next is tried.
     * Extensions not in this list are appended at the end (newly installed).
     * Empty list = use the default installation order.
     */
    var reverseAutoLinkExtensionOrder: List<String>
        get() = store.getStringList(KEY_REVERSE_EXT_ORDER, emptyList())
        set(value) = store.putStringList(KEY_REVERSE_EXT_ORDER, value)

    companion object {
        private const val KEY_AUTO_LINK_ENABLED = "auto_link_enabled"
        private const val KEY_STRATEGY = "auto_link_strategy"
        private const val KEY_THRESHOLD = "auto_link_threshold"
        private const val KEY_PER_SOURCE_PREFIX = "auto_link_source:"
        private const val KEY_LINK_CACHE_PREFIX = "auto_link_cache:"

        // D-225b: Reverse auto-link (AniList → extensions) — separate toggle + extension order.
        private const val KEY_REVERSE_ENABLED = "auto_link_reverse_enabled"
        private const val KEY_REVERSE_EXT_ORDER = "auto_link_reverse_ext_order"

        private fun keyPerSourceOverride(sourceId: Long): String =
            "$KEY_PER_SOURCE_PREFIX$sourceId"

        private fun keyLinkCache(sourceId: Long, animeUrl: String): String =
            "$KEY_LINK_CACHE_PREFIX$sourceId:${animeUrl.hashCode()}"
    }
}
