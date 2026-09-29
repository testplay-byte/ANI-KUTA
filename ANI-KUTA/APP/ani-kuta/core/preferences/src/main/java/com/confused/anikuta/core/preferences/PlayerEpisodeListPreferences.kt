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
 * **Categories (ROUND 105 / D-707):**
 * 1. **Row appearance** — the FOUR layout paradigms
 *    (DETAILED / TRACKLIST / GRID / BANNER, see [rowStyle]) + the synopsis,
 *    date-pill, progress-bar, banner-number/position/style, banner-size,
 *    grid-checkmark/current/titles knobs within each style's frame.
 * 2. **Watched treatment** — dim watched rows (DETAILED + TRACKLIST +
 *    BANNER; the GRID has its own checkmark knob instead — the v1.1.62
 *    round: "rather than showing the user the dim option here").
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
     * that BOTH player stacks AND the Appearance → "Player page"
     * live preview call — the D-481 one-source-of-truth):
     *
     * - `"DETAILED"` (default = the look the player list has always been):
     *     thumbnail + EP tag + title + date/audio pills + synopsis — the
     *     ONLY style that keeps the EP tag on the thumbnail (the v1.1.60
     *     round: the tags "should only be kept in the detailed view").
     * - `"TRACKLIST"` (ROUND 104 — replaces COMPACT): the typographic list —
     *     the episode NUMBER as the hero element (ROUND 105: exact-fit
     *     column + 24sp — no dead left padding), a hairline spine, the
     *     title + pills + an optional two-line synopsis to its right; never
     *     a thumbnail.
     * - `"GRID"`: the two-across poster wall — full-bleed 16:9 cells; the
     *     episode number rides the bottom scrim's title line (the top-left
     *     tag is gone); watched = the checkmark treatment (see
     *     [gridWatchedCheckmark]); current = the play disc or the themed
     *     tint (see [gridCurrentStyle]).
     * - `"BANNER"`: the banner card — the thumbnail AS the card, a bottom
     *     scrim, an OPTIONAL big episode number with its position + style
     *     knobs (see [showEpisodeNumber], [bannerNumberPosition],
     *     [bannerNumberStyle]), overlaid title + pills, and an item-SIZE
     *     scale (see [bannerSize]; the aspect is fixed 16:9).
     *
     * LENIENT migration (the D-529 lesson): the retired round-101/103 keys
     * (COMPACT, MINIMAL — the density variations the v1.1.60 round called
     * "just trash") fold into TRACKLIST (the slot's replacement); unknown,
     * null or blank → DETAILED.
     */
    val rowStyle = store.preference(
        KEY_ROW_STYLE, "DETAILED", StringSerializer,
    )

    /**
     * Show the two-line synopsis under the title (DETAILED + TRACKLIST —
     * ROUND 105 widens it from DETAILED-only: "there was no option to turn
     * on or show the synopsis or turn off the synopsis" in the Tracklist).
     */
    val showSynopsis = store.preference(
        KEY_SHOW_SYNOPSIS, true, BooleanSerializer,
    )

/**
 * Show the release-date pill (DETAILED + TRACKLIST + GRID + BANNER).
 */
    val showDatePill = store.preference(
        KEY_SHOW_DATE_PILL, true, BooleanSerializer,
    )

    /**
     * ROUND 106 (WS-D): show the AUDIO pills (SUB / DUB / HSUB + the CS
     * flavor tags) on every style — the details page's parity knob ("all
     * the relevant options for each one of the layouts should be available
     * and easily customizable"). The pills WRAP now — every tag is shown.
     */
    val showAudioPills = store.preference(
        KEY_SHOW_AUDIO_PILLS, true, BooleanSerializer,
    )

    /**
     * ROUND 106 (WS-D): THE DOWNLOAD BUTTON — "in the player page there
     * could be a toggle for this, like a dedicated separate toggle, like
     * given a proper dedicated section for it, like download… If turned off,
     * then on the player page the download button will not show. But if it
     * is turned on, then the download button will show." Default OFF — the
     * player list's current (button-less) look is what everyone knows; the
     * toggle is the opt-in. When on, every one of the four layouts carries
     * the badge (the full state contract: download / in-flight / progress /
     * pause / resume / retry / play-downloaded).
     */
    val showDownloadButton = store.preference(
        KEY_SHOW_DOWNLOAD_BUTTON, false, BooleanSerializer,
    )

    /**
     * ROUND 106 (WS-D): the BANNER's currently-playing treatment — the GRID's
     * PLAY/TINT knob, ported: "the grid view has the ability to select
     * between play button and the themed tint, but the banner does not have
     * it. So I want you to implement it there properly too." "PLAY" (the
     * default — the centered play disc, today's look) or "TINT" (the
     * grayscaled imagery under a themed wash + the ring).
     */
    val bannerCurrentStyle = store.preference(
        KEY_BANNER_CURRENT_STYLE, "PLAY", StringSerializer,
    )

    /**
     * Show the thin watch-progress bar / underline (DETAILED + TRACKLIST) —
     * ROUND 105: "I should be given an option there to show or hide the
     * progress bar."
     */
    val showProgressBar = store.preference(
        KEY_SHOW_PROGRESS_BAR, true, BooleanSerializer,
    )

    /**
     * Show the BANNER's big episode number (top corner). ROUND 104 / D-703
     * made it a live toggle; ROUND 105 adds the position + the style knobs.
     */
    val showEpisodeNumber = store.preference(
        KEY_SHOW_EPISODE_NUMBER, true, BooleanSerializer,
    )

    /**
     * The BANNER's episode-number corner — "TOP_START" (top-left) or
     * "TOP_END" (top-right, the default). The stored vocabulary IS the
     * enum's (SA2-F1 fix, lead-verified: the write side must speak exactly
     * what fromKey parses). Lenient parse through the display bundle's
     * fromKey (unknown values fold to the default).
     */
    val bannerNumberPosition = store.preference(
        KEY_BANNER_NUMBER_POSITION, "TOP_END", StringSerializer,
    )

    /**
     * The BANNER's episode-number style — "FROSTED" (the default; the
     * two-copy blurred-glass text, the details page's CINEMA D-559
     * treatment) or "SOLID" (the straight themed numeral, D-558).
     */
    val bannerNumberStyle = store.preference(
        KEY_BANNER_NUMBER_STYLE, "FROSTED", StringSerializer,
    )

    /**
     * The BANNER's ITEM SIZE — 0f (small: the cards sit at 66% of the row
     * width, centered, with breathing room between them) … 1f (full-bleed,
     * the classic look; the DEFAULT). ROUND 105 re-aims the round-104 knob:
     * "It should not change the height of the banner, but what it should
     * change is the actual size of the whole thumbnail cover image banner
     * itself… If the user has selected it to be smaller, then there will be
     * some padding on the left and right sides and also some padding
     * between each individual episodes themselves." The aspect is FIXED at
     * 16:9 now; the old aspect-driven bannerDensity is retired (its stored
     * semantics are incompatible — the D-529 tombstone lesson).
     */
    val bannerSize = store.preference(
        KEY_BANNER_SIZE, 1f, FloatSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  2. Watched treatment
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Dim watched episodes (DETAILED + TRACKLIST + BANNER — the alpha
     * treatment; the player twin of D-554). The GRID reads
     * [gridWatchedCheckmark] instead (ROUND 105: "it should properly give
     * the user one option, which is to show the checkmark on the watched
     * episodes or not, rather than showing the user the dim option here").
     */
    val dimWatched = store.preference(
        KEY_DIM_WATCHED, true, BooleanSerializer,
    )

    // ════════════════════════════════════════════════════════════════════════
    //  2b. The GRID's own treatments (ROUND 105 / D-707)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * The GRID's watched treatment — the grayscale + the centered check
     * together (default on). Replaces the dim knob on this style.
     */
    val gridWatchedCheckmark = store.preference(
        KEY_GRID_WATCHED_CHECKMARK, true, BooleanSerializer,
    )

    /**
     * The GRID's currently-playing treatment — "PLAY" (the default: the
     * centered play disc, today's look) or "TINT" ("if the user has selected
     * theme, then the whole thumbnail image will be tinted" — the grayscale
     * imagery under a themed wash + the ring).
     */
    val gridCurrentStyle = store.preference(
        KEY_GRID_CURRENT_STYLE, "PLAY", StringSerializer,
    )

    /**
     * The GRID's title strip — the bottom scrim's "EP N · Title" line
     * (default on; off = the clean image wall).
     */
    val gridTitles = store.preference(
        KEY_GRID_TITLES, true, BooleanSerializer,
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
        private const val KEY_SHOW_AUDIO_PILLS = "pref_player_episode_list_show_audio_pills"
        private const val KEY_SHOW_DOWNLOAD_BUTTON = "pref_player_episode_list_show_download_button"
        private const val KEY_BANNER_CURRENT_STYLE = "pref_player_episode_list_banner_current_style"
        private const val KEY_SHOW_PROGRESS_BAR = "pref_player_episode_list_show_progress_bar"
        private const val KEY_SHOW_EPISODE_NUMBER =
            "pref_player_episode_list_show_episode_number"
        private const val KEY_BANNER_NUMBER_POSITION =
            "pref_player_episode_list_banner_number_position"
        private const val KEY_BANNER_NUMBER_STYLE =
            "pref_player_episode_list_banner_number_style"
        private const val KEY_BANNER_SIZE = "pref_player_episode_list_banner_size"
        private const val KEY_GRID_WATCHED_CHECKMARK =
            "pref_player_episode_list_grid_watched_checkmark"
        private const val KEY_GRID_CURRENT_STYLE =
            "pref_player_episode_list_grid_current_style"
        private const val KEY_GRID_TITLES = "pref_player_episode_list_grid_titles"
        private const val KEY_DIM_WATCHED = "pref_player_episode_list_dim_watched"
        // Tombstones (no longer read): the round-101 sort mode, the
        // round-102/103 watched filter, and the round-104 aspect-driven
        // banner density (ROUND 105 re-aimed the knob at the item SIZE —
        // the old key's stored semantics are incompatible; default 1f
        // preserves the full-bleed look).
        private const val KEY_SORT_MODE = "pref_player_episode_list_sort_mode"
        private const val KEY_WATCHED_FILTER = "pref_player_episode_list_watched_filter"
        private const val KEY_BANNER_DENSITY = "pref_player_episode_list_banner_density"
        private const val KEY_SORT_DESCENDING = "pref_player_episode_list_sort_descending"
    }
}
