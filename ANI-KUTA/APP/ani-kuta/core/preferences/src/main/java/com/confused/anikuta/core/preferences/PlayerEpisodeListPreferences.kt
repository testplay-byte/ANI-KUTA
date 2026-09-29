package com.confused.anikuta.core.preferences

/**
 * ROUND 101 (WS-E) → ROUND 103 (WS-4) REWORKED: preferences for the PLAYER
 * page's episode-list customization — the user's order: "for the player page,
 * we do not have any customizability functionality there… make sure that there
 * is a proper dedicated section where the user can be able to separately
 * customize the episode list of the player page properly, just like how he is
 * able to easily customize the episodes list of the Details page." The
 * v1.1.59 device round then ordered the details-page STANDARD: "the actual
 * episode list on the details page is getting a total of four custom display
 * options, but the player page one is not getting things like those… build
 * custom ones for that too… just like the other one" — plus "the only sort
 * option which should be given here is ascending or descending. It does not
 * need to give any other options."
 *
 * SEPARATE from [EpisodeListPreferences] by design ("separately customize"):
 * the details page and the player page are different reading contexts — the
 * player list favors compactness and quick switching — so each surface keeps
 * its own independent settings, both persisted, both reactive.
 *
 * **Categories (ROUND 103):**
 * 1. **Row appearance** — the FOUR layout paradigms
 *    (DETAILED / COMPACT / GRID / BANNER, see [rowStyle]) + the synopsis and
 *    date-pill toggles within the style's frame.
 * 2. **Watched treatment** — dim watched rows; the watched three-state filter.
 * 3. **Sort** — DIRECTION ONLY (ascending / descending over the episode
 *    number; the round-101 sort modes are retired per the order above).
 *
 * CORE_RULES §23: reactive `Preference<T>` — the player list recomposes on
 * change, live.
 */
class PlayerEpisodeListPreferences(private val store: PreferenceStore) {

    // ════════════════════════════════════════════════════════════════════════
    //  1. Row appearance — THE FOUR PARADIGMS (ROUND 103 / D-699)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The player episode list's LAYOUT — FOUR curated paradigms, each a
     * different STRUCTURE (the details page's D-555 doctrine, player-scoped;
     * rendered through the ONE shared dispatcher in
     * `:core:designsystem/component/playerlist/PlayerEpisodeListLayouts.kt`
     * that BOTH player stacks AND the Appearance → "Player episode list"
     * live preview call — the D-481 one-source-of-truth):
     *
     * - `"DETAILED"` (default = the look the player list has always been):
     *     thumbnail + EP tag + title + date/audio pills + synopsis.
     * - `"COMPACT"`: the dense row — a smaller thumbnail, never a synopsis.
     * - `"GRID"`: the two-across poster wall — full-bleed 16:9 cells, EP
     *     badge overlays, watched = grayscale + check, current = ring + play.
     * - `"BANNER"`: the full-bleed banner card — the thumbnail AS the card,
     *     a bottom scrim, a ghost episode number, overlaid title + chips.
     *
     * LENIENT migration (the D-529 lesson): the round-101 keys DETAILED and
     * COMPACT keep their meaning; the retired "MINIMAL" (a density
     * variation, not a structure) folds into COMPACT (the closest rhythm);
     * unknown/null/blank → DETAILED.
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
    //  3. Sort — DIRECTION ONLY (ROUND 103: "the only sort option which
    //     should be given here is ascending or descending")
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Sort descending (true) or ascending (false, default) — over the
     * EPISODE NUMBER (the list's natural key). The round-101 sort modes
     * (upload date / alphabetical) are RETIRED per the v1.1.59 device
     * round's order; the stored key is simply no longer read.
     */
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
        // Retired with ROUND 103's direction-only sort (the stored value is
        // simply no longer read; kept as a tombstone so a future re-add does
        // not silently inherit a stale mode).
        private const val KEY_SORT_MODE = "pref_player_episode_list_sort_mode"
        private const val KEY_SORT_DESCENDING = "pref_player_episode_list_sort_descending"
    }
}
