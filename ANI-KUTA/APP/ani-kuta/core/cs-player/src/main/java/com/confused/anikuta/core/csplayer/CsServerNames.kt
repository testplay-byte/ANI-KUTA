package com.confused.anikuta.core.csplayer

/**
 * D-551 — the SERVER part of a CloudStream link name, extracted from
 * :feature:cs-watch:impl's `serverNameOf` (it lived there since Task 55) into
 * :core:cs-player.
 *
 * WHY the move: the derivation is needed by THREE consumers that live in three
 * different modules —
 *   1. the resolve/links sheets' grouping (:feature:cs-watch:impl —
 *      Server → AudioVersion → Quality tiers),
 *   2. the download enqueue's sibling-audio-variant matcher (:app — D-550's
 *      `CsDownloadRequestBuilder.siblingAudioVariants`),
 *   3. the resolve debug report (:feature:cs-watch:impl).
 * The v1.1.24 device round exposed what a divergence costs: the sheets derived
 * the server one way while the sibling matcher compared RAW names — so
 * "MovieBox (Hindi Audio)" and "MovieBox (Original Audio)" rendered as TWO
 * servers (the user's complaint) AND never matched as siblings (only one
 * audio version downloaded; the D-550 offline audio switch never engaged).
 * One function, one vocabulary, all three consumers.
 *
 * The derivation (the aniyomi semantics + the Task 57 decoration pass):
 * audio-version tokens and quality tokens are stripped when they appear as
 * separate " - " segments ("HD-1 - Sub - 1080p" → "HD-1"), as BRACKETED
 * decorations glued to a segment ("Mirror [SUB] 1080p" → "Mirror",
 * "Server [1080p]" → "Server"), and — D-551 — as LANGUAGE-AUDIO decorations
 * ("MovieBox (Hindi Audio)" → "MovieBox", "Server - Original Audio" →
 * "Server"), using the EXACT regexes [CsAudioTag] parses labels with (single
 * source of truth). A name that is ONLY tokens ("HSUB - 360p") keeps its full
 * original form (blank guard); names without separators or brackets pass
 * through unchanged (no over-stripping of hyphenated names like "HD-1").
 *
 * Pure Kotlin, unit-testable (the locks live in CsSourceListUiTest through the
 * feature module's delegating `serverNameOf`, plus CsAudioTagTest).
 */
object CsServerNames {

    /**
     * The server part of [name] — the aniyomi derivation with the Task 57
     * decoration pass and the D-551 language-audio pass.
     */
    fun of(name: String): String {
        val kept = name.split(SEPARATOR).map { seg ->
            // Decoration pass: strip glued brackets (sub/dub family, quality,
            // language-audio), then bare quality words, then re-join the
            // surviving words with single spaces (collapses the runs of spaces
            // the bracket removal leaves behind).
            seg.trim()
                .replace(CsAudioTag.LANGUAGE_AUDIO_BRACKET, "")
                .replace(BRACKETED_AUDIO_TAG, "")
                .replace(BRACKETED_QUALITY_TAG, "")
                .split(WHITESPACE)
                .filter { word -> !QUALITY_TOKEN.matches(word) }
                .joinToString(" ")
        }.filter { seg ->
            val s = seg.lowercase()
            s.isNotBlank() &&
                s !in AUDIO_SEGMENT_WORDS &&
                // D-551: a whole segment that IS "<words> Audio" is the audio
                // VERSION, not part of the server name ("MovieBox - Hindi Audio").
                !CsAudioTag.LANGUAGE_AUDIO_SEGMENT.matches(seg.trim()) &&
                !QUALITY_TOKEN.matches(s)
        }
        return kept.joinToString(" - ").trim().ifBlank { name.trim() }
    }

    /** Segment separator: " - " with flexible spacing. */
    internal val SEPARATOR = Regex("\\s+-\\s+")

    /** Task 57 (P5): a bracketed audio-version token glued to a segment —
     *  "[SUB]", "(Dub)", "[Dubbed]", "[Multi Audio]", "(Softsub)". */
    private val BRACKETED_AUDIO_TAG =
        Regex("[\\[(]\\s*(?:sub(?:bed)?|dub(?:bed)?|hsub|hardsub|multi[ -]?audio|softsub)\\s*[\\])]", RegexOption.IGNORE_CASE)

    /** Task 57 (P5): a bracketed quality token glued to a segment — "[1080p]", "(720p)", "[4k]". */
    private val BRACKETED_QUALITY_TAG =
        Regex("[\\[(]\\s*(?:\\d{3,4}[pi]?|[48]k)\\s*[\\])]", RegexOption.IGNORE_CASE)

    /** Task 57 (P5): the decoration pass's word splitter. */
    private val WHITESPACE = Regex("\\s+")

    /** Audio-version words (the [CsAudioTag.parse] vocabulary, whole-segment). */
    private val AUDIO_SEGMENT_WORDS = setOf(
        "sub", "subbed", "dubbed", "dub", "hsub", "hardsub", "h-hardsub", "mix", "raw",
    )

    /** A standalone quality token: 240p–2160p/i, 4K, 8K. */
    private val QUALITY_TOKEN = Regex("^[1-9]\\d{2,3}[pi]?$|^[48]k$")
}
