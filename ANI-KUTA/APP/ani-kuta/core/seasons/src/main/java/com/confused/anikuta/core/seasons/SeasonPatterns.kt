package com.confused.anikuta.core.seasons

/**
 * D-312: The season-pattern REGISTRY — the single place to teach the season
 * engine a new episode-name format.
 *
 * ## How to support a new format
 *
 * Add a [SeasonPattern] entry to [DEFAULT] (order matters — first match wins)
 * or call [register] at runtime (kept for future per-extension configuration).
 * Nothing else in the app needs to change: consumers only ever see
 * [SeasonTag]s from [SeasonDetector.parseSeasonTag].
 *
 * ## Supported formats (v2 — superset of the D-307 v1 spec)
 *
 * | id             | matches                                            | example                          |
 * |----------------|----------------------------------------------------|----------------------------------|
 * | season-episode | "Season N <sep> Episode M [sep] Title" (+ parens)  | "( Season 5 - Episode 12 - The Black Cat )" |
 * | compact        | "S< N >E< M >" / "SN EP M" / "SN Episode M" (+ parens) | "S5E12 - The Black Cat"      |
 * | season-only    | "Season N" alone (with optional parens/separator)  | "( Season 5 ) The Black Cat"     |
 *
 * All patterns: case-insensitive, optional wrapping parentheses, flexible
 * separators (`-`, `–`, `—`, `:`, `.`), and an optional trailing title that
 * may itself contain separators/parens (the remainder is only trimmed, never
 * re-parsed).
 *
 * ## Not yet supported (deliberate — extend when a real extension needs it)
 *
 * - Tag at the END of the name ("Title - S5E12")
 * - "Part N" / "Cour N" wording
 * - Non-ASCII numerals
 *
 * When adding a pattern, mirror the D-312 rule: verify against REAL episode
 * names from the episode-list dumper logs first (`Anikuta:EpisodeDump` tag),
 * not against invented examples.
 */
data class SeasonPattern(
    /** Stable identifier — surfaces in diagnostics/dump logs. */
    val id: String,
    /** Regex matched against the START of the raw episode name. */
    val regex: Regex,
    /** Human description for maintainers. */
    val description: String,
)

object SeasonPatterns {

    /**
     * The default, ordered pattern set. FIRST MATCH WINS — keep the most
     * specific patterns (season + episode) above the looser ones
     * (season-only), or the loose ones will steal their prefixes.
     */
    val DEFAULT: List<SeasonPattern> = listOf(
        SeasonPattern(
            id = "season-episode",
            description = "\"Season N - Episode M - Title\" (optional parens, flexible separators)",
            regex = Regex(
                // 1: season number — 2: episode-in-season number.
                """^\(?\s*season\s+(\d+)\s*[-:–—.]?\s*(?:episode|ep\.?)\s*(\d+)\s*(?:[-:–—.]\s*)?""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        SeasonPattern(
            id = "compact",
            description = "\"S5E12\" / \"S5 EP 12\" / \"S5 Episode 12\" compact forms (+ parens)",
            regex = Regex(
                // 1: season number — 2: episode-in-season number.
                """^\(?\s*S\s*(\d{1,2})\s*(?:EPISODE|EP|E)\W{0,2}(\d{1,4})\s*(?:[-:–—.]\s*)?""",
                RegexOption.IGNORE_CASE,
            ),
        ),
        SeasonPattern(
            id = "season-only",
            description = "\"Season N\" alone — no episode-in-season number (+ parens)",
            regex = Regex(
                // 1: season number. No episode group.
                """^\(?\s*season\s+(\d+)\s*\)?\s*(?:[-:–—.]\s*)?""",
                RegexOption.IGNORE_CASE,
            ),
        ),
    )

    @Volatile
    private var patterns: List<SeasonPattern> = DEFAULT

    /** All active patterns, in match order. */
    fun all(): List<SeasonPattern> = patterns

    /**
     * Register an additional pattern (appended LAST — after the defaults).
     * Future hook for per-extension season configurations; unused today.
     */
    @Synchronized
    fun register(pattern: SeasonPattern) {
        patterns = patterns + pattern
    }

    /** Reset to [DEFAULT] (test/repair hook). */
    @Synchronized
    fun reset() {
        patterns = DEFAULT
    }
}
