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
 *  3. LANGUAGE-AUDIO PASS (D-551, after the word pass, before the sub/dub
 *     decoration pass): providers that ship the SAME server under several
 *     AUDIO VERSIONS name the decorations after the LANGUAGE, not the flavor —
 *     the MovieBox shape from the v1.1.24 device round:
 *       "MovieBox (Hindi Audio)"     → "Hindi"
 *       "MovieBox (Original Audio)"  → "Original"
 *     Two forms are recognized (the vocabulary the wild uses):
 *       a. BRACKETED — "(Hindi Audio)", "[Japanese Audio]", "(Eng Audio)":
 *          a bracket group whose content is short word(s) + the word "audio".
 *       b. WHOLE-SEGMENT — "MovieBox - Hindi Audio - 1080p": a full " - "
 *          segment that IS "<words> Audio" (the separator regex mirrors
 *          CsServerNames.SEPARATOR — kept in sync by contract).
 *     The captured language is normalized ("eng"→"English", "orig"→
 *     "Original", else first-letter-capitalized). NOT a language: the sub/dub
 *     families (the word pass already caught them) and the multi-audio family
 *     ("Multi Audio" stays a decoration — "Default" — exactly like before).
 *     A free-form "… Audio" WITHOUT brackets/segment boundaries is deliberately
 *     NOT matched ("MovieBox Audio Server" must stay Default — no over-matching).
 *
 *  4. Nothing matched → "Default".
 *
 * Pure Kotlin, unit-testable. All regexes are compiled ONCE as object-level
 * vals (round-15 CI rule). The language-audio regexes are PUBLIC: CsServerNames
 * (the server-name derivation, also :core:cs-player) reuses them as the single
 * source of truth — the server name must strip exactly what the label parse
 * recognized, or the same decoration would land in BOTH tiers.
 * Public API unchanged otherwise: [parse], [DEFAULT], [isAudio].
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
     * D-551 — the LANGUAGE-AUDIO vocabulary, PUBLIC (CsServerNames strips the
     * server name with the EXACT regexes this parse recognizes).
     *
     * [LANGUAGE_AUDIO_BRACKET]: a bracket group holding short word(s) + the
     * word "audio" — "(Hindi Audio)", "[Original Audio]", "(Eng Audio)". The
     * capture excludes brackets and requires ≤ 24 chars so a whole prose
     * bracket ("(watch in original audio with subs)") can never become a
     * version label. Case-insensitive.
     */
    val LANGUAGE_AUDIO_BRACKET: Regex =
        Regex("[\\[(]\\s*([a-z][a-z ]{0,23}?)\\s+audio\\s*[\\])]", RegexOption.IGNORE_CASE)

    /**
     * D-551 — the WHOLE-SEGMENT form: a full " - " segment that IS
     * "<words> Audio" — "MovieBox - Hindi Audio - 1080p". Anchored both ends;
     * group 1 = the language words. The separator it splits on mirrors
     * CsServerNames.SEPARATOR (`\s+-\s+`) — kept in sync by contract.
     */
    val LANGUAGE_AUDIO_SEGMENT: Regex =
        Regex("^([a-z][a-z ]{0,23}?)\\s+audio$", RegexOption.IGNORE_CASE)

    /** D-551 — segment splitter for [parse]'s whole-segment pass (mirrors CsServerNames.SEPARATOR). */
    private val SEGMENT_SPLIT = Regex("\\s+-\\s+")

    /**
     * D-551 — captures that are NOT a language version: the multi-audio
     * family (stays a decoration — "Default" — as it always was), the sub/dub
     * families (the word pass owns them; belt-and-braces here), and the
     * meaningless "audio"-only capture.
     */
    private val NOT_A_LANGUAGE = setOf(
        "multi", "multi audio", "mixed", "mix", "sub", "subs", "subbed", "dub",
        "dubbed", "hsub", "hardsub", "softsub", "audio", "track", "unknown", "none",
    )

    /** D-551 — common abbreviations → the display label. */
    private val LANGUAGE_NAMES = mapOf(
        "eng" to "English", "en" to "English", "english" to "English",
        "jp" to "Japanese", "jap" to "Japanese", "jpn" to "Japanese", "japanese" to "Japanese",
        "hin" to "Hindi", "hindi" to "Hindi",
        "kor" to "Korean", "kr" to "Korean", "korean" to "Korean",
        "chi" to "Chinese", "zh" to "Chinese", "mand" to "Mandarin", "mandarin" to "Mandarin",
        "tam" to "Tamil", "tamil" to "Tamil",
        "tel" to "Telugu", "telugu" to "Telugu",
        "mal" to "Malayalam", "malayalam" to "Malayalam",
        "kan" to "Kannada", "kannada" to "Kannada",
        "ben" to "Bengali", "bengali" to "Bengali",
        "pan" to "Punjabi", "punjabi" to "Punjabi",
        "mar" to "Marathi", "marathi" to "Marathi",
        "urd" to "Urdu", "urdu" to "Urdu",
        "ara" to "Arabic", "arabic" to "Arabic",
        "spa" to "Spanish", "spanish" to "Spanish",
        "por" to "Portuguese", "portuguese" to "Portuguese",
        "fre" to "French", "fra" to "French", "french" to "French",
        "ger" to "German", "deu" to "German", "german" to "German",
        "rus" to "Russian", "russian" to "Russian",
        "ind" to "Indonesian", "indo" to "Indonesian", "indonesian" to "Indonesian",
        "tha" to "Thai", "thai" to "Thai",
        "vie" to "Vietnamese", "viet" to "Vietnamese", "vietnamese" to "Vietnamese",
        "fil" to "Filipino", "tagalog" to "Filipino", "filipino" to "Filipino",
        "orig" to "Original", "original" to "Original",
    )

    /**
     * D-551 — normalizes a captured language-audio token ("hindi", "ENG",
     * "orig") into the display label ("Hindi", "English", "Original"); null
     * when the capture is NOT a language (the [NOT_A_LANGUAGE] family).
     */
    fun languageAudioLabel(capture: String): String? {
        val key = capture.trim().lowercase()
        if (key.isEmpty() || key in NOT_A_LANGUAGE) return null
        return LANGUAGE_NAMES[key] ?: key.split(' ').joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

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
     *   "MovieBox (Hindi Audio)"   → "Hindi"    (D-551 language-audio pass)
     *   "MovieBox (Original Audio)" → "Original" (D-551)
     *   "MovieBox - Hindi Audio"   → "Hindi"    (D-551 whole-segment form)
     *   "Server [Multi Audio]"     → "Default"  (multi-audio stays a decoration)
     *   "MovieBox Audio Server"    → "Default"  (no free-form over-matching)
     */
    fun parse(text: String?): String {
        if (text.isNullOrBlank()) return DEFAULT
        for ((pattern, label) in wordPatterns) {
            if (pattern.containsMatchIn(text)) return label
        }
        // D-551: the language-audio pass — bracketed first, then whole segments.
        LANGUAGE_AUDIO_BRACKET.find(text)?.let { return languageAudioLabel(it.groupValues[1]) ?: DEFAULT }
        for (segment in text.split(SEGMENT_SPLIT)) {
            LANGUAGE_AUDIO_SEGMENT.find(segment.trim())?.let {
                return languageAudioLabel(it.groupValues[1]) ?: DEFAULT
            }
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
