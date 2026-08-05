package com.confused.anikuta.core.smartmatcher

/**
 * Normalizes anime titles for fuzzy comparison.
 *
 * Anime titles vary wildly across sources:
 * - "Frieren: Beyond Journey's End" (AniList English) vs "Frieren Sousou no Mahou" (extension romaji)
 * - "Re:Zero − Starting Life in Another World" vs "Re:Zero kara Hajimeru Isekai Seikatsu"
 * - "Season 2", "S2", "2nd Season", "(TV)", "(2024)" suffixes
 * - Punctuation, capitalization, accents
 *
 * TitleNormalizer strips all this noise so the SmartMatcher can compare the
 * underlying title tokens. It does NOT transliterate (romaji → english) — that's
 * a harder problem. Instead, it normalizes each title so that the SAME title
 * written slightly differently on two sources compares equal.
 *
 * CORE_RULES §7: Backend logic (data normalization) — no UI concerns.
 */
object TitleNormalizer {

    /**
     * Normalize a title for comparison.
     *
     * Steps:
     * 1. Lowercase.
     * 2. Remove parenthetical suffixes: "(TV)", "(2024)", "(Dub)".
     * 3. Remove season/sequel suffixes: "Season 2", "S2", "2nd Season", "II", "Part 2".
     * 4. Strip all non-alphanumeric characters (keeps unicode letters for romaji).
     * 5. Collapse whitespace.
     *
     * Returns the normalized title. Empty string if input is blank.
     */
    fun normalize(title: String): String {
        if (title.isBlank()) return ""

        var s = title.lowercase().trim()

        // Remove parenthetical content: "(TV)", "(2024)", "(Dub)", "(Subbed)"
        s = s.replace(Regex("""\([^)]*\)"""), " ")

        // Remove bracket content: "[TV]", "[2024]"
        s = s.replace(Regex("""\[[^\]]*\]"""), " ")

        // Remove season/sequel suffixes (AFTER parenthetical removal):
        // - "season 2", "season 3"
        // - "s2", "s3" (only when preceded by space or at start — avoid stripping "s2" from "soul")
        // - "2nd season", "3rd season", "first season"
        // - "part 1", "part 2"
        // - " ii", " iii" (roman numerals at end)
        s = s.replace(Regex("""\s+season\s+\d+"""), " ")
        s = s.replace(Regex("""\s+\d+(?:st|nd|rd|th)\s+season"""), " ")
        s = s.replace(Regex("""\s+part\s+\d+"""), " ")
        // "s2", "s3" only when at the END of the string or followed by space
        s = s.replace(Regex("""\s+s\d+(?:\s|$)"""), " ")
        // Roman numerals II/III/IV at the end
        s = s.replace(Regex("""\s+(?:ii|iii|iv|v)\s*$"""), " ")

        // Replace common separators with space: ":", "−", "-", "–", "/", "|"
        s = s.replace(Regex("""[\-−–—:/\\|]"""), " ")

        // Remove all remaining non-alphanumeric (keeps unicode letters for romaji)
        s = s.replace(Regex("""[^\p{L}\p{N}\s]"""), " ")

        // Collapse whitespace
        s = s.trim().replace(Regex("""\s+"""), " ")

        return s
    }

    /**
     * Extract a candidate "core title" — the first N tokens of the normalized title.
     *
     * Used for the "contains" bonus: if AniList's normalized title fully contains
     * the extension's core title (or vice versa), they likely refer to the same anime.
     */
    fun coreTokens(title: String, maxTokens: Int = 4): String {
        val normalized = normalize(title)
        return normalized.split(" ").take(maxTokens).joinToString(" ")
    }
}
