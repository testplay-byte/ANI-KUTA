package com.confused.anikuta.feature.cswatch.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 55 (round 15) — pure-JVM locks for the sub/dub sibling pairing.
 *
 * The bridge appends " (Sub)" / " (Dub)" to bridged CloudStream episode
 * names (each flavor has its own loadLinks data handle). These tests pin the
 * pairing rules behind the COMBINED/SEPARATE display modes.
 *
 * Task 56 (round 16): pairing now runs on per-flavor ORDINALS — the details
 * pipeline guarantees globally-unique numbers (the normalizer renumbers
 * duplicates 1..N, sub first), so dub rows carry CONTINUING numbers
 * (13–24 for a 12+12 show). The ordinal tests pin the new pairing key.
 */
class CsSubDubSiblingsTest {

    private fun eps(vararg rows: Triple<String, Float, String>) =
        rows.map { CsSimpleEpisode(data = it.first, episodeNumber = it.second, name = it.third) }

    // ── tagOf ───────────────────────────────────────────────────────────────

    @Test
    fun `tag detection from name suffix`() {
        assertEquals("SUB", CsSubDubSiblings.tagOf("Season 1 - Episode 5 - Title (Sub)"))
        assertEquals("DUB", CsSubDubSiblings.tagOf("Episode 5 (Dub)"))
        assertEquals(null, CsSubDubSiblings.tagOf("Episode 5"))
        assertEquals(null, CsSubDubSiblings.tagOf(""))
    }

    @Test
    fun `sub in a title word is not a tag`() {
        // "Subaru" must NOT read as a flavor — only the exact trailing tag counts.
        assertEquals(null, CsSubDubSiblings.tagOf("Episode 5 - Subaru"))
    }

    // ── stripTag ────────────────────────────────────────────────────────────

    @Test
    fun `strip removes the trailing tag`() {
        assertEquals("Season 1 - Episode 5", CsSubDubSiblings.stripTag("Season 1 - Episode 5 (Sub)"))
        assertEquals("Episode 5", CsSubDubSiblings.stripTag("Episode 5 (Dub)"))
        assertEquals("Episode 5", CsSubDubSiblings.stripTag("Episode 5"))
    }

    // ── handlesFor ──────────────────────────────────────────────────────────

