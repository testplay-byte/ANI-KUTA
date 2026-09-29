package com.confused.anikuta.core.preferences

/**
 * ROUND 101 (WS-E) → ROUND 103 (WS-4) → ROUND 104 (WS-D / D-703) REWORKED:
 * preferences for the PLAYER page's episode-list customization — the user's
 * order: "for the player page, we do not have any customizability
 * functionality there… make sure that there is a proper dedicated section
 * where the user can be able to separately customize the episode list of the
 * player page properly, just like how he is able to easily customize the
 * episodes list of the Details page."
 *
 * SEPARATE from [EpisodeListPreferences] by design ("separately customize"):
 * the details page and the player page are different reading contexts — the
 * player list favors compactness and quick switching — so each surface keeps
 * its own independent settings, both persisted, both reactive.
 *
 * **Categories (ROUND 104 / D-703):**
 * 1. **Row appearance** — the FOUR layout paradigms
 *    (DETAILED / TRACKLIST / GRID / BANNER, see [rowStyle]) + the synopsis,
 *    date-pill, banner-number and density knobs within each style's frame.
 *    The round-103 COMPACT ("just trash… completely remove it") is REPLACED
 *    by TRACKLIST — the from-scratch typographic paradigm (the number as the
 *    hero); the lenient migration folds the retired COMPACT/MINIMAL keys
 *    into TRACKLIST (the slot's replacement inherits its users).
 * 2. **Watched treatment** — dim watched rows. THE WATCHED FILTER IS GONE
 *    (the v1.1.60 round: "the option for watch filter as off, show watched
 *    and hide watched is most definitely not good. It should be completely
 *    removed") — the pref, both stacks' filter branches, and the settings
 *    card all retired together.
 * 3. **Sort** — DIRECTION ONLY (ascending / descending over the episode
 *    number; the round-101 sort modes retired since round 103).
 *
 * CORE_RULES §23: reactive `Preference<T>` — the player list recomposes on
 * change, live.
 */
class PlayerEpisodeListPreferences(private val store: PreferenceStore) {

    // ════════════════════════════════════════════════════════════════════════
    //  1. Row appearance — THE FOUR PARADIGMS (ROUND 104 / D-703)
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
     *     thumbnail + EP tag + title + date/audio pills + synopsis — the
     *     ONLY style that keeps the EP tag on the thumbnail (the v1.1.60
     *     round: the tags "should only be kept in the detailed view").
     * - `"TRACKLIST"` (ROUND 104 — replaces COMPACT): the typographic list —
     *     the episode NUMBER as the hero element, a hairline spine, the
     *     title + pills to its right; never a thumbnail, never a synopsis.
     * - `"GRID"`: the two-across poster wall — full-bleed 16:9 cells; the
     *     episode number rides the bottom scrim's title line (the top-left
     *     tag is gone); watched = grayscale + check, current = ring + play.
     * - `"BANNER"`: the full-bleed banner card — the thumbnail AS the card,
     *     a bottom scrim, an OPTIONAL ghost episode number (see
     *     [showEpisodeNumber]), overlaid title + chips, a DENSITY-driven
     *     aspect (see [bannerDensity]).
     *
     * LENIENT migration (the D-529 lesson): the retired round-101/103 keys
     * (COMPACT, MINIMAL — the density variations the v1.1.60 round called
     * "just trash") fold into TRACKLIST (the slot's replacement); unknown,
     * null or blank → DETAILED.
     */
    val rowStyle = store.preference(
        KEY_ROW_STYLE, "DETAILED", StringSerializer,
    )

    /** Show the two-line synopsis under the title (DETAILED only). */
    val showSynopsis = store.preference(
        KEY_SHOW_SYNOPSIS, true, BooleanSerializer,
    )

    /** Show the release-date pill (DETAILED + TRACKLIST + BANNER). */
    val showDatePill = store.preference(
        KEY_SHOW_DATE_PILL, true, BooleanSerializer,
    )

    /**
     * Show the BANNER's ghost episode number (the huge translucent numeral,
     * top-end). ROUND 104 / D-703: "the episode number is not shown
     * properly, it is not customizable" — now it is a live toggle.
     */
    val showEpisodeNumber = store.preference(
        KEY_SHOW_EPISODE_NUMBER, true, BooleanSerializer,
    )

    /**
     * The BANNER's density — 0f (flat, 21:9 cinematic strips) … 1f (tall,
     * 4:3 preview cards); 0.5f ≈ the classic 16:9. ROUND 104 / D-703: "add
     * a density slider too, like I can select what the size of them should
     * be easily, and it would properly show in live view."
     */
    val bannerDensity = store.preference(
        KEY_BANNER_DENSITY, 0.5f, FloatSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  2. Watched treatment
    // ════════════════════════════════════════════════════════════════════════

    /** Dim watched episodes (the alpha treatment; the player twin of D-554). */
    val dimWatched = store.preference(
        KEY_DIM_WATCHED, true, BooleanSerializer,
    )

    // (ROUND 104 / D-703: the watched FILTER — Off / Show watched / Hide
    // watched — is COMPLETELY REMOVED per the v1.1.60 device round's order.
    // The stored key is no longer read anywhere; kept as a tombstone so a
    // future re-add does not silently inherit a stale mode.)

    // ════════════════════════════════════════════════════════════════════════
    //  3. Sort — DIRECTION ONLY (ROUND 103: "the only sort option which
    //     should be given here is ascending or descending")
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Sort descending (true) or ascending (false, default) — over the
     * EPISODE NUMBER (the list's natural key).
     */
    val sortDescending = store.preference(
        KEY_SORT_DESCENDING, false, BooleanSerializer,
    )

    companion object {
        private const val KEY_ROW_STYLE = "pref_player_episode_list_row_style"
        private const val KEY_SHOW_SYNOPSIS = "pref_player_episode_list_show_synopsis"
        private const val KEY_SHOW_DATE_PILL = "pref_player_episode_list_show_date_pill"
        private const val KEY_SHOW_EPISODE_NUMBER =
            "pref_player_episode_list_show_episode_number"
        private const val KEY_BANNER_DENSITY = "pref_player_episode_list_banner_density"
        private const val KEY_DIM_WATCHED = "pref_player_episode_list_dim_watched"
        // Tombstones (no longer read): the round-101 sort mode + the
        // round-102/103 watched filter.
        private const val KEY_SORT_MODE = "pref_player_episode_list_sort_mode"
        private const val KEY_WATCHED_FILTER = "pref_player_episode_list_watched_filter"
        private const val KEY_SORT_DESCENDING = "pref_player_episode_list_sort_descending"
    }
}
