package com.confused.anikuta.core.csplayer

/**
 * Task 55 (round 15) — audio-version (SUB/DUB/…) tags for CS streams.
 *
 * The aniyomi ResolverSheet groups videos by SERVER → AUDIO VERSION → QUALITY
 * (the 3-tier hierarchy in :core:video-resolver). The CS resolve sheet now
 * mirrors that: links group by server name + audio label. The label comes
 * from either
 *
 *  - an EXPLICIT tag set at resolution time (sub/dub episode handles merged
 *    in "COMBINED" display mode — the row's (Sub)/(Dub) tag rides the link as
 *    [CsVideoLink.audioTag]), or
 *  - this parser, ported 1:1 from the aniyomi `VideoResolver.parseAudioVersion`
 *    (word-boundary, case-insensitive, HSUB before SUB so "HSub" doesn't match
 *    the shorter word).
 *
 * Pure Kotlin, unit-testable.
 */
object CsAudioTag {

    /** "Default" = no audio version found (the aniyomi label for the same case). */
    const val DEFAULT = "Default"

    /**
     * Parses an audio-version label from free text (a link name, an episode
     * name). Examples (aniyomi semantics):
     *   "SUB - 1080p"              → "SUB"
     *   "HD-1 - Sub - 1080p"       → "SUB"
     *   "Vidstream-2 - Dub - 720p" → "DUB"
     *   "HSUB - 360p"              → "HSUB"
     *   "Mirror 1080p"             → "Default"
     */
    fun parse(text: String?): String {
        if (text.isNullOrBlank()) return DEFAULT
        val patterns = listOf(
            Regex("\\b(hsub|hardsub|h-hardsub)\\b", RegexOption.IGNORE_CASE) to "HSUB",
            Regex("\\b(subbed|sub)\\b", RegexOption.IGNORE_CASE) to "SUB",
            Regex("\\b(dubbed|dub)\\b", RegexOption.IGNORE_CASE) to "DUB",
            Regex("\\b(mix)\\b", RegexOption.IGNORE_CASE) to "MIX",
            Regex("\\b(raw)\\b", RegexOption.IGNORE_CASE) to "RAW",
        )
        for ((pattern, label) in patterns) {
            if (pattern.containsMatchIn(text)) return label
        }
        return DEFAULT
    }

    /** True when the label is a real audio flavor (not "Default"). */
    fun isAudio(label: String?): Boolean = label != null && label != DEFAULT
}
