package com.confused.anikuta.core.csplayer

/**
 * Task 58 (round 18) — the PURE subtitle-overlay geometry math, extracted from
 * CsSubtitleOverlay/CsPlayerEngine so it is unit-testable (the overlay is
 * Compose; CI compiles it, but the numbers are the contract).
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
 */
object CsSubtitleGeometry {

    /** Media3 SubtitleView's default fraction-of-height for cue text. */
    const val FONT_FRACTION = 0.0533f

    /** MPV sub-font-size default (the unit base for border/shadow too). */
    const val DEFAULT_FONT_SIZE = 55f

    /** The engine's sub-pos → bottom-padding cap (Media3 parity). */
    const val MAX_BOTTOM_FRACTION = 0.12f

    /** Sanity clamp for extreme border settings (10 → 18%, stays readable). */
    const val MAX_BORDER_FRACTION = 0.30f

    /** Sanity clamp for extreme shadow settings. */
    const val MAX_SHADOW_FRACTION = 0.30f

    /**
     * The font-size fraction of the overlay height: Media3's 0.0533 scaled by
     * (fontSize / 55) and the user's scale — LINEAR in both.
     */
    fun fontFraction(fontSize: Int, fontScale: Float): Float =
        FONT_FRACTION * (fontSize.coerceAtLeast(1) / DEFAULT_FONT_SIZE) *
            fontScale.coerceIn(0.25f, 4f)

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
}
