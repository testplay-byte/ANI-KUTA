package com.confused.anikuta.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 48.1 (device round 8): locks for the canonical header-csv parser and
 * the mpv escaping boundary. The round-8 428 playback failure was caused by
 * exactly the case [parses comma continuation fragments into the value] pins.
 */
class MpvHeaderFieldsTest {

    private val chromeUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

    // ── parse (gluing) ────────────────────────────────────────────────────────

    @Test
    fun `parses comma continuation fragments into the value`() {
        val csv = "Referer: https://api3.aoneroom.com,User-Agent: $chromeUa"
        val parsed = MpvHeaderFields.parse(csv)
        assertEquals(2, parsed.size)
        assertEquals("Referer", parsed[0].first)
        assertEquals("https://api3.aoneroom.com", parsed[0].second)
        assertEquals("User-Agent", parsed[1].first)
        // THE round-8 bug: the naive split truncated this at "(KHTML".
        assertEquals(chromeUa, parsed[1].second)
    }

    @Test
    fun `parses null and blank to empty`() {
        assertTrue(MpvHeaderFields.parse(null).isEmpty())
        assertTrue(MpvHeaderFields.parse("").isEmpty())
        assertTrue(MpvHeaderFields.parse("   ").isEmpty())
    }

    @Test
    fun `skips entries without a value or colon`() {
        // No colon at all / empty value → the whole entry drops.
        assertTrue(MpvHeaderFields.parse("garbage").isEmpty())
        assertTrue(MpvHeaderFields.parse("X-Empty: ").isEmpty())
    }

    @Test
    fun `glues colonless fragments onto the previous value`() {
        // DESIGN TRADEOFF (same as DownloadHeaderParser): a comma-chunk that
        // doesn't start a new header-name is a CONTINUATION of the previous
        // value — that's exactly what fixes comma-bearing User-Agents.
        val csv = "Referer: https://example.com,garbage-without-colon,:empty-name"
        val parsed = MpvHeaderFields.parse(csv)
        assertEquals(1, parsed.size)
        assertEquals("Referer", parsed[0].first)
        assertEquals("https://example.com,garbage-without-colon,:empty-name", parsed[0].second)
    }

    @Test
    fun `glues multiple comma fragments in one value`() {
        val csv = "Cookie: a=1, b=2, c=3"
        val parsed = MpvHeaderFields.parse(csv)
        assertEquals(1, parsed.size)
        assertEquals("a=1, b=2, c=3", parsed[0].second)
    }

    @Test
    fun `treats a colon-bearing chunk after a comma as a new header`() {
        // A chunk that STARTS with a header-name pattern begins a new entry —
        // the same tradeoff DownloadHeaderParser makes.
        val csv = "Referer: https://example.com,Accept: text/html"
        val parsed = MpvHeaderFields.parse(csv)
        assertEquals(2, parsed.size)
        assertEquals("Accept" to "text/html", parsed[1])
    }

    // ── escapeForMpv (the mpv list-option boundary) ──────────────────────────

    @Test
    fun `escapes commas in values with backslashes`() {
        val csv = "Referer: https://api3.aoneroom.com,User-Agent: $chromeUa"
        val escaped = MpvHeaderFields.escapeForMpv(csv)
        // mpv's get_nextsep splits on ',' unless preceded by '\': every comma
        // inside the UA must be escaped, the structural ones must not be.
        // Split on UNESCAPED commas only (what mpv will see as separators).
        val entries = escaped.split("(?<!\\\\),".toRegex()).filter { it.isNotEmpty() }
        assertEquals(2, entries.size)
        assertEquals("Referer: https://api3.aoneroom.com", entries[0])
        assertEquals("User-Agent: $chromeUa".replace(",", "\\,"), entries[1])
        assertTrue(entries[1].contains("(KHTML\\, like Gecko)"))
    }

    @Test
    fun `escapes literal backslashes before commas`() {
        // A value ending in a backslash would otherwise escape the structural
        // separator — it must be doubled first.
        val csv = "X-Key: trailing\\"
        val escaped = MpvHeaderFields.escapeForMpv(csv)
        assertEquals("X-Key: trailing\\\\", escaped)
    }

    @Test
    fun `empty input escapes to empty string`() {
        assertEquals("", MpvHeaderFields.escapeForMpv(null))
        assertEquals("", MpvHeaderFields.escapeForMpv(""))
    }

    @Test
    fun `round trips simple two header csv`() {
        val csv = "Referer: https://a.example,User-Agent: PlainAgent/1.0"
        assertEquals(csv, MpvHeaderFields.escapeForMpv(csv))
        // And parsing the escaped form (no commas in values) is identity.
        assertEquals(
            MpvHeaderFields.parse(csv),
            MpvHeaderFields.parse(MpvHeaderFields.escapeForMpv(csv)),
        )
    }

    @Test
    fun `escaped output parses back to the same entries when unescaped`() {
        // Simulate mpv's un-escaping (drop '\' before ',' and collapse '\\')
        // and verify the entry set is exactly the parsed input.
        val csv = "Referer: https://api3.aoneroom.com,User-Agent: $chromeUa"
        val escaped = MpvHeaderFields.escapeForMpv(csv)
        val mpvUnescaped = escaped
            .split("(?<!\\\\),".toRegex()) // split on unescaped commas only
            .map { it.replace("\\\\", "\u0000").replace("\\,", ",").replace("\u0000", "\\") }
        val parsed = MpvHeaderFields.parse(csv)
        assertEquals(parsed.size, mpvUnescaped.size)
        parsed.zip(mpvUnescaped).forEach { (pair, entry) ->
            assertEquals("${pair.first}: ${pair.second}", entry)
        }
    }
}
