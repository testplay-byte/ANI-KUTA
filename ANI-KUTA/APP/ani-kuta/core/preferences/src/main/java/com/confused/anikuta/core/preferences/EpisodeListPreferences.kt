package com.confused.anikuta.core.preferences

/**
 * D-230: Preferences for the episode list customization system.
 *
 * Controls the episode list on BOTH the Details page + the Watch page.
 * Settings are GLOBAL (apply to all anime) — per-anime overrides can be
 * added in a future iteration if requested.
 *
 * **Categories:**
 * 1. **Thumbnail fallback** — what to show when an episode has no per-episode
 *    thumbnail: fall back to the anime's cover image, or show no image.
 * 2. **Filters** — downloaded-only, unseen-only, seen-only (three-state: off/show/hide).
 * 3. **Sort** — by episode number, upload date, or alphabetical; ascending/descending.
 * 4. **Audio filter** — sub, dub, or both.
 * 5. **Grouping** — for long series (100+ episodes), group into chunks of 100/200/300/400.
 *
 * Uses the reactive `Preference<T>` pattern so the UI auto-recomposes on change.
 *
 * CORE_RULES §23: Settings changes propagate live (the next episode list
 * recomposition reads the current values).
 */
class EpisodeListPreferences(private val store: PreferenceStore) {

    // ════════════════════════════════════════════════════════════════════════
    //  1. Thumbnail fallback
    // ════════════════════════════════════════════════════════════════════════

