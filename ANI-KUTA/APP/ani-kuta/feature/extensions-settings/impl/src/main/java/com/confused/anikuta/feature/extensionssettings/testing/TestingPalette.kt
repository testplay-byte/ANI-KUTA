package com.confused.anikuta.feature.extensionssettings.testing

import androidx.compose.ui.graphics.Color

/**
 * THE TESTING PALETTE (round 87, D-594) — the single source of truth for
 * every color the extension-testing system uses to tell its two ECOSYSTEMS
 * and its seven TEST KINDS apart.
 *
 * WHY THIS FILE EXISTS (the round-87 device report): the ring's two system
 * colors were the theme's `primary` (user-selectable accent — lime by
 * default) and the Material3 BASELINE `tertiary` (pale pink #EFB8C8, never
 * themed anywhere in the app). Pink sits ~20° from the theme's error red,
 * so "CloudStream passed" and "anything failed" were near-twins, and any
 * warm accent preset collapsed the two systems into one blob — the user:
 * "the colors are way too close together. They look ugly."
 *
 * THE FIX: dedicated, FIXED hues that never follow the accent preset:
 *   • ANIYOMI  = emerald  (#34D399) — the app's primary system;
 *   • CLOUDSTREAM = sky   (#38BDF8)  — ~50° away, unmistakable next to it.
 * Both are vivid enough to read on the dark surfaces this feature paints,
 * and BOTH differ from error/warn so a failure never masquerades as a
 * system color. Within each VERDICT group the two systems keep their hue:
 *   passed = emerald + sky; failed = red + orange; untested = two grays.
 *
 * The per-kind palette gives each of the seven tests its own accent — the
 * time-proportional stage bar and the detail page's colored result blocks
 * all draw from [kindColor], so "the search block" is always the same pink
 * everywhere it appears.
 */
object TestingPalette {

    // ── The two ecosystems (FIXED — never the accent preset) ──────────────
    val SystemA = Color(0xFF34D399) // Aniyomi — emerald
    val SystemB = Color(0xFF38BDF8) // CloudStream — sky

    // ── Verdict groups: per-system shades (same hues in ring + cards) ─────
    val PassA = SystemA
    val PassB = SystemB
    val FailA = Color(0xFFF87171) // Aniyomi failed — red
    val FailB = Color(0xFFFB923C) // CloudStream failed — orange
    val NewA = Color(0xFFA9AFBA)  // Aniyomi untested — light gray
    val NewB = Color(0xFF7C828D)  // CloudStream untested — dark gray

    // ── The seven test kinds (the colored-block accents) ──────────────────
    private val KindColors = mapOf(
        ExtensionTestKind.PING to Color(0xFF7CC8FA),        // sky
        ExtensionTestKind.HOME_PAGE to Color(0xFFFFB300),   // amber
        ExtensionTestKind.SEARCH to Color(0xFFE57C9F),      // pink — the search block
        ExtensionTestKind.DETAILS to Color(0xFFFF8A65),     // coral
        ExtensionTestKind.EPISODE_LIST to Color(0xFF4DB6AC),// teal
        ExtensionTestKind.VIDEO_RESOLVE to Color(0xFFB1F256), // lime
        ExtensionTestKind.STREAM_PLAY to Color(0xFFCE93D8), // violet
    )

    /** One stable accent per test kind — the blocks, bubbles and stage bar. */
    fun kindColor(kind: ExtensionTestKind): Color =
        KindColors[kind] ?: Color(0xFFB1F256)
}
