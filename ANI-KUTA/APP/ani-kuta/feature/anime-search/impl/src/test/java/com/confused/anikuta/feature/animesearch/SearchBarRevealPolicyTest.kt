package com.confused.anikuta.feature.animesearch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-402 (round 28): unit tests for the top-bar reveal policy — the PURE
 * decision the search screen's NestedScrollConnection delegates to.
 *
 * These encode the round-28 device report's expected behavior verbatim:
 * "If I try to scroll down, then it apparently hides" (finger DOWN =
 * toward the top → the bar must REVEAL — the round-27 code had this
 * inverted); "If I scroll up with my finger from bottom to up, then it
 * shows" (finger UP = into the content → the bar must COLLAPSE); "If I try
 * to scroll to the very top again from the bottom, then it gets hidden
 * again" (at the very top → the bar is ALWAYS visible).
 *
 * Compose nested-scroll sign convention (proven from the pinned material3
 * 1.3.1 PullToRefresh source): dy > 0 = finger DOWN (content toward the
 * top / the P2R pull); dy < 0 = finger UP (scrolling into the content).
 */
class SearchBarRevealPolicyTest {

    private val t = 8f

    // ── The finger-DOWN family (dy > 0): reveal ──────────────────────────────

    @Test
    fun `finger down (toward the top) reveals the collapsed bar`() {
        // The user's report: scrolling down (finger down) must bring the
        // options back — the round-27 code HID the bar here (inverted).
        val next = searchBarNextCollapsed(current = true, dy = +12f, atTop = false, threshold = t)
        assertFalse(next)
    }

    @Test
    fun `finger down from the middle of the list reveals`() {
        val next = searchBarNextCollapsed(current = true, dy = +40f, atTop = false, threshold = t)
        assertFalse(next)
    }

    @Test
    fun `finger down keeps an already-visible bar visible`() {
        val next = searchBarNextCollapsed(current = false, dy = +12f, atTop = false, threshold = t)
        assertFalse(next)
    }

    // ── The finger-UP family (dy < 0): collapse ──────────────────────────────

    @Test
    fun `finger up (into the content) collapses the visible bar`() {
        // The user's report: "If I scroll up with my finger from bottom to up,
        // then it shows" — that was the INVERTED round-27 behavior; the
        // standard semantic is: scrolling into the content HIDES the bar.
        val next = searchBarNextCollapsed(current = false, dy = -12f, atTop = false, threshold = t)
        assertTrue(next)
    }

    @Test
    fun `finger up keeps an already-collapsed bar collapsed`() {
        val next = searchBarNextCollapsed(current = true, dy = -40f, atTop = false, threshold = t)
        assertTrue(next)
    }

    // ── The at-top guarantee ─────────────────────────────────────────────────

    @Test
    fun `at the very top the bar is ALWAYS visible`() {
        // The user's report: "If I try to scroll to the very top again from
        // the bottom, then it gets hidden again" — at the top the bar must
        // show no matter what the current state or delta says.
        assertFalse(searchBarNextCollapsed(current = true, dy = -50f, atTop = true, threshold = t))
        assertFalse(searchBarNextCollapsed(current = true, dy = 0f, atTop = true, threshold = t))
        assertFalse(searchBarNextCollapsed(current = false, dy = -50f, atTop = true, threshold = t))
    }

    @Test
    fun `at top even a finger-up delta cannot hide the bar`() {
        // Edge: a small finger-up movement at the top (the list hasn't left
        // offset 0 yet) must not collapse the bar.
        val next = searchBarNextCollapsed(current = false, dy = -12f, atTop = true, threshold = t)
        assertFalse(next)
    }

    // ── The dead zone ────────────────────────────────────────────────────────

    @Test
    fun `sub-threshold deltas keep the current state`() {
        // Horizontal rows' sideways scrolls produce dy ≈ 0 — never trip.
        assertTrue(searchBarNextCollapsed(current = true, dy = 0f, atTop = false, threshold = t))
        assertFalse(searchBarNextCollapsed(current = false, dy = +4f, atTop = false, threshold = t))
        assertTrue(searchBarNextCollapsed(current = true, dy = -4f, atTop = false, threshold = t))
    }

    @Test
    fun `exactly at the threshold is still the dead zone`() {
        assertTrue(searchBarNextCollapsed(current = true, dy = +t, atTop = false, threshold = t))
        assertFalse(searchBarNextCollapsed(current = false, dy = -t, atTop = false, threshold = t))
    }

    // ── The P2R pull family ──────────────────────────────────────────────────

    @Test
    fun `a pull-to-refresh drag (finger down at top) keeps the bar visible`() {
        // P2R grows its indicator on POSITIVE dy at the top; the bar stays.
        val next = searchBarNextCollapsed(current = false, dy = +25f, atTop = true, threshold = t)
        assertFalse(next)
    }
}