    /**
     * What to show when an episode has no per-episode thumbnail.
     * - `"COVER"` → fall back to the anime's cover image (default).
     * - `"NONE"` → show no image (a bare placeholder).
     */
    val thumbnailFallback = store.preference(
        KEY_THUMBNAIL_FALLBACK, "COVER", StringSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  2. Filters (three-state: off / show-only / hide)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Downloaded filter state.
     * - `"OFF"` → no filter (default).
     * - `"SHOW"` → show only downloaded episodes.
     * - `"HIDE"` → show only non-downloaded episodes.
     */
    val downloadedFilter = store.preference(
        KEY_DOWNLOADED_FILTER, "OFF", StringSerializer,
    )

    /**
     * Watched/seen filter state.
     * - `"OFF"` → no filter (default).
     * - `"SHOW"` → show only watched episodes.
     * - `"HIDE"` → show only unwatched episodes.
     */
    val watchedFilter = store.preference(
        KEY_WATCHED_FILTER, "OFF", StringSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  3. Sort
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Sort mode for the episode list.
     * - `"EPISODE_NUMBER"` → sort by episode number (default).
     * - `"UPLOAD_DATE"` → sort by upload/air date.
     * - `"ALPHABETICAL"` → sort by title alphabetically.
     */
    val sortMode = store.preference(
        KEY_SORT_MODE, "EPISODE_NUMBER", StringSerializer,
    )

    /** Sort descending (true) or ascending (false, default). */
    val sortDescending = store.preference(
        KEY_SORT_DESCENDING, false, BooleanSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  4. Audio filter
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Audio type filter.
     * - `"BOTH"` → show all episodes (default).
     * - `"SUB"` → show only subbed episodes.
     * - `"DUB"` → show only dubbed episodes.
     */
    val audioFilter = store.preference(
        KEY_AUDIO_FILTER, "BOTH", StringSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  5. Grouping (for long series)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Grouping size for long series.
     * - `0` → no grouping (default).
     * - `100` → group into chunks of 100.
     * - `200` → group into chunks of 200.
     * - `300` → group into chunks of 300.
     * - `400` → group into chunks of 400.
     *
     * Grouping only activates when the episode count exceeds the group size.
     * The group switcher UI appears between the "Episodes" text and the source pill.
     */
    val groupingSize = store.preference(
        KEY_GROUPING_SIZE, 0, IntSerializer,
    )

    // ══════════════════════════════════════════════════════════════════════
    //  7. Season organization (D-307)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * D-307 (legacy): When a series' episode names carry season tags
     * ("( Season 5 - Episode 12 - ... )"), organize the episode list by
     * SEASONS (horizontally-scrollable chip selector) instead of number-range
     * grouping.
     *
     * D-317: superseded by [organizeMode] (three states). KEPT ONLY as the
     * migration source — the new pref's DEFAULT derives from this value, so
     * users who explicitly chose "Number groups" before keep that choice.
     */
    @Deprecated("Superseded by organizeMode (D-317) — kept as the migration source only")
    val organizeBySeasons = store.preference(
        KEY_ORGANIZE_BY_SEASONS, true, BooleanSerializer,
    )

    /**
     * D-317: How the episode list is organized (user spec — three states):
     * - `"SEASONS"` (default) — season chip selector; number-range grouping
     *   suppressed; within a selected season the rows show per-season numbers.
     * - `"NUMBER_GROUPS"` — the classic number-range grouping (EP 1-100, …)
     *   for long series, configured by [groupingSize].
     * - `"OFF"` — a flat list: no season selector, no range groups.
     *
     * Falls back to number-group behavior when no multi-season structure is
     * detected for the current series (SEASONS with nothing to organize).
     */
    val organizeMode = store.preference(
        KEY_ORGANIZE_MODE,
        // Migration: users of the old boolean keep their explicit choice.
        @Suppress("DEPRECATION") if (organizeBySeasons.get()) "SEASONS" else "NUMBER_GROUPS",
        StringSerializer,
    )

    /**
     * D-317: Show the season inside the episode tag — "S-3/E-5" style with the
     * season + episode in two shades of the theme color. Only applies to the
     * ALL-episodes list (organize OFF / number groups / the "All" chip) —
     * within a selected season the plain per-season number is shown instead
     * (user spec).
     *
     * D-324: the compound tag only ever renders for ACTIVATED multi-season
     * content (≥2 detected seasons). No-season and single-season lists always
     * show the plain "EP n" tag — a season prefix there is noise (user
     * feedback 2026-08-29).
     */
    val seasonTagInNumber = store.preference(
        KEY_SEASON_TAG_IN_NUMBER, true, BooleanSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  6. Next episode release date display
    // ════════════════════════════════════════════════════════════════════════

    /**
     * D-233: Whether to show the next upcoming episode (with countdown) at the
     * top/bottom of the episode list. When enabled, if there's a future-dated
     * episode in the metadata, it's shown as a special card with the release
     * date + countdown instead of the normal episode row.
     *
     * Default: true (most users want to see upcoming episodes).
     */
    val showNextEpisode = store.preference(
        KEY_SHOW_NEXT_EPISODE, true, BooleanSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  8. Sub/Dub episode display (Task 55 / round 15)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Task 55: how episodes of series that emit BOTH (Sub) and (Dub) rows
     * (some CloudStream extensions) are displayed. Only affects lists that
     * actually carry tagged rows — aniyomi lists are a no-op.
     *
     * - `"SEPARATE"` (default = current look): sub and dub rows stay distinct;
     *     a Sub | Dub chip switcher appears above the episode list when both
     *     flavors exist.
     * - `"COMBINED"`: sibling rows merge into ONE row per episode (the tag is
     *     stripped); tapping it resolves BOTH variants — the resolve sheet's
     *     audio-version chips (SUB/DUB) let the user pick the stream.
     */
    val subDubMode = store.preference(
        KEY_SUB_DUB_MODE, "SEPARATE", StringSerializer,
    )

    /**
     * D-233: Reset all filters to their defaults (downloaded=OFF, watched=OFF,
     * audio=BOTH). Called when the user taps "Reset filters" on the empty-state.
     */
    fun resetFilters() {
        downloadedFilter.set("OFF")
        watchedFilter.set("OFF")
        audioFilter.set("BOTH")
    }

    companion object {
        private const val KEY_THUMBNAIL_FALLBACK = "pref_episode_list_thumbnail_fallback"
        private const val KEY_DOWNLOADED_FILTER = "pref_episode_list_downloaded_filter"
        private const val KEY_WATCHED_FILTER = "pref_episode_list_watched_filter"
        private const val KEY_SORT_MODE = "pref_episode_list_sort_mode"
        private const val KEY_SORT_DESCENDING = "pref_episode_list_sort_descending"
        private const val KEY_AUDIO_FILTER = "pref_episode_list_audio_filter"
        private const val KEY_GROUPING_SIZE = "pref_episode_list_grouping_size"
        private const val KEY_SHOW_NEXT_EPISODE = "pref_episode_list_show_next_episode"
        private const val KEY_ORGANIZE_BY_SEASONS = "pref_episode_list_organize_by_seasons"
        private const val KEY_ORGANIZE_MODE = "pref_episode_list_organize_mode"
        private const val KEY_SEASON_TAG_IN_NUMBER = "pref_episode_list_season_tag_in_number"
        private const val KEY_SUB_DUB_MODE = "pref_episode_list_sub_dub_mode"
    }
}
