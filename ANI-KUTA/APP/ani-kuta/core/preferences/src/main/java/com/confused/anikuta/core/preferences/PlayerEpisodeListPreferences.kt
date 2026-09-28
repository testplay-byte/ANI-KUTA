package com.confused.anikuta.core.preferences

/**
 * ROUND 101 (WS-E): preferences for the PLAYER page's episode-list
 * customization — the user's order: "for the player page, we do not have any
 * customizability functionality there… make sure that there is a proper
 * dedicated section where the user can be able to separately customize the
 * episode list of the player page properly, just like how he is able to
 * easily customize the episodes list of the Details page."
 *
 * SEPARATE from [EpisodeListPreferences] by design ("separately customize"):
 * the details page and the player page are different reading contexts — the
 * player list favors compactness and quick switching — so each surface keeps
 * its own independent settings, both persisted, both reactive.
 *
 * **Categories:**
 * 1. **Row appearance** — style (DETAILED / COMPACT / MINIMAL) + the synopsis
 *    and date-pill toggles within the style's frame.
 * 2. **Watched treatment** — dim watched rows; the watched three-state filter.
 * 3. **Sort** — episode number / upload date / alphabetical; asc/desc.
 *
 * Search needs no preference — the player list exposes it via its header
 * icon (always available, like the details page's episode search).
 *
 * CORE_RULES §23: reactive `Preference<T>` — the player list recomposes on
 * change, live.
 */
class PlayerEpisodeListPreferences(private val store: PreferenceStore) {

    // ════════════════════════════════════════════════════════════════════════
    //  1. Row appearance
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The player episode row's layout style (the details page's D-554 twin).
     *
     * - `"DETAILED"` (default): thumbnail + title + the date pill + synopsis
     *   (when the metadata carries one) — the richest row.
     * - `"COMPACT"`: a smaller row; the synopsis never renders.
     * - `"MINIMAL"`: no thumbnail, no date — number + title only. The
     *   fast-switching shape for long lists.
     */
    val rowStyle = store.preference(
        KEY_ROW_STYLE, "DETAILED", StringSerializer,
    )

    /** Show the two-line synopsis under the title (DETAILED only). */
    val showSynopsis = store.preference(
        KEY_SHOW_SYNOPSIS, true, BooleanSerializer,
    )

    /** Show the release-date pill (DETAILED + COMPACT). */
    val showDatePill = store.preference(
        KEY_SHOW_DATE_PILL, true, BooleanSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  2. Watched treatment
    // ════════════════════════════════════════════════════════════════════════

    /** Dim watched episodes (the alpha treatment; the player twin of D-554). */
    val dimWatched = store.preference(
        KEY_DIM_WATCHED, true, BooleanSerializer,
    )

    /**
     * Watched filter state.
     * - `"OFF"` (default) → no filter.
     * - `"SHOW"` → only watched episodes.
     * - `"HIDE"` → only unwatched episodes.
     */
    val watchedFilter = store.preference(
        KEY_WATCHED_FILTER, "OFF", StringSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  3. Sort
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Sort mode for the player episode list.
     * - `"EPISODE_NUMBER"` (default), `"UPLOAD_DATE"`, `"ALPHABETICAL"`.
     */
    val sortMode = store.preference(
        KEY_SORT_MODE, "EPISODE_NUMBER", StringSerializer,
    )

    /** Sort descending (true) or ascending (false, default). */
    val sortDescending = store.preference(
        KEY_SORT_DESCENDING, false, BooleanSerializer,
    )

    /** Reset the watched filter (the filtered-empty state's quick action). */
    fun resetFilters() {
        watchedFilter.set("OFF")
    }

    companion object {
        private const val KEY_ROW_STYLE = "pref_player_episode_list_row_style"
        private const val KEY_SHOW_SYNOPSIS = "pref_player_episode_list_show_synopsis"
        private const val KEY_SHOW_DATE_PILL = "pref_player_episode_list_show_date_pill"
        private const val KEY_DIM_WATCHED = "pref_player_episode_list_dim_watched"
        private const val KEY_WATCHED_FILTER = "pref_player_episode_list_watched_filter"
        private const val KEY_SORT_MODE = "pref_player_episode_list_sort_mode"
        private const val KEY_SORT_DESCENDING = "pref_player_episode_list_sort_descending"
    }
}
