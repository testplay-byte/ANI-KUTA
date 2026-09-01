package com.confused.anikuta.core.csplayer

/**
 * Task 58 (round 18) / **Task 59 (round 19 — the accuracy round 2)** — the PURE
 * subtitle-overlay geometry math, extracted from CsSubtitleOverlay/CsPlayerEngine
 * so it is unit-testable (the overlay is Compose; CI compiles it, but the
 * numbers are the contract).
 *
 * All fractions are of the OVERLAY HEIGHT (the player box the overlay fills —
 * the 16:9 minimized box or the fullscreen surface), matching Media3's
 * [androidx.media3.ui.SubtitleView] semantics byte-for-byte.
 *
 * MPV unit parity (the v0.4.5 device-round fixes):
 *  - MPV's `sub-font-size`, `sub-border-size` and `sub-shadow-offset` all use
 *    the SAME scaled units (defaults: 55 / 3 / 0). The v0.4.5 overlay used
 *    `0.035f × borderSize` for the border (≈10.5% of the font height at the
 *    default 3 — ≈1.9× MPV's 3/55 ≈ 5.5%) AND clamped the fraction at 0.15,
 *    so every border setting ≥ 5 rendered identically (saturated) — the
 *    device round's "border size is not shown properly". The math is now
 *    LINEAR in MPV units: `borderSize / 55` of the font height.
 *  - `sub-shadow-offset` follows the same units (the old 0.03f-per-unit was
 *    close but is unified now), and MPV draws the shadow IN ADDITION to the
 *    border (the overlay used to suppress it — fixed at the call site).
 *  - `sub-pos` (0..100, 100 = flush bottom) → the ((100 - pos)/100) × 0.12
 *    bottom-padding fraction (unchanged — byte parity with the engine's
 *    Media3 mapping).
 *
 * Task 59 (the v0.4.6 device round — "way too much spacing between the lines"
 * / "lines overlapping" / "the border was showing somewhere else from the
 * font"): the v0.4.6 overlay rendered each cue LINE as a separate `Text` in a
 * `Column`, so every line carried its own full platform line box (ascent +
 * descent + leading) — the inter-line gap was DOUBLE-ledged and uncontrolled,
 * and the stroke passes poked into the next line's box unopposed at large
 * fonts. The round-19 overlay renders the WHOLE cue as ONE multi-line `Text`
 * (the platform's natural, Media3-SubtitleView-parity line spacing — one
 * leading, not two) and draws every decoration pass (background boxes, shadow
 * stroke, border stroke) from the SAME [androidx.compose.ui.text.TextLayoutResult]
 * the fill uses — the passes are structurally incapable of detaching. The one
 * new geometry constant was the horizontal inset.
 *
 * **Task 60 (round 20 — the v0.4.7 device round, "the gap between the two
 * lines is very bad… way too much / way too minimal / overlapping"):** the
 * round-19 `Text` set `fontSize` but NOT `lineHeight`, so the AMBIENT
 * `LocalTextStyle` (Material3 provides `bodyLarge` — lineHeight **24sp, a
 * FIXED value**) leaked into the overlay: at small effective fonts (16:9 box,
 * scale 0.5×, ≈11sp) the fixed 24sp line box read as a huge gap; at large ones
 * (scale 2×/4×, 21–43sp) the line box was SMALLER than the glyphs → overlap
 * and distortion. The fix is structural: the overlay now passes an EXPLICIT
 * `lineHeight = fontSizeSp × [LINE_HEIGHT_RATIO]` — the gap is a CONSTANT
 * fraction of the effective font at every size and scale, and no ambient
 * style can influence the overlay's line metric again.
 */
object CsSubtitleGeometry {

    /** Media3 SubtitleView's default fraction-of-height for cue text. */
    const val FONT_FRACTION = 0.0533f

    /** MPV sub-font-size default (the unit base for border/shadow too). */
    const val DEFAULT_FONT_SIZE = 55f

    /**
     * Task 60: the cue's line-height ratio — `lineHeight = fontSize × this`.
     * 1.2 keeps a clean, constant inter-line gap (~20% of the glyph height)
     * that is slightly ABOVE Roboto's natural line box (≈1.18em, ascent +
     * descent), so adjacent lines never touch at any size while the gap stays
     * tight enough to read as one subtitle block — identical in both display
     * modes and at every font/scale combination.
     */
    const val LINE_HEIGHT_RATIO = 1.2f

    /** The engine's sub-pos → bottom-padding cap (Media3 parity). */
    const val MAX_BOTTOM_FRACTION = 0.12f

    /** Sanity clamp for extreme border settings (10 → 18%, stays readable). */
    const val MAX_BORDER_FRACTION = 0.30f

    /** Sanity clamp for extreme shadow settings. */
    const val MAX_SHADOW_FRACTION = 0.30f

    /**
     * Task 59: the horizontal inset fraction of the OVERLAY WIDTH — the cue
     * block wraps at this margin from each side so long lines never touch the
     * screen edge (MPV wraps its sub layout with a margin too). 4% per side.
     */
    const val HORIZONTAL_INSET_FRACTION = 0.04f

    /**
     * The font-size fraction of the overlay height: Media3's 0.0533 scaled by
     * (fontSize / 55) and the user's scale — LINEAR in both.
     */
    fun fontFraction(fontSize: Int, fontScale: Float): Float =
        FONT_FRACTION * (fontSize.coerceAtLeast(1) / DEFAULT_FONT_SIZE) *
            fontScale.coerceIn(0.25f, 4f)

    /**
     * Task 60: the cue's line height (sp) for a given effective font size —
     * `fontSize × [LINE_HEIGHT_RATIO]` (the overlay passes this EXPLICITLY so
     * the ambient LocalTextStyle's fixed lineHeight can never leak in — the
     * v0.4.7 "way too much / overlapping" line-gap bug).
     */
    fun lineHeightSp(fontSizeSp: Float): Float =
        (fontSizeSp * LINE_HEIGHT_RATIO).coerceAtLeast(1f)

    /**
     * The border width as a fraction of the FONT height — MPV units: the
     * border uses the same scaled units as the font size, so `borderSize` 3
     * (the default) → 3/55 ≈ 5.45%. Linear across the sheet's 0..10 range;
     * clamped only at a generous 0.30 sanity ceiling.
     */
    fun borderWidthFraction(borderSize: Int): Float =
        (borderSize.coerceAtLeast(0) / DEFAULT_FONT_SIZE).coerceIn(0f, MAX_BORDER_FRACTION)

    /**
     * The shadow offset as a fraction of the FONT height — the same MPV
     * units as the border (drawn downward, in ADDITION to the border).
     */
    fun shadowFraction(shadowOffset: Int): Float =
        (shadowOffset.coerceAtLeast(0) / DEFAULT_FONT_SIZE).coerceIn(0f, MAX_SHADOW_FRACTION)

    /**
     * MPV sub-pos (0..100, 100 = flush bottom) → the bottom-padding fraction
     * of the overlay height (byte parity with the engine's Media3 mapping).
     */
    fun bottomPaddingFraction(position: Int): Float =
        ((100 - position.coerceIn(0, 100)) / 100f) * MAX_BOTTOM_FRACTION

    /**
     * Task 59: the horizontal inset (px) for a given overlay width — the cue
     * block's wrap margin (see [HORIZONTAL_INSET_FRACTION]).
     */
    fun horizontalInsetPx(overlayWidthPx: Int): Int =
        (overlayWidthPx.coerceAtLeast(0) * HORIZONTAL_INSET_FRACTION).toInt()
}
