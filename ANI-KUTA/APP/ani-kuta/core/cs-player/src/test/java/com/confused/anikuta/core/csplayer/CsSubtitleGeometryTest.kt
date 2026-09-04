package com.confused.anikuta.core.csplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 58 (round 18): locks the subtitle-overlay geometry math — the numbers
 * behind the v0.4.5 device-round fixes ("border size is not shown properly",
 * background/border geometry) and the byte-parity with the engine's Media3
 * mapping.
 *
 * Task 60 (round 20): locks the LINE-HEIGHT contract — the v0.4.7 overlay
 * passed only `fontSize`, so Material3's ambient bodyLarge leaked a FIXED
 * 24sp lineHeight in ("way too much gap" at 0.5× scale, "overlapping" at
 * 2×+). The overlay now passes an explicit `lineHeight = fontSize ×
 * LINE_HEIGHT_RATIO` — the ratio locks below pin the gap as a constant
 * fraction of the font at every size and scale.
 */
class CsSubtitleGeometryTest {

    // ── Border: MPV unit parity, LINEAR, no early saturation ──────────────

    @Test
    fun `border default 3 is MPV's 3 of 55 fraction`() {
        // 3/55 ≈ 0.0545 — MPV's own outline at the default setting (the v0.4.5
        // overlay rendered 0.035*3 = 10.5% ≈ 1.9× MPV here).
        assertEquals(3f / 55f, CsSubtitleGeometry.borderWidthFraction(3), 1e-6f)
    }

    @Test
    fun `border is linear across the sheet range`() {
        val f1 = CsSubtitleGeometry.borderWidthFraction(1)
        val f5 = CsSubtitleGeometry.borderWidthFraction(5)
        val f10 = CsSubtitleGeometry.borderWidthFraction(10)
        assertEquals(5f * f1, f5, 1e-6f)
        assertEquals(10f * f1, f10, 1e-6f)
        // v0.4.5's clamp saturated everything >= 5 at 0.15 — 10 must exceed it.
        assertEquals(10f / 55f, f10, 1e-6f)
    }

    @Test
    fun `border clamps negatives and absurd values`() {
        assertEquals(0f, CsSubtitleGeometry.borderWidthFraction(0), 1e-6f)
        assertEquals(0f, CsSubtitleGeometry.borderWidthFraction(-3), 1e-6f)
        assertEquals(CsSubtitleGeometry.MAX_BORDER_FRACTION, CsSubtitleGeometry.borderWidthFraction(50), 1e-6f)
    }

    // ── Shadow: same units as the border (drawn IN ADDITION) ──────────────

    @Test
    fun `shadow follows the border's MPV units`() {
        assertEquals(3f / 55f, CsSubtitleGeometry.shadowFraction(3), 1e-6f)
        assertEquals(0f, CsSubtitleGeometry.shadowFraction(0), 1e-6f)
        assertEquals(CsSubtitleGeometry.MAX_SHADOW_FRACTION, CsSubtitleGeometry.shadowFraction(99), 1e-6f)
    }

    // ── Font fraction: Media3 0.0533 parity + scale ───────────────────────

    @Test
    fun `font fraction at defaults equals Media3's 0_0533`() {
        assertEquals(0.0533f, CsSubtitleGeometry.fontFraction(55, 1f), 1e-6f)
    }

    @Test
    fun `font fraction is linear in fontSize and fontScale`() {
        assertEquals(0.0533f * 2f, CsSubtitleGeometry.fontFraction(110, 1f), 1e-6f)
        assertEquals(0.0533f * 1.5f, CsSubtitleGeometry.fontFraction(55, 1.5f), 1e-6f)
        assertEquals(0.0533f * 4f, CsSubtitleGeometry.fontFraction(55, 4f), 1e-6f)
    }

    @Test
    fun `font fraction clamps scale and guards zero font size`() {
        assertEquals(0.0533f * 0.25f, CsSubtitleGeometry.fontFraction(55, 0.05f), 1e-6f)
        assertEquals(0.0533f * 4f, CsSubtitleGeometry.fontFraction(55, 40f), 1e-6f)
        // fontSize 0 must not divide by zero — coerced to 1.
        assertEquals(0.0533f * (1f / 55f), CsSubtitleGeometry.fontFraction(0, 1f), 1e-6f)
    }

