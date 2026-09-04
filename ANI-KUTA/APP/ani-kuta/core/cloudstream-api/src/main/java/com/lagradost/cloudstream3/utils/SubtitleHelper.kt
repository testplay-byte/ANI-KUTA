// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Session-1 scope (doc 23 §4): language-code normalization for SubtitleFile.langTag.
// The full language-name lookup tables land with the subtitles/playback session.
package com.lagradost.cloudstream3.utils

@Suppress("unused", "MemberVisibilityCanBePrivate")
object SubtitleHelper {

    fun fromLanguageToTwoLetters(input: String, looseCheck: Boolean): String? {
        val cleaned = input.trim().lowercase()
        return if (cleaned.length == 2) cleaned else null
    }

    fun fromLanguageToThreeLetters(input: String): String? = null

    fun fromLanguageToTagIETF(languageName: String?, halfMatch: Boolean? = false): String? = null

    fun fromTwoLettersToLanguage(input: String): String? = null

    fun fromThreeLettersToLanguage(input: String): String? = null

    fun fromTagToLanguageName(languageCode: String?, localizedTo: String? = null): String? = null

    fun fromTagToEnglishLanguageName(languageCode: String?): String? = null

    fun fromCodeToOpenSubtitlesTag(languageCode: String?): String? = null

    /** Normalizes "pt-br" → "pt-BR", "en" → "en". */
    fun fromCodeToLangTagIETF(languageCode: String?): String? {
        if (languageCode == null) return null
        val parts = languageCode.trim().split("-", "_")
        if (parts.size == 1) {
            val code = parts[0].lowercase()
            return if (code.length == 2 || code.length == 3) code else null
        }
        val lang = parts[0].lowercase()
        val region = parts[1].uppercase()
        return "$lang-$region"
    }

    fun isWellFormedTagIETF(langTagIETF: String?): Boolean {
        if (langTagIETF == null) return false
        return Regex("""^[a-zA-Z]{2,3}(-[a-zA-Z]{2,8})*$""").matches(langTagIETF)
    }

    fun getFlagFromIso(inp: String?): String? = null

    fun getNameNextToFlagEmoji(languageCode: String?, localizedTo: String? = null): String? = null
}
