package com.confused.anikuta.settings.search

/**
 * D-558 — the settings SEARCH ENGINE. Pure Kotlin (no Compose, no Android
 * imports) so it is unit-testable and the ranking can be tuned without
 * touching any UI.
 *
 * # The scoring model (relevance, the user's spec: "sorted properly based on
 * the relevance, based on what the user was intending to be searching for")
 *
 * The query is normalized + tokenized; EVERY token must match somewhere on
 * an entry (any field, synonyms included) or the entry is out. The per-token
 * score is the strongest of:
 *
 *  - 100  title IS the token (exact)
 *  -  85  title starts with the token
 *  -  75  title contains the token at a word boundary
 *  -  60  title contains the token anywhere
 *  -  90  the FULL phrase matches the title (multi-token queries, D-559)
 *  -  70  the FULL phrase matches the keywords (multi-token queries, D-559:
 *         curated multi-word synonyms like "episode thumbnail" hit whole)
 *  -  65  a keyword IS the token (exact synonym hit)
 *  -  50  a keyword starts with the token
 *  -  40  the keywords contain the token anywhere
 *  -  45  a SYNONYM of the token hits the title (word-boundary)
 *  -  35  a synonym of the token hits the keywords
 *  -  20  the token is a subsequence of the title ("thme" → "theme" — the
 *         forgiving typo floor; NEVER applies to empty results)
 *
 * The entry's total = the SUM of its per-token scores, so multi-token
 * queries ("dark mode") rank entries matching both tokens strongly above
 * entries barely touching one. Ties break alphabetically by title.
 *
 * # Tuning this later (the future-proofing the user asked for)
 *
 * - A search surfaces nothing → add the missing WORDS to the entry's
 *   [SettingsSearchEntry.keywords] in [SettingsSearchIndex] (zero code).
 * - A result ranks wrongly → adjust the WEIGHTS below (they are the only
 *   magic numbers in the system).
 * - A new field to match on (e.g. descriptions) → add it to [ScoredFields].
 */
object SettingsSearchEngine {

    /** Weights — the single tuning surface (see the class KDoc). */
    private const val W_TITLE_EXACT = 100
    private const val W_TITLE_PHRASE = 90
    private const val W_TITLE_PREFIX = 85
    private const val W_TITLE_WORD = 75
    private const val W_TITLE_CONTAINS = 60
    private const val W_KEYWORD_PHRASE = 70
    private const val W_KEYWORD_EXACT = 65
    private const val W_KEYWORD_PREFIX = 50
    private const val W_KEYWORD_CONTAINS = 40
    private const val W_SYNONYM_TITLE = 45
    private const val W_SYNONYM_KEYWORD = 35
    private const val W_FUZZY_SUBSEQUENCE = 20

    private const val MAX_RESULTS = 12

    /**
     * The synonym expansion map — a query token ALSO matches through these.
     * Keys are normalized tokens; values are the alternate tokens they
     * imply. The user's own example lives here verbatim: theme ⇄ ui ⇄ accent
     * all reach the appearance/theme entries. Keep the map SMALL and
     * SYMMETRIC-minded (a synonym hit scores BELOW a direct hit, so adding
     * synonyms never drowns exact matches).
     */
    private val SYNONYMS: Map<String, List<String>> = mapOf(
        "theme" to listOf("appearance", "mode", "palette", "color", "dark", "light"),
        "ui" to listOf("appearance", "interface", "look", "design", "visual"),
        "accent" to listOf("color", "palette", "tint", "theme"),
        "color" to listOf("palette", "accent", "tint"),
        "look" to listOf("appearance", "theme", "design"),
        "skin" to listOf("appearance", "theme"),
        "dark" to listOf("amoled", "black"),
        "amoled" to listOf("black", "dark"),
        "font" to listOf("text", "typography"),
        "ep" to listOf("episode"),
        "eps" to listOf("episode"),
        "episodes" to listOf("episode"),
        // D-559: the v1.1.32 round searched "episode thumbnail" and got
        // NOTHING — neither token had a synonym entry, so entries that only
        // spoke of posters/images (or carried no thumbnail word at all)
        // never matched. Both nouns now expand (and the reverse lookup
        // feeds off them).
        "episode" to listOf("ep", "episodes", "chapter"),
        "thumbnail" to listOf("thumbnails", "poster", "image", "cover", "art"),
        "thumbnails" to listOf("thumbnail", "poster", "image", "cover", "art"),
        "numbering" to listOf("number", "episode"),
        "dl" to listOf("download"),
        "downloads" to listOf("download"),
        "sub" to listOf("subtitle", "audio"),
        "dub" to listOf("audio"),
        "audio" to listOf("sound", "dub", "sub"),
        "poster" to listOf("thumbnail", "image", "cover"),
        "image" to listOf("poster", "picture", "thumbnail"),
        "picture" to listOf("image", "poster"),
        "wallpaper" to listOf("background", "image"),
        "background" to listOf("wallpaper", "image"),
        "player" to listOf("playback", "video", "watch"),
        "playback" to listOf("player", "video"),
        "speed" to listOf("playback", "player"),
        "quality" to listOf("resolution"),
        "cache" to listOf("caching", "storage"),
        "notifications" to listOf("alerts", "updates"),
        "alerts" to listOf("notifications"),
        "update" to listOf("updates", "check"),
        "updates" to listOf("update"),
        "version" to listOf("about"),
        "apk" to listOf("install", "update"),
        "tracker" to listOf("trackers", "anilist", "mal"),
        "anilist" to listOf("tracker", "trackers"),
        "mal" to listOf("tracker", "trackers"),
        "sync" to listOf("tracker", "populate"),
        "history" to listOf("watched", "recently"),
        "lang" to listOf("language"),
        "haptics" to listOf("vibration", "feedback"),
        "anim" to listOf("animation"),
        "blur" to listOf("effect"),
        "extension" to listOf("extensions", "source", "plugin"),
        "extensions" to listOf("extension", "source", "plugin"),
        "plugin" to listOf("extension", "extensions", "cloudstream"),
        "source" to listOf("extension", "extensions"),
        "repo" to listOf("repository", "repositories"),
        "repos" to listOf("repository", "repositories"),
    )

