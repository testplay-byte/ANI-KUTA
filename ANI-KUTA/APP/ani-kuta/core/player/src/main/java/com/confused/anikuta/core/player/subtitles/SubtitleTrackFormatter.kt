package com.confused.anikuta.core.player.subtitles

/**
 * Formats MPV track-list display names for subtitles and audio tracks.
 *
 * Ported from the old project's `SubtitleTrackFormatter` with one improvement:
 * an ISO 639 → English name mapping so the subtitle sheet shows "English"
 * instead of "eng", "Japanese" instead of "jpn", etc. The old project showed
 * raw language codes.
 *
 * Rules (mirrors the old project + the ISO improvement):
 *  - A title that looks like an ugly filename (`.vtt` / `.srt` / `.ass` / `.ssa`
 *    suffix, or a >20-char hash with no spaces) is discarded so the language
 *    (or fallback "Track N") is shown instead.
 *  - When both a real title and a language are available, the result is
 *    `"<title> (<Language>)"` — e.g. `"Full Sub (English)"`.
 *  - When only one of the two is available, that one is shown.
 *  - When neither is available, fall back to `"Track <id>"`.
 *
 * Note: this object does NOT inject the "Off" sentinel — callers add that
 * themselves (it's a UI-only concept that doesn't belong in a name formatter).
 */
object SubtitleTrackFormatter {

    /**
     * Common ISO 639-2/B and ISO 639-1 codes → English names.
     *
     * Covers the languages most likely to appear in anime extensions.
     * Unknown codes fall back to the raw string (better than hiding it).
     */
    private val LANG_NAMES: Map<String, String> = mapOf(
        "eng" to "English",
        "en" to "English",
        "jpn" to "Japanese",
        "ja" to "Japanese",
        "spa" to "Spanish",
        "es" to "Spanish",
        "por" to "Portuguese",
        "pt" to "Portuguese",
        "fra" to "French",
        "fre" to "French",
        "fr" to "French",
        "deu" to "German",
        "ger" to "German",
        "de" to "German",
        "ita" to "Italian",
        "it" to "Italian",
        "rus" to "Russian",
        "ru" to "Russian",
        "kor" to "Korean",
        "ko" to "Korean",
        "chi" to "Chinese",
        "zho" to "Chinese",
        "zh" to "Chinese",
        "ara" to "Arabic",
        "ar" to "Arabic",
        "hin" to "Hindi",
        "hi" to "Hindi",
        "tha" to "Thai",
        "th" to "Thai",
        "vie" to "Vietnamese",
        "vi" to "Vietnamese",
        "pol" to "Polish",
        "pl" to "Polish",
        "nld" to "Dutch",
        "dut" to "Dutch",
        "nl" to "Dutch",
        "swe" to "Swedish",
        "sv" to "Swedish",
        "tur" to "Turkish",
        "tr" to "Turkish",
        "ukr" to "Ukrainian",
        "uk" to "Ukrainian",
        "heb" to "Hebrew",
        "he" to "Hebrew",
        "ind" to "Indonesian",
        "id" to "Indonesian",
        "may" to "Malay",
        "ms" to "Malay",
        "fil" to "Filipino",
        "tl" to "Filipino",
        "cat" to "Catalan",
        "ca" to "Catalan",
        "ces" to "Czech",
        "cze" to "Czech",
        "cs" to "Czech",
        "ron" to "Romanian",
        "rum" to "Romanian",
        "ro" to "Romanian",
    )

    /**
     * Format a track's display name from its MPV track-list fields.
     *
     * @param id   The MPV track ID (used for the "Track N" fallback).
     * @param title The track's `title` field (often a filename or description).
     * @param lang  The track's `lang` field (ISO 639 code, e.g. "eng").
     * @return A human-readable display name.
     */
    fun formatTrackName(id: Int, title: String, lang: String): String {
        val isUglyFilename = title.isNotBlank() && (
            title.endsWith(".vtt", ignoreCase = true) ||
                title.endsWith(".srt", ignoreCase = true) ||
                title.endsWith(".ass", ignoreCase = true) ||
                title.endsWith(".ssa", ignoreCase = true) ||
                (title.length > 20 && title.none { it == ' ' })
            )
        val displayTitle = if (isUglyFilename) "" else title
        val displayLang = langLookup(lang)
        return when {
            displayTitle.isNotBlank() && displayLang.isNotBlank() -> "$displayTitle ($displayLang)"
            displayTitle.isNotBlank() -> displayTitle
            displayLang.isNotBlank() -> displayLang
            else -> "Track $id"
        }
    }

    private fun langLookup(lang: String): String {
        if (lang.isBlank()) return ""
        val lower = lang.lowercase()
        return LANG_NAMES[lower] ?: lang
    }
}