    @Test
    fun `separate mode resolves only the tapped handle tagged`() {
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("dub-1", 1f, "Episode 1 (Dub)"),
        )
        val handles = CsSubDubSiblings.handlesFor(list, "sub-1", combined = false)
        assertEquals(1, handles.size)
        assertEquals("sub-1", handles[0].data)
        assertEquals("SUB", handles[0].audioTag)
    }

    @Test
    fun `combined mode resolves both siblings tagged`() {
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("dub-1", 1f, "Episode 1 (Dub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
        )
        val handles = CsSubDubSiblings.handlesFor(list, "sub-1", combined = true)
        assertEquals(2, handles.size)
        assertEquals("sub-1" to "SUB", handles[0].data to handles[0].audioTag)
        assertEquals("dub-1" to "DUB", handles[1].data to handles[1].audioTag)
    }

    @Test
    fun `neutral row resolves alone even in combined mode`() {
        val list = eps(
            Triple("only", 1f, "Episode 1"),
        )
        val handles = CsSubDubSiblings.handlesFor(list, "only", combined = true)
        assertEquals(1, handles.size)
        assertEquals(null, handles[0].audioTag)
    }

    @Test
    fun `tagged row without counterpart resolves alone`() {
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
        )
        val handles = CsSubDubSiblings.handlesFor(list, "sub-1", combined = true)
        assertEquals(1, handles.size)
        assertEquals("SUB", handles[0].audioTag)
    }

    @Test
    fun `missing clicked row falls back to itself`() {
        val list = eps(Triple("a", 1f, "Episode 1"))
        val handles = CsSubDubSiblings.handlesFor(list, "unknown-data", combined = true)
        assertEquals(1, handles.size)
        assertEquals("unknown-data", handles[0].data)
    }

    // ── mergeSiblings (COMBINED display) ────────────────────────────────────

    @Test
    fun `siblings merge into one row with sub handle and stripped name`() {
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("dub-1", 1f, "Episode 1 (Dub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
            Triple("dub-2", 2f, "Episode 2 (Dub)"),
        )
        val merged = CsSubDubSiblings.mergeSiblings(list)
        assertEquals(2, merged.size)
        assertEquals("sub-1", merged[0].data)
        assertEquals("Episode 1", merged[0].name)
        assertEquals("sub-2", merged[1].data)
        assertEquals("Episode 2", merged[1].name)
    }

    @Test
    fun `untagged lists pass through unchanged`() {
        val list = eps(Triple("a", 1f, "Episode 1"), Triple("b", 2f, "Episode 2"))
        assertEquals(list, CsSubDubSiblings.mergeSiblings(list))
    }

    // ── hasBothFlavors ──────────────────────────────────────────────────────

    @Test
    fun `both flavors detection`() {
        assertTrue(
            CsSubDubSiblings.hasBothFlavors(
                eps(Triple("s", 1f, "E1 (Sub)"), Triple("d", 1f, "E1 (Dub)")),
            ),
        )
        assertFalse(
            CsSubDubSiblings.hasBothFlavors(
                eps(Triple("s1", 1f, "E1 (Sub)"), Triple("s2", 2f, "E2 (Sub)")),
            ),
        )
        assertFalse(CsSubDubSiblings.hasBothFlavors(eps(Triple("a", 1f, "E1"))))
    }

    // ── Task 56: flavor ordinals (the global-numbering reality) ─────────────

    @Test
    fun `ordinals renumber each flavor from one`() {
        // The pipeline's real shape: dub rows CONTINUE (13, 14) after sub 1..12.
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
            Triple("dub-1", 13f, "Episode 13 (Dub)"),
            Triple("dub-2", 14f, "Episode 14 (Dub)"),
        )
        val ordinals = CsSubDubSiblings.flavorOrdinals(list)
        assertEquals(1, ordinals["sub-1"])
        assertEquals(2, ordinals["sub-2"])
        assertEquals(1, ordinals["dub-1"])
        assertEquals(2, ordinals["dub-2"])
        // Untagged rows are absent — callers fall back to the raw number.
        val neutral = eps(Triple("only", 7f, "Episode 7"))
        assertTrue(CsSubDubSiblings.flavorOrdinals(neutral).isEmpty())
    }

    @Test
    fun `combined mode pairs siblings under continuing numbers`() {
        val list = eps(
            Triple("sub-5", 5f, "Episode 5 (Sub)"),
            Triple("dub-5", 17f, "Episode 17 (Dub)"), // dub continues — same real episode
            Triple("dub-6", 18f, "Episode 18 (Dub)"),
        )
        val handles = CsSubDubSiblings.handlesFor(list, "sub-5", combined = true)
        assertEquals(2, handles.size)
        assertEquals("sub-5" to "SUB", handles[0].data to handles[0].audioTag)
        assertEquals("dub-5" to "DUB", handles[1].data to handles[1].audioTag)
    }

    @Test
    fun `combined mode from the dub side pairs back to sub`() {
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
            Triple("dub-1", 13f, "Episode 13 (Dub)"),
            Triple("dub-2", 14f, "Episode 14 (Dub)"),
        )
        val handles = CsSubDubSiblings.handlesFor(list, "dub-2", combined = true)
        assertEquals(2, handles.size)
        assertEquals("dub-2" to "DUB", handles[0].data to handles[0].audioTag)
        assertEquals("sub-2" to "SUB", handles[1].data to handles[1].audioTag)
    }

    @Test
    fun `merge collapses sub+dub pairs under continuing numbers`() {
        // 12+12 shows must render 12 merged rows — not 24 (device feedback F4).
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
            Triple("dub-1", 13f, "Episode 13 (Dub)"),
            Triple("dub-2", 14f, "Episode 14 (Dub)"),
            Triple("dub-3", 15f, "Episode 15 (Dub)"), // no sub-3 — stays alone
        )
        val merged = CsSubDubSiblings.mergeSiblings(list)
        assertEquals(3, merged.size)
        assertEquals("sub-1", merged[0].data)
        assertEquals("Episode 1", merged[0].name)
        assertEquals("sub-2", merged[1].data)
        assertEquals("Episode 2", merged[1].name)
        // The unpaired dub row survives (tag intact — nothing merged into it).
        assertEquals("dub-3", merged[2].data)
    }

    @Test
    fun `unequal flavor sizes merge pairwise by ordinal`() {
        // Sub 1..3, dub only 1 (ordinal 1) — pairs collapse, the rest stay.
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
            Triple("sub-3", 3f, "Episode 3 (Sub)"),
            Triple("dub-1", 4f, "Episode 4 (Dub)"),
        )
        val merged = CsSubDubSiblings.mergeSiblings(list)
        assertEquals(3, merged.size)
        assertEquals("sub-1", merged[0].data)
        assertEquals("Episode 1", merged[0].name)
        assertEquals("sub-2", merged[1].data)
        assertEquals("sub-3", merged[2].data)
    }

    // ── Task 57 (round 17 — P2): merged rows carry their flavor pills ──────

    @Test
    fun `merged rows carry the pair's actual flavor tags`() {
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("dub-1", 1f, "Episode 1 (Dub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
            Triple("dub-2", 2f, "Episode 2 (Dub)"),
        )
        val merged = CsSubDubSiblings.mergeSiblings(list)
        assertEquals(2, merged.size)
        assertEquals(listOf("SUB", "DUB"), merged[0].flavors)
        assertEquals(listOf("SUB", "DUB"), merged[1].flavors)
        // Primary row's data + ordinal identity unchanged — flavors is
        // render-only, never an identity field.
        assertEquals("sub-1", merged[0].data)
        assertEquals(1f, merged[0].episodeNumber)
    }

    @Test
    fun `untagged pass-through rows keep flavors empty`() {
        val list = eps(Triple("a", 1f, "Episode 1"), Triple("b", 2f, "Episode 2"))
        val merged = CsSubDubSiblings.mergeSiblings(list)
        assertEquals(2, merged.size)
        assertEquals(emptyList<String>(), merged[0].flavors)
        assertEquals(emptyList<String>(), merged[1].flavors)
    }

    @Test
    fun `unpaired tagged row keeps flavors empty and tag intact`() {
        // A tagged row with no sibling is NOT merged — it passes through with
        // its tag (round-16 contract: only MERGED rows lose the name tag; the
        // display layer strips the rest) and gets NO flavor pills (nothing
        // merged, so there is no variant set to advertise).
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("dub-1", 1f, "Episode 1 (Dub)"),
            Triple("dub-3", 15f, "Episode 15 (Dub)"), // no sub-3 sibling
        )
        val merged = CsSubDubSiblings.mergeSiblings(list)
        assertEquals(2, merged.size)
        assertEquals(listOf("SUB", "DUB"), merged[0].flavors)
        assertEquals("Episode 15 (Dub)", merged[1].name)
        assertEquals(emptyList<String>(), merged[1].flavors)
    }

    // ── Task 57 (round 17 — P1): ordinals are the progress identity ─────────

    @Test
    fun `flavor ordinals are the shared progress identity`() {
        // P1's "one episode" contract: sub-5 and dub-5 are the SAME episode —
        // their flavor ordinals MATCH (5 == 5) even though the raw numbers
        // differ (5 vs 17, the normalizer's continuing numbers). The ordinal
        // is what the progress/rating keys embed.
        val list = eps(
            Triple("sub-1", 1f, "Episode 1 (Sub)"),
            Triple("sub-2", 2f, "Episode 2 (Sub)"),
            Triple("sub-3", 3f, "Episode 3 (Sub)"),
            Triple("sub-4", 4f, "Episode 4 (Sub)"),
            Triple("sub-5", 5f, "Episode 5 (Sub)"),
            Triple("dub-1", 13f, "Episode 13 (Dub)"),
            Triple("dub-2", 14f, "Episode 14 (Dub)"),
            Triple("dub-3", 15f, "Episode 15 (Dub)"),
            Triple("dub-4", 16f, "Episode 16 (Dub)"),
            Triple("dub-5", 17f, "Episode 17 (Dub)"),
        )
        val ordinals = CsSubDubSiblings.flavorOrdinals(list)
        assertEquals(5, ordinals["sub-5"])
        assertEquals(5, ordinals["dub-5"])
        assertEquals(ordinals["sub-5"], ordinals["dub-5"])
        // The raw numbers genuinely differ — the ordinal is the ONLY linker.
        assertEquals(5f, list[4].episodeNumber)
        assertEquals(17f, list[9].episodeNumber)
    }
}
