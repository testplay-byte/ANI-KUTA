package com.confused.anikuta.core.preferences

/**
 * Preferences for the auto-link system (D-226 redesign).
 *
 * Two independent directions, each with its OWN toggle + strategy + threshold:
 *
 * **Forward (extension → metadata provider):**
 *   When the user opens an extension anime, search a metadata provider (AniList)
 *   by title and merge metadata if a match is found.
 *   - [autoLinkEnabled] (toggle)
 *   - [strategy] (fuzzy/strict/manual)
 *   - [threshold] (fuzzy threshold)
 *
 * **Reverse (metadata provider → extensions):**
 *   When the user opens an AniList anime with NO linked source, search the
 *   user's extensions (in priority order) for a matching SAnime.
 *   - [reverseAutoLinkEnabled] (toggle)
 *   - [reverseStrategy] (fuzzy/strict/manual)
 *   - [reverseThreshold] (fuzzy threshold)
 *   - [reverseAutoLinkExtensionOrder] (drag-to-reorder priority list)
 *
 * **Shared across both directions:**
 *   - Per-source overrides ("default" / "on" / "off") — keyed by sourceId.
 *   - Link cache (sourceId, animeUrl) → anilistId — avoids re-searching.
 *
 * **Future-proofing (D-226):** The forward direction is designed to work with
 * any metadata provider, not just AniList. Currently AniList is the only
 * provider, but the strategy/threshold/override model is provider-agnostic.
 * Adding a second provider (e.g., MAL, Kitsu) would add a new
 * `forwardProvider` pref + a new cache prefix, without changing the UI or
 * the ReverseAutoLinkService.
 *
 * CORE_RULES §23: The UI toggles flip mutableStateOf snapshots; the underlying
 * SharedPreferences write happens immediately. Reactive consumers
 * (SettingsScreen) recompose on state change.
 *
 * Per-source override is keyed by `sourceId` (Long) — stable across reinstalls
 * because source IDs are derived from the extension package name + source name
 * (see ExtensionLoader).
 */
class AutoLinkPreferences(private val store: PreferenceStore) {

    // ════════════════════════════════════════════════════════════════════════
    //  FORWARD direction (extension → AniList metadata)
    // ════════════════════════════════════════════════════════════════════════

    /** Forward master switch — when false, forward auto-link never runs. */
    var autoLinkEnabled: Boolean
        get() = store.getBoolean(KEY_AUTO_LINK_ENABLED, true)
        set(value) = store.putBoolean(KEY_AUTO_LINK_ENABLED, value)

    /** Forward match strategy: "fuzzy" / "strict" / "manual". */
    var strategy: String
        get() = store.getString(KEY_STRATEGY, "fuzzy")
        set(value) = store.putString(KEY_STRATEGY, value)

    /** Forward FUZZY threshold (0.0..1.0). Default 0.80. */
    var threshold: Float
        get() = store.getFloat(KEY_THRESHOLD, 0.80f)
        set(value) = store.putFloat(KEY_THRESHOLD, value)

    // ════════════════════════════════════════════════════════════════════════
    //  REVERSE direction (AniList → extensions) — D-226: own strategy + threshold
    // ════════════════════════════════════════════════════════════════════════

    /** Reverse master toggle (searching extensions when opening AniList anime). */
    var reverseAutoLinkEnabled: Boolean
        get() = store.getBoolean(KEY_REVERSE_ENABLED, true)
        set(value) = store.putBoolean(KEY_REVERSE_ENABLED, value)

    /**
     * Reverse match strategy: "fuzzy" / "strict" / "manual".
     * Independent from the forward [strategy] — the user may want a stricter
     * threshold when searching extensions (more false positives) vs. AniList.
     */
    var reverseStrategy: String
        get() = store.getString(KEY_REVERSE_STRATEGY, "fuzzy")
        set(value) = store.putString(KEY_REVERSE_STRATEGY, value)

    /** Reverse FUZZY threshold (0.0..1.0). Default 0.80. */
    var reverseThreshold: Float
        get() = store.getFloat(KEY_REVERSE_THRESHOLD, 0.80f)
        set(value) = store.putFloat(KEY_REVERSE_THRESHOLD, value)

    /**
     * Ordered list of extension package names for reverse auto-link search priority.
     * The first extension in this list is searched first; if no match, the next is tried.
     * Extensions not in this list are appended at the end (newly installed).
     * Empty list = use the default installation order.
     */
    var reverseAutoLinkExtensionOrder: List<String>
        get() = store.getStringList(KEY_REVERSE_EXT_ORDER, emptyList())
        set(value) = store.putStringList(KEY_REVERSE_EXT_ORDER, value)

    // ── Per-source overrides (shared by both directions) ─────────────────────

    /**
     * Per-source override: "default" / "on" / "off".
     *
     * - "default" → use the global [autoLinkEnabled] setting (forward) AND
     *   [reverseAutoLinkEnabled] (reverse).
     * - "on"      → always auto-link in BOTH directions (overrides global OFF).
     * - "off"     → never auto-link in EITHER direction (overrides global ON).
     */
    fun getPerSourceOverride(sourceId: Long): String {
        return store.getString(keyPerSourceOverride(sourceId), "default")
    }

    fun setPerSourceOverride(sourceId: Long, value: String) {
        store.putString(keyPerSourceOverride(sourceId), value)
    }

    /**
     * Resolve the effective auto-link setting for a source in the FORWARD direction:
     * global ANDed with the per-source override.
     */
    fun isAutoLinkEnabledForSource(sourceId: Long): Boolean {
        return when (getPerSourceOverride(sourceId)) {
            "on" -> true
            "off" -> false
            else -> autoLinkEnabled
        }
    }

    /**
     * D-226: Resolve the effective auto-link setting for a source in the REVERSE
     * direction. Same override logic as [isAutoLinkEnabledForSource] but uses the
     * reverse master toggle.
     */
    fun isReverseAutoLinkEnabledForSource(sourceId: Long): Boolean {
        return when (getPerSourceOverride(sourceId)) {
            "on" -> true
            "off" -> false
            else -> reverseAutoLinkEnabled
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

    companion object {
        // Forward direction
        private const val KEY_AUTO_LINK_ENABLED = "auto_link_enabled"
        private const val KEY_STRATEGY = "auto_link_strategy"
        private const val KEY_THRESHOLD = "auto_link_threshold"

        // Shared
        private const val KEY_PER_SOURCE_PREFIX = "auto_link_source:"
        private const val KEY_LINK_CACHE_PREFIX = "auto_link_cache:"

        // Reverse direction (D-226: now has own strategy + threshold)
        private const val KEY_REVERSE_ENABLED = "auto_link_reverse_enabled"
        private const val KEY_REVERSE_STRATEGY = "auto_link_reverse_strategy"
        private const val KEY_REVERSE_THRESHOLD = "auto_link_reverse_threshold"
        private const val KEY_REVERSE_EXT_ORDER = "auto_link_reverse_ext_order"

        private fun keyPerSourceOverride(sourceId: Long): String =
            "$KEY_PER_SOURCE_PREFIX$sourceId"

        private fun keyLinkCache(sourceId: Long, animeUrl: String): String =
            "$KEY_LINK_CACHE_PREFIX$sourceId:${animeUrl.hashCode()}"
    }
}
