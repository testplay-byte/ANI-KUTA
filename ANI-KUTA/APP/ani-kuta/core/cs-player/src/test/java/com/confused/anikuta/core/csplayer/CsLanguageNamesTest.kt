package com.confused.anikuta.core.csplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 55 (round 15) — pure-JVM locks for the subtitle display-name + audio
 * tag helpers. The v0.4.2 device round showed raw URLs in the subtitle rows
 * and no SUB/DUB grouping; these tests pin the fixes.
 */
class CsLanguageNamesTest {

    // ── display() — the "never show a URL" contract ─────────────────────────

    @Test
    fun `language tag maps to locale display name`() {
        assertEquals("English", CsLanguageNames.display("en"))
        assertEquals("Japanese", CsLanguageNames.display("ja"))
    }

    @Test
    fun `three-letter iso code maps to display name`() {
        // "eng" resolves through the tag parser's leniency or falls back as-is.
        val name = CsLanguageNames.display("eng")
        assertTrue(name.isNotBlank() && !name.contains("/"))
    }

    @Test
    fun `url-shaped lang becomes the file name`() {
        assertEquals(
            "ep1_en",
            CsLanguageNames.display("https://cdn.example.com/subs/ep1_en.vtt"),
        )
        assertEquals("subs", CsLanguageNames.display("//host.com/subs.srt"))
    }

    @Test
    fun `blank becomes subtitle`() {
        assertEquals("Subtitle", CsLanguageNames.display(null))
        assertEquals("Subtitle", CsLanguageNames.display(""))
        assertEquals("Subtitle", CsLanguageNames.display("   "))
    }

    @Test
    fun `real names pass through`() {
        assertEquals("English", CsLanguageNames.display("English"))
        assertEquals("English (SRT)", CsLanguageNames.display("English (SRT)"))
    }

    // ── matchesPreferred() — the auto-select gate ───────────────────────────

    @Test
    fun `en matches english in any notation`() {
        assertTrue(CsLanguageNames.matchesPreferred("en", "en,eng,english"))
        assertTrue(CsLanguageNames.matchesPreferred("eng", "en,eng,english"))
        assertTrue(CsLanguageNames.matchesPreferred("English", "en,eng,english"))
        assertTrue(CsLanguageNames.matchesPreferred("en-US", "en,eng,english"))
    }

    @Test
    fun `other languages do not match`() {
        assertFalse(CsLanguageNames.matchesPreferred("ja", "en,eng,english"))
        assertFalse(CsLanguageNames.matchesPreferred("Spanish", "en,eng,english"))
    }

    @Test
    fun `empty inputs never match`() {
        assertFalse(CsLanguageNames.matchesPreferred(null, "en"))
        assertFalse(CsLanguageNames.matchesPreferred("en", ""))
    }
}

/** Task 55 — the aniyomi parseAudioVersion port (SUB/DUB grouping source). */
class CsAudioTagTest {

    @Test
    fun `sub patterns`() {
        assertEquals("SUB", CsAudioTag.parse("SUB - 1080p"))
        assertEquals("SUB", CsAudioTag.parse("HD-1 - Sub - 1080p"))
        assertEquals("SUB", CsAudioTag.parse("subbed"))
    }

    @Test
    fun `dub patterns`() {
        assertEquals("DUB", CsAudioTag.parse("Vidstream-2 - Dub - 720p"))
        assertEquals("DUB", CsAudioTag.parse("dubbed"))
    }

    @Test
    fun `hsub wins over sub`() {
        assertEquals("HSUB", CsAudioTag.parse("HSUB - 360p"))
        assertEquals("HSUB", CsAudioTag.parse("Hardsub 720p"))
    }

    @Test
    fun `plain server names are default`() {
        assertEquals("Default", CsAudioTag.parse("Mirror 1080p"))
        assertEquals("Default", CsAudioTag.parse(""))
        assertEquals("Default", CsAudioTag.parse(null))
    }

    @Test
    fun `isAudio gate`() {
        assertTrue(CsAudioTag.isAudio("SUB"))
        assertFalse(CsAudioTag.isAudio("Default"))
        assertFalse(CsAudioTag.isAudio(null))
    }

    // ── Task 57 (round 17) — expanded vocabulary + decoration pass ──────────

    @Test
    fun `round 17 sub vocabulary`() {
        assertEquals("SUB", CsAudioTag.parse("Mirror [SUB] 1080p"))
        assertEquals("SUB", CsAudioTag.parse("English Subtitles 1080"))
        assertEquals("SUB", CsAudioTag.parse("Softsub 480p"))
        assertEquals("SUB", CsAudioTag.parse("HD-2 - Subs - 720p"))
        assertEquals("SUB", CsAudioTag.parse("Soft-sub 540p"))
        assertEquals("SUB", CsAudioTag.parse("Eng Sub 480p"))
        assertEquals("SUB", CsAudioTag.parse("English Sub 1080p"))
    }

    @Test
    fun `round 17 dub vocabulary`() {
        assertEquals("DUB", CsAudioTag.parse("Streamtape (Dub)"))
        assertEquals("DUB", CsAudioTag.parse("Vidstream-3 - Dubs - 1080p"))
    }

    @Test
    fun `round 17 hsub vocabulary keeps priority`() {
        assertEquals("HSUB", CsAudioTag.parse("HSUB - 360p"))
        assertEquals("HSUB", CsAudioTag.parse("Hard-sub 480p"))
        assertEquals("HSUB", CsAudioTag.parse("Hard sub 720p"))
        assertEquals("HSUB", CsAudioTag.parse("H-Hardsub 1080p"))
        assertEquals("HSUB", CsAudioTag.parse("HSub 480p"))
    }

    @Test
    fun `underscore decorations hit the second signal`() {
        assertEquals("SUB", CsAudioTag.parse("SomeName_Sub"))
        assertEquals("DUB", CsAudioTag.parse("Name_Dub"))
        assertEquals("HSUB", CsAudioTag.parse("Mirror_HSub"))
        assertEquals("DUB", CsAudioTag.parse("Source_Dubbed"))
    }

    @Test
    fun `bracketed standalone tokens map by token`() {
        assertEquals("SUB", CsAudioTag.parse("Mirror [ Sub ] 1080p"))
        assertEquals("DUB", CsAudioTag.parse("[Dubbed] 720p"))
    }

    @Test
    fun `round 17 regressions stay default`() {
        assertEquals("Default", CsAudioTag.parse("Mirror 1080p"))
        assertEquals("Default", CsAudioTag.parse("Vidstream 720p"))
        assertEquals("Default", CsAudioTag.parse(null))
        assertEquals("Default", CsAudioTag.parse("   "))
    }
}