    // ── Position: MPV sub-pos → bottom padding (engine byte parity) ──────

    @Test
    fun `position 100 is flush bottom and 0 hits the 0_12 cap`() {
        assertEquals(0f, CsSubtitleGeometry.bottomPaddingFraction(100), 1e-6f)
        assertEquals(0.12f, CsSubtitleGeometry.bottomPaddingFraction(0), 1e-6f)
    }

    @Test
    fun `position maps linearly and clamps out-of-range`() {
        assertEquals(0.06f, CsSubtitleGeometry.bottomPaddingFraction(50), 1e-6f)
        assertEquals(0f, CsSubtitleGeometry.bottomPaddingFraction(150), 1e-6f)
        assertEquals(0.12f, CsSubtitleGeometry.bottomPaddingFraction(-20), 1e-6f)
    }

    // ── Task 59: the horizontal wrap inset ────────────────────────────────

    @Test
    fun `horizontal inset is 4 percent of the overlay width`() {
        assertEquals(43, CsSubtitleGeometry.horizontalInsetPx(1080))
        assertEquals(172, CsSubtitleGeometry.horizontalInsetPx(4300))
        assertEquals(0, CsSubtitleGeometry.horizontalInsetPx(0))
        assertEquals(0, CsSubtitleGeometry.horizontalInsetPx(-5))
    }

    // ── Task 60: the line-height contract (the v0.4.7 line-gap fix) ───────

    @Test
    fun `line height is a constant ratio of the font size`() {
        // The ratio is locked at 1.2 — the inter-line gap is ~20% of the
        // glyph height, above Roboto's natural ~1.17-1.18 line box so lines
        // never touch, tight enough to read as one block.
        assertEquals(1.2f, CsSubtitleGeometry.LINE_HEIGHT_RATIO, 1e-6f)
        assertEquals(12f, CsSubtitleGeometry.lineHeightSp(10f), 1e-6f)
        assertEquals(24f, CsSubtitleGeometry.lineHeightSp(20f), 1e-6f)
        // 1e-4 tolerance: 43f * 1.2f is 51.600002 in IEEE-754 single precision.
        assertEquals(51.6f, CsSubtitleGeometry.lineHeightSp(43f), 1e-4f)
    }

    @Test
    fun `line height scales PROPORTIONALLY with the font at every device scale`() {
        // The three v0.4.7 failure settings, simulated at 16:9-box effective
        // fonts: the line height must track the font LINEARLY — never a fixed
        // sp value (the leaked 24sp) and never sub-font.
        val small = 11f   // font 100, scale 0.5x
        val double = 21f  // font 100, scale 2x
        val maxScale = 43f // font 100, scale ~4x
        assertEquals(
            CsSubtitleGeometry.lineHeightSp(small) / small,
            CsSubtitleGeometry.lineHeightSp(double) / double,
            1e-6f,
        )
        assertEquals(
            CsSubtitleGeometry.lineHeightSp(small) / small,
            CsSubtitleGeometry.lineHeightSp(maxScale) / maxScale,
            1e-6f,
        )
        // The gap (lineHeight - font) is 20% of the font everywhere.
        assertEquals(0.2f * small, CsSubtitleGeometry.lineHeightSp(small) - small, 1e-4f)
        assertEquals(0.2f * maxScale, CsSubtitleGeometry.lineHeightSp(maxScale) - maxScale, 1e-4f)
    }

    @Test
    fun `line height never drops below a minimum and tolerates degenerate input`() {
        assertEquals(1f, CsSubtitleGeometry.lineHeightSp(0f), 1e-6f)
        assertEquals(1f, CsSubtitleGeometry.lineHeightSp(-5f), 1e-6f)
        assertEquals(1.2f, CsSubtitleGeometry.lineHeightSp(1f), 1e-6f)
    }
}
