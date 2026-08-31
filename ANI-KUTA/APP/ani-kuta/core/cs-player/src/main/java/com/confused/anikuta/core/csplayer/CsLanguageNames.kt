package com.confused.anikuta.core.csplayer

import java.util.Locale

/**
 * Task 55 (round 15) — subtitle track display names.
 *
 * The v0.4.2 device round showed subtitle rows as raw URLs: the sidecar
 * `CsSubtitle.id` is "$url|$name" and the sheet fell back to it because the
 * Media3 `SubtitleConfiguration` never carried a LABEL. This helper derives a
 * human name from whatever the provider gave us:
 *
 *  1. a real language tag ("en", "pt-BR", "eng") → the Locale display name
 *     ("English", "Portuguese (Brazil)");
 *  2. a language-ish name ("English", "spanish") → title-cased as-is;
 *  3. a URL (some providers put the file URL in `lang`) → the file name
 *     without extension ("ep1_en.vtt" → "ep1_en");
 *  4. anything else → trimmed as-is; blank → "Subtitle".
 *
 * Pure Kotlin (no Android deps) so it is unit-testable.
 */
object CsLanguageNames {

    /** URL-ish sniffing — covers the provider bug where `lang` is a file URL. */
    private val URLISH = Regex("^(https?://|www\\.|//)", RegexOption.IGNORE_CASE)

    /** ISO-639-1/2 + BCP-47 shape: 2-3 letters, optional -script/-region. */
    private val LANG_TAG = Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$")

    /** Display name for one provider language string. */
    fun display(language: String?): String {
        val raw = language?.trim().orEmpty()
        if (raw.isEmpty()) return "Subtitle"
        if (URLISH.containsMatchIn(raw)) {
            val file = raw.substringAfterLast('/').substringBefore('?').substringBefore('#')
            val noExt = file.substringBeforeLast('.')
            return noExt.ifBlank { "Subtitle" }
        }
        if (LANG_TAG.matches(raw)) {
            val byTag = runCatching { Locale.forLanguageTag(raw).getDisplayLanguage(Locale.ENGLISH) }
                .getOrNull().orEmpty()
            if (byTag.isNotBlank() && !byTag.equals(raw, ignoreCase = true)) return byTag
            val byIso3 = runCatching { Locale(raw.substring(0, 2)).isO3Language.let { iso -> Locale(iso) } }
                .getOrNull()?.getDisplayLanguage(Locale.ENGLISH).orEmpty()
            if (byIso3.isNotBlank() && !byIso3.equals(raw, ignoreCase = true)) return byIso3
        }
        // A real name ("English", "English (SRT)") — keep the provider's wording.
        return raw
    }

    /**
     * True when [language] matches any of the preferred entries (comma-separated
     * codes/names from PlayerPreferences.preferredSubtitleLanguages).
     * Comparison: exact, case-insensitive, and language-tag-level (en ↔ eng ↔
     * English all match each other).
     */
    fun matchesPreferred(language: String?, preferredCsv: String): Boolean {
        val lang = language?.trim().orEmpty()
        if (lang.isEmpty() || preferredCsv.isBlank()) return false
        val langDisplay = display(lang).lowercase()
        val langTag = runCatching { Locale.forLanguageTag(lang).language }.getOrNull().orEmpty()
        return preferredCsv.split(',').any { entry ->
            val e = entry.trim().lowercase()
            if (e.isEmpty()) return@any false
            val eTag = runCatching { Locale.forLanguageTag(e).language }.getOrNull().orEmpty()
            lang.lowercase() == e ||
                langTag.isNotBlank() && eTag.isNotBlank() && langTag == eTag ||
                langDisplay == e
        }
    }
}