    /** The matched-against fields of one entry, pre-normalized. */
    private data class ScoredFields(
        val title: String,
        val keywords: String,
    )

    fun search(
        query: String,
        index: List<SettingsSearchEntry> = SettingsSearchIndex.entries,
    ): List<SettingsSearchResult> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        // D-559: the FULL normalized query as one phrase — the unit the
        // phrase bonus/rescue below matches (only meaningful for
        // multi-token queries).
        val phrase = normalize(query)
        val isMultiToken = tokens.size > 1
        val results = ArrayList<SettingsSearchResult>(index.size)
        for (entry in index) {
            val fields = ScoredFields(
                title = normalize(entry.title),
                keywords = normalize(entry.keywords.joinToString(" ")),
            )
            var total = 0
            var allMatched = true
            for (token in tokens) {
                val tokenScore = scoreToken(token, fields) ?: run {
                    allMatched = false
                    0
                }
                total += tokenScore
            }
            // D-559: the PHRASE BONUS — when the full phrase lands on the
            // title or the curated keywords, the entry outranks its
            // token-only rivals ("Episode thumbnail" the row beats
            // everything else for the query "episode thumbnail"). The
            // per-token pass already guarantees the match (every token of
            // a phrase present in a field is that field's substring), so
            // the phrase only ever BONUSSES — no rescue path, no
            // double-counting.
            if (allMatched && total > 0) {
                if (isMultiToken) {
                    when {
                        fields.title.contains(phrase) -> total += W_TITLE_PHRASE
                        fields.keywords.contains(phrase) -> total += W_KEYWORD_PHRASE
                    }
                }
                results += SettingsSearchResult(entry, total)
            }
        }
        return results
            .sortedWith(compareByDescending<SettingsSearchResult> { it.score }.thenBy { it.entry.title })
            .take(MAX_RESULTS)
    }

    /** The strongest match score for ONE token against one entry, or null. */
    private fun scoreToken(token: String, fields: ScoredFields): Int? {
        // Title field — the primary signal.
        if (fields.title == token) return W_TITLE_EXACT
        if (fields.title.startsWith(token)) return W_TITLE_PREFIX
        if (containsAtWordBoundary(fields.title, token)) return W_TITLE_WORD
        if (fields.title.contains(token)) return W_TITLE_CONTAINS

        // Keyword field — the synonym surface the index curates.
        val keywordTokens = fields.keywords.split(' ').filter { it.isNotBlank() }
        if (keywordTokens.any { it == token }) return W_KEYWORD_EXACT
        if (keywordTokens.any { it.startsWith(token) }) return W_KEYWORD_PREFIX
        if (fields.keywords.contains(token)) return W_KEYWORD_CONTAINS

        // The built-in synonym expansion (theme ⇄ ui ⇄ accent …).
        val expanded = SYNONYMS[token].orEmpty() +
            SYNONYMS.entries.filter { (_, v) -> v.contains(token) }.map { it.key }
        for (syn in expanded.distinct()) {
            if (containsAtWordBoundary(fields.title, syn) || fields.title == syn) {
                return W_SYNONYM_TITLE
            }
            if (fields.keywords.contains(syn)) return W_SYNONYM_KEYWORD
        }

        // The forgiving typo floor — subsequence of the title ("thme" → theme).
        if (isSubsequence(token, fields.title)) return W_FUZZY_SUBSEQUENCE
        return null
    }

    /** normalize → lowercase; tokens split on anything non-letter/digit. */
    private fun tokenize(query: String): List<String> =
        normalize(query)
            .split(' ')
            .filter { it.isNotBlank() }

    private fun normalize(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    /** "theme mode" contains "mode" at a word boundary. */
    private fun containsAtWordBoundary(haystack: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        var idx = haystack.indexOf(needle)
        while (idx >= 0) {
            val beforeOk = idx == 0 || !haystack[idx - 1].isLetterOrDigit()
            val after = idx + needle.length
            val afterOk = after >= haystack.length || !haystack[after].isLetterOrDigit()
            if (beforeOk && afterOk) return true
            idx = haystack.indexOf(needle, idx + 1)
        }
        return false
    }

    /** Every char of [needle] appears in [haystack] in order ("thme"→"theme"). */
    private fun isSubsequence(needle: String, haystack: String): Boolean {
        if (needle.length > haystack.length) return false
        var h = 0
        for (n in needle) {
            h = haystack.indexOf(n, h)
            if (h < 0) return false
            h++
        }
        return true
    }
}
