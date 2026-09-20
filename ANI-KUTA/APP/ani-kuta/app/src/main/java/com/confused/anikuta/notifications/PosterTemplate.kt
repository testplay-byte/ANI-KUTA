package com.confused.anikuta.notifications

/**
 * D-524: the FIVE PREDEFINED POSTER TEMPLATES — the round-55 verdict retired
 * the Poster Studio ("we are doing a little bit overboard ... we should not
 * give the users that much customizability"). Instead of free-form element
 * pinning, the user picks ONE of five carefully-designed arrangements and the
 * composer draws it deterministically. No saved anchors, no absolute mode, no
 * layout JSON — the template enum IS the layout.
 *
 * The five (canvas 1024×400 — see [PosterCanvasMetrics]):
 *
 *  ┌─ CLASSIC ──────────────────────────────────────────────────────────────┐
 *  │ The v1.1.14-approved banner, unchanged: the 16:9 episode-still card    │
 *  │ vertically centered on the left, the text column top-right (title ≤2   │
 *  │ lines → [EP n][SUB][DUB] tag row → episode title), branding            │
 *  │ bottom-right.                                                          │
 *  ├─ SPOTLIGHT ────────────────────────────────────────────────────────────┤
 *  │ The billboard: the art fills the whole stage and the text stack hangs  │
 *  │ BOTTOM-LEFT (title ≤2 → tag row → episode title), the branding moves   │
 *  │ top-right, and the episode still (if the toggle is on) shrinks to a    │
 *  │ small corner card at the TOP-LEFT — the art itself is the star.        │
 *  ├─ SPLIT ────────────────────────────────────────────────────────────────┤
 *  │ The magazine cover: a TALL episode panel fills the left half           │
 *  │ (420×344), the right column is vertically CENTERED (title ≤3 lines →   │
 *  │ tag row → episode title), branding bottom-right.                       │
 *  ├─ MINIMAL ──────────────────────────────────────────────────────────────┤
 *  │ The symmetric poster: no card at all — title, tag row and episode      │
 *  │ title all CENTERED in the frame, branding bottom-right. The clean      │
 *  │ look for gorgeous key art.                                             │
 *  ├─ CARD ─────────────────────────────────────────────────────────────────┤
 *  │ The info card: a dark rounded panel docks to the bottom of the art;    │
 *  │ the text lives INSIDE the panel (title ≤2 → tag row → episode title),  │
 *  │ the episode still (if on) pokes above the panel's left edge, and the   │
 *  │ branding sits top-right.                                               │
 *  └────────────────────────────────────────────────────────────────────────┘
 *
 * Every template reuses the SAME primitives (adaptive scrim, cover-crop,
 * soft shadows, the chip row) so the app's visual language survives intact —
 * only the ARRANGEMENT differs. The element toggles (episode title,
 * thumbnail, badges, branding) keep gating the same elements in every
 * template; where an element has no natural home (the Minimal template's
 * symmetry, e.g.) the composer simply omits it and the live preview shows
 * the truth.
 */
enum class PosterTemplate(
    val key: String,
    val label: String,
) {
    CLASSIC("classic", "Classic"),
    SPOTLIGHT("spotlight", "Spotlight"),
    SPLIT("split", "Split"),
    MINIMAL("minimal", "Minimal"),
    CARD("card", "Card"),
    ;

    companion object {
        /**
         * Lenient parse: unknown/legacy keys (including a pre-template
         * install that never stored anything) degrade to CLASSIC — the
         * approved factory look. A bad value must never kill a banner.
         */
        fun fromKey(raw: String?): PosterTemplate =
            entries.firstOrNull { it.key == raw?.trim()?.lowercase() } ?: CLASSIC
    }
}

/**
 * D-503: the banner canvas' shared geometry — the ONE source of truth for
 * the composer's five templates and the settings screen's preview ratio.
 * (Formerly hosted in PosterLayoutConfig.kt; that file and its free-form
 * layout model were retired with the Poster Studio in D-523, and the
 * geometry moved here — the template file is now the layout's home.)
 */
object PosterCanvasMetrics {
    const val WIDTH = 1024
    const val HEIGHT = 400

    const val MARGIN = 28f
    const val LEFT = 44f
    const val TOP = 48f

    // The LEFT thumbnail card (fixed 16:9, D-499).
    const val THUMB_BOX_W = 400f
    const val THUMB_BOX_H = 225f
    const val THUMB_CORNER = 20f

    // The text column (right of the thumbnail card when one is on stage).
    const val THUMB_TEXT_GAP = 28f
    const val TEXT_X = MARGIN + THUMB_BOX_W + THUMB_TEXT_GAP // 456
    const val TEXT_RIGHT_MARGIN = 36f

    // Type sizes.
    const val TITLE_SIZE = 44f
    const val EPISODE_TITLE_SIZE = 26f
    const val CHIP_LABEL_SIZE = 30f
    const val TITLE_LINE_HEIGHT = 1.22f

    // Chips ([EP n] [SUB] [DUB]).
    const val CHIP_H = 54f
    const val CHIP_PAD_X = 26f
    const val CHIP_GAP = 12f
    const val CHIP_CORNER = 16f

    /** The flow layout's default thumbnail box (vertically centered). */
    const val THUMB_DEFAULT_X = MARGIN
    const val THUMB_DEFAULT_Y = (HEIGHT - THUMB_BOX_H) / 2f

    /** The default title color / episode-title color (the composer's D-499 palette). */
    const val TITLE_COLOR_DEFAULT = 0xFFFFFFFFL
    const val EPISODE_TITLE_COLOR_DEFAULT = 0xE1FFFFFFL // alpha 225 white
    const val EP_CHIP_LABEL_DEFAULT = 0xFF16141DL       // dark label on the lime EP chip
    const val AUDIO_CHIP_LABEL_DEFAULT = 0xFFB1F256L    // lime label on the dark audio chips
    const val LIME = 0xFFB1F256L
}
