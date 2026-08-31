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
}
