package com.confused.anikuta.core.common

/**
 * Parses episode names to extract clean titles + formats episode numbers.
 *
 * Extensions often put rich info in `SEpisode.name`:
 *   "Episode 5 - The Dragon's Labyrinth" → title = "The Dragon's Labyrinth"
 *   "EP 5 - The Dragon's Labyrinth"      → title = "The Dragon's Labyrinth"
 *   "Ep 5 - The Dragon's Labyrinth"      → title = "The Dragon's Labyrinth"
 *   "The Dragon's Labyrinth"              → title = "The Dragon's Labyrinth" (no prefix)
 *   "Episode 5"                           → title = null (no title, just episode number)
 *
 * Ported from the old project's `EpisodeTitleParser.kt`.
 *
 * ## Why this matters
 *
 * Some extensions return raw URLs, hashes, or code-like strings as the episode
 * name (e.g. "c9eca6cff4f25c6b73be4bfbd546b1d3" or "/anime/series/ep5"). This
 * parser detects those cases and falls back to "Episode N" so the user sees a
 * clean name instead of a hash. This fixes the user's report: "random numbers
 * for the episodes, like random code words or something."
 */
object EpisodeTitleParser {

    private val PREFIX_REGEX = Regex(
        """^(?:Episode|Ep\.?|EP)\s*\d+(?:\.\d+)?\s*[-:–—]\s*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Detect strings that look like hashes, URLs, or code (not human-readable names).
     *
     * A name is "ugly" if:
     *  - It's a long hex string (>20 chars, no spaces, mostly hex chars)
     *  - It looks like a URL path (starts with "/" or contains "://")
     *  - It's a long alphanumeric string with no spaces (>25 chars)
     */
    private fun looksLikeCodeOrHash(name: String): Boolean {
        if (name.isBlank()) return false
        val trimmed = name.trim()
        // URL-like
        if (trimmed.startsWith("/") || trimmed.contains("://")) return true
        // Long string with no spaces
        if (trimmed.length > 25 && !trimmed.contains(" ")) {
            // Check if it's mostly hex (hash-like)
            val hexCount = trimmed.count { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            if (hexCount >= trimmed.length * 0.7) return true
            // Long alphanumeric with no spaces = likely code
            return true
        }
        return false
    }

    /**
     * Extract a clean title from the episode name.
     *
     * @param name The raw SEpisode.name (e.g. "Episode 5 - The Dragon's Labyrinth")
     * @param episodeNumber The episode number (for fallback)
     * @return The cleaned title, or null if the name is just "Episode N" with no title,
     *         or if the name looks like a hash/code (falls back to "Episode N").
     */
    fun parseTitle(name: String, episodeNumber: Float): String? {
        if (name.isBlank()) return null

        // If the name looks like a hash/URL/code, don't show it — fall back to "Episode N".
        if (looksLikeCodeOrHash(name)) return null

        // Try stripping the "Episode X - " prefix
        val stripped = PREFIX_REGEX.replace(name, "").trim()

        // If the stripped result is empty or just a number, there's no title
        if (stripped.isEmpty() || stripped.matches(Regex("""\d+(?:\.\d+)?"""))) {
            return null
        }

        // If the stripped result itself looks like code, discard it
        if (looksLikeCodeOrHash(stripped)) return null

        return stripped
    }

    /**
     * Get the display title for an episode.
     *
     * If the name has a parseable title, returns it.
     * Otherwise returns "Episode N" as fallback.
     */
    fun getDisplayTitle(name: String, episodeNumber: Float): String {
        return parseTitle(name, episodeNumber) ?: "Episode ${formatEpisodeNumber(episodeNumber)}"
    }

    /**
     * Format an episode number: 5.0 → "5", 5.5 → "5.5", 0 or negative → "?".
     *
     * The "?" fallback handles extensions that return 0 or -1 for episode_number
     * (which would otherwise show "0" or "-1" — confusing for the user).
     */
    fun formatEpisodeNumber(episodeNumber: Float): String {
        if (episodeNumber <= 0f) return "?"
        return if (episodeNumber % 1f == 0f) {
            episodeNumber.toInt().toString()
        } else {
            episodeNumber.toString()
        }
    }
}
