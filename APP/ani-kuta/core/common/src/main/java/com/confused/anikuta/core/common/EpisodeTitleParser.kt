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

    /**
     * The field delimiter used for episode list serialization (WatchKey.episodeListSerialized).
     *
     * CRITICAL: Uses \u001F (ASCII Unit Separator) instead of '|' because episode
     * URLs can contain '|' characters. Using '|' corrupts the URL, episode number,
     * AND name when the URL contains '|'. \u001F is a control character that never
     * appears in URLs or episode names.
     */
    const val EPISODE_FIELD_DELIMITER = "\u001F"

    private val PREFIX_REGEX = Regex(
        """^(?:Episode|Ep\.?|EP)\s*\d+(?:\.\d+)?\s*[-:–—]\s*""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Detect strings that look like hashes, URLs, or code (not human-readable names).
     *
     * A name is "ugly" if:
     *  - It's a URL path (starts with "/" or contains "://")
     *  - It's a long alphanumeric string with no spaces (>15 chars)
     *  - It's all-uppercase + digits with no spaces (>10 chars) — like "DGFV024L2R0V2IXL0F1"
     *  - It's mostly hex (>15 chars, >70% hex chars) — like a SHA hash
     */
    private fun looksLikeCodeOrHash(name: String): Boolean {
        if (name.isBlank()) return false
        val trimmed = name.trim()
        // URL-like
        if (trimmed.startsWith("/") || trimmed.contains("://")) return true
        // All-uppercase + digits, no spaces, >10 chars (e.g. "DGFV024L2R0V2IXL0F1")
        if (trimmed.length > 10 && !trimmed.contains(" ")) {
            val hasLower = trimmed.any { it in 'a'..'z' }
            val hasUpper = trimmed.any { it in 'A'..'Z' }
            val hasDigit = trimmed.any { it in '0'..'9' }
            // All-caps + digits (no lowercase) = likely a code/ID
            if (hasUpper && hasDigit && !hasLower) return true
            // Long alphanumeric with no spaces = likely code
            if (trimmed.length > 15) {
                // Check if it's mostly hex (hash-like)
                val hexCount = trimmed.count { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
                if (hexCount >= trimmed.length * 0.7) return true
                // Long alphanumeric with no spaces = likely code
                return true
            }
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
     * Format an episode number: 5.0 → "5", 5.5 → "5.5", 0 or negative → "?",
     * unreasonably large (>1000, likely a timestamp/ID) → "?".
     *
     * The "?" fallback handles extensions that return 0, -1, or timestamps/IDs
     * (like 1784388992) for episode_number — which would otherwise show a
     * confusing 10-digit number to the user.
     */
    fun formatEpisodeNumber(episodeNumber: Float): String {
        if (episodeNumber <= 0f) return "?"
        // Unreasonably large — probably a timestamp or ID, not a real episode number.
        // Real episode numbers are typically 1-100 (up to ~1000 for long-running series).
        if (episodeNumber > 1000f) return "?"
        return if (episodeNumber % 1f == 0f) {
            episodeNumber.toInt().toString()
        } else {
            episodeNumber.toString()
        }
    }
}
