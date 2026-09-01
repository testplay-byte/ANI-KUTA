package com.confused.anikuta.core.csplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 58 (round 18): locks the subtitle-overlay geometry math — the numbers
 * behind the v0.4.5 device-round fixes ("border size is not shown properly",
 * background/border geometry) and the byte-parity with the engine's Media3
 * mapping.
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
}
