package com.confused.anikuta.core.player.controls

/**
 * ROUND 122 (R5): the effective intro/outro ranges shown ON the seek bar.
 *
 * Deliberately INDEPENDENT of the Off/Button/Auto skip modes — the ribbon is
 * informational (the user's order: "it should properly show on the progress
 * bar itself from where to where the intro is and from where to where the
 * outro is, regardless of the user's settings"). All values are seconds;
 * null = that boundary unknown. A manual outro has no end — the host draws
 * it to EOF; an AniSkip outro carries its real ed end.
 */
data class SkipRanges(
    val introStartSec: Int? = null,
    val introEndSec: Int? = null,
    val outroStartSec: Int? = null,
    val outroEndSec: Int? = null,
) {
    val hasIntro: Boolean get() = introStartSec != null && introEndSec != null && introEndSec > introStartSec
    val hasOutro: Boolean get() = outroStartSec != null
}
