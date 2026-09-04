package com.confused.anikuta.feature.animesearch

/**
 * Task 62 (round 22 — the randomization TRIGGER rework): the in-memory signal
 * that tells the search page WHEN the CloudStream section randomization may
 * re-run.
 *
 * The round-22 device reports on the v0.4.9 behavior (sections were shuffled
 * at FOUR sites — every page entry, every ON_RESUME, both browse landings):
 *  • "when I went to a subpage of the category or opened up any content and
 *    came back to the search page, the results had been randomized" — NO
 *    re-shuffle on back-navigation;
 *  • "if I close the app completely and reopen it, the results are reloaded
 *    [randomized]" — NO re-shuffle on app reopen (the arrangement is PERSISTED
 *    via the browse cache's display state and restored instead);
 *  • "the randomization should only happen if I leave the search page and
 *    then come back to the search page, or I try to refresh the search page".
 *
 * The signal: MainActivity's bottom-nav [onSelect] calls [markTabExit] when
 * the user switches AWAY from the search root tab (the only true tab exit —
 * subpage pushes stay inside the tab). The search screen's fresh-composition
 * entry then asks [shouldReshuffleOnEntry]: true only when the last tab exit
 * is NEWER than the last shuffle. In-memory by design — a process death
 * resets both stamps (zero → no re-shuffle on the cold reopen, exactly the
 * spec) and the ViewModel itself is already activity-scoped.
 */
object SearchTabExitSignal {

    /** Wall-clock millis of the last switch AWAY from the search tab (0 = never). */
    @Volatile
    private var lastTabExitAtMs: Long = 0L

    /** Wall-clock millis of the last section shuffle (0 = never in this process). */
    @Volatile
    private var lastShuffleAtMs: Long = 0L

    /** MainActivity's bottom-nav handler: the user left the search root tab. */
    fun markTabExit() {
        lastTabExitAtMs = System.currentTimeMillis()
    }

    /**
     * True when a fresh search-page composition should re-shuffle: the user
     * left the SEARCH TAB after our last shuffle (a subpage return leaves both
     * stamps untouched → false; the first entry of a fresh process → both 0 →
     * false).
     */
    fun shouldReshuffleOnEntry(): Boolean = lastTabExitAtMs > lastShuffleAtMs

    /** Recorded by the ViewModel right after a shuffle runs. */
    fun markShuffled() {
        lastShuffleAtMs = System.currentTimeMillis()
    }
}
