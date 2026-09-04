package com.confused.anikuta.core.csplayer

/**
 * Task 55 (round 15) — audio-version (SUB/DUB/…) tags for CS streams.
 * Task 57 (round 17) — smarter free-text detection (user directive:
 * "make it smarter" at spotting the audio version inside stream names).
 *
 * The aniyomi ResolverSheet groups videos by SERVER → AUDIO VERSION → QUALITY
 * (the 3-tier hierarchy in :core:video-resolver). The CS resolve sheet mirrors
 * that: links group by server name + audio label. The label comes from either
 *
 *  - an EXPLICIT tag set at resolution time (sub/dub episode handles merged
 *    in "COMBINED" display mode — the row's (Sub)/(Dub) tag rides the link as
 *    [CsVideoLink.audioTag]), or
 *  - this parser, ported from the aniyomi `VideoResolver.parseAudioVersion`
 *    and EXPANDED in round 17: real CloudStream provider link names use a far
 *    wider vocabulary than the original 3-word port ("Subs", "Subtitles",
 *    "Softsub", "Eng sub", "Hard-sub", …) plus decorations the word pass is
 *    structurally blind to ("Name_Sub" — '_' is a regex word character, so
 *    `\b` never fires there).
 *
 * Round-17 detection strategy (Task 57), two signals in order:
 *
 *  1. WORD PASS (first signal, first family match wins — HSUB stays before
 *     SUB so "HSub" / "Hard sub" never degrade to the shorter word):
 *       HSUB family: hsub, hardsub, h-hardsub, hard-sub, hard sub
 *       SUB  family: subbed, subs, sub, subtitles, softsub, soft-sub,
 *                    eng sub, english sub
 *       DUB  family: dubbed, dubs, dub
 *       plus: mix, raw
 *     All case-insensitive `\b` word boundaries. Brackets ("[SUB]", "(Dub)")
 *     are word boundaries, so bracketed tokens KEEP matching here.
 *
 *  2. DECORATION PASS (second signal, only when the word pass fails):
 *     standalone bracketed tokens "[SUB]", "(DUB)", "[Sub]", "[Dubbed]"
 *     (regex `[\[(]\s*(sub(?:bed)?|dub(?:bed)?|hsub|hardsub)\s*[\])]`), then
 *     underscore-attached tokens "Name_Sub" / "Name_Dub" — belt-and-braces
 *     for "_Sub" style names the word pass cannot see.
 *
 *  3. Nothing matched → "Default".
 *
 * Pure Kotlin, unit-testable. All regexes are compiled ONCE as object-level
 * vals (round-15 CI rule). Public API unchanged: [parse], [DEFAULT], [isAudio].
 */
object CsAudioTag {

    /** "Default" = no audio version found (the aniyomi label for the same case). */
    const val DEFAULT = "Default"

    /**
     * Word-pass vocabulary. FAMILY ORDER MATTERS (first match wins): the HSUB
     * family must run before the SUB family or "Hard sub" would degrade to
     * plain SUB; alternative order inside a family is irrelevant because every
     * alternative maps to the same label.
     */
    private val wordPatterns: List<Pair<Regex, String>> = listOf(
        Regex("\\b(hsub|hardsub|h-hardsub|hard-sub|hard sub)\\b", RegexOption.IGNORE_CASE) to "HSUB",
        Regex("\\b(subbed|subs|sub|subtitles|softsub|soft-sub|eng sub|english sub)\\b", RegexOption.IGNORE_CASE) to "SUB",
        Regex("\\b(dubbed|dubs|dub)\\b", RegexOption.IGNORE_CASE) to "DUB",
        Regex("\\b(mix)\\b", RegexOption.IGNORE_CASE) to "MIX",
        Regex("\\b(raw)\\b", RegexOption.IGNORE_CASE) to "RAW",
    )

    /**
     * Decoration pass, signal A — standalone bracketed tokens: "[SUB]",
     * "(Dub)", "[Sub]", "[Dubbed]". Runs only after the word pass fails (the
     * word pass already catches the usual bracketed forms — this covers
     * decorated variants the word boundaries might miss).
     */
    private val bracketToken =
        Regex("[\\[(]\\s*(sub(?:bed)?|dub(?:bed)?|hsub|hardsub)\\s*[\\])]", RegexOption.IGNORE_CASE)

    /**
     * Decoration pass, signal B — underscore-attached tokens: "Name_Sub",
     * "Name_Dub". '_' is a regex word character, so `\bSub` can never fire on
     * "_Sub"; this pattern reads the token DIRECTLY after the underscore and
     * rejects a trailing letter (so "_Subtitles" doesn't half-match "Sub").
     */
    private val underscoreToken =
        Regex("_(sub(?:bed)?|dub(?:bed)?|hsub|hardsub)(?![a-z])", RegexOption.IGNORE_CASE)

    /**
     * Parses an audio-version label from free text (a link name, an episode
     * name). Examples:
     *   "HD-1 - Sub - 1080p"       → "SUB"
     *   "Vidstream-2 - Dub - 720p" → "DUB"
     *   "Mirror [SUB] 1080p"       → "SUB"  (brackets are word boundaries)
     *   "Streamtape (Dub)"         → "DUB"
     *   "English Subtitles 1080"   → "SUB"
     *   "Softsub 480p"             → "SUB"
     *   "Hard-sub 480p"            → "HSUB" (HSUB family runs before SUB)
     *   "HSUB - 360p"              → "HSUB"
     *   "SomeName_Sub"             → "SUB"  (underscore decoration pass)
     *   "Name_Dub"                 → "DUB"  (underscore decoration pass)
     *   "Mirror 1080p"             → "Default"
     */
    fun parse(text: String?): String {
        if (text.isNullOrBlank()) return DEFAULT
        for ((pattern, label) in wordPatterns) {
            if (pattern.containsMatchIn(text)) return label
        }
        bracketToken.find(text)?.let { return labelFor(it.groupValues[1]) }
        underscoreToken.find(text)?.let { return labelFor(it.groupValues[1]) }
        return DEFAULT
    }

    /** Maps a decoration-pass token ("Sub", "Dubbed", "Hsub", …) to its label. */
    private fun labelFor(token: String): String = when (token.lowercase()) {
        "hsub", "hardsub" -> "HSUB"
        "sub", "subbed" -> "SUB"
        "dub", "dubbed" -> "DUB"
        else -> DEFAULT
    }

    /** True when the label is a real audio flavor (not "Default"). */
    fun isAudio(label: String?): Boolean = label != null && label != DEFAULT
}
