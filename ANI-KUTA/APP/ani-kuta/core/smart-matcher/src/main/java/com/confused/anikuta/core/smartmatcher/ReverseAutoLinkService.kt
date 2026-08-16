package com.confused.anikuta.core.smartmatcher

import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import com.confused.anikuta.core.preferences.AutoLinkPreferences
import com.confused.anikuta.data.extension.manager.ExtensionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * D-225: Reverse auto-link service — searches extensions for an AniList anime.
 *
 * When the user opens an AniList anime that has NO linked extension source,
 * this service searches the user's enabled extensions (in order) for a matching
 * SAnime. If a match is found (fuzzy/strict), the extension is auto-linked.
 *
 * **Flow:**
 * 1. Check if auto-link is enabled (global toggle + strategy ≠ MANUAL).
 * 2. For each enabled extension source (in user-defined order, max 5):
 *    a. Search the source with the anime's title.
 *    b. For each result, compute a SmartMatcher score against the AniList title.
 *    c. Track the best match across all results.
 * 3. If best score ≥ threshold → return Matched(source, sAnime, score).
 * 4. If no match after all sources → return NoMatch.
 *
 * **Reuses:** AutoLinkPreferences (same toggle, strategy, threshold, per-source
 * overrides), SmartMatcher (same fuzzy/strict algorithm), TitleNormalizer,
 * LevenshteinDistance.
 *
 * CORE_RULES §7: Backend logic — no UI.
 * CORE_RULES §20: Logged with tag "Anikuta:Core:SmartMatcher:Reverse".
 */
class ReverseAutoLinkService(
    private val extensionManager: ExtensionManager,
    private val preferences: AutoLinkPreferences,
    private val smartMatcher: SmartMatcher = SmartMatcher(),
) {

    companion object {
        private const val TAG = "Anikuta:Core:SmartMatcher:Reverse"
        private const val MAX_SOURCES_TO_SEARCH = 5
    }

    /**
     * Attempt to find a matching extension source for an AniList anime.
     *
     * @param anilistTitle The anime's display title from AniList.
     * @param anilistYear The anime's season year (optional — used for year bonus).
     * @return The best match result, or NoMatch/Skipped/Error.
     */
    suspend fun attemptReverseAutoLink(
        anilistTitle: String,
        anilistYear: Int?,
    ): ReverseAutoLinkResult = withContext(Dispatchers.IO) {
        if (!preferences.autoLinkEnabled) {
            Logger.d(TAG) { "Reverse auto-link disabled (global toggle)" }
            return@withContext ReverseAutoLinkResult.Skipped("Auto-link disabled")
        }

        val strategy = preferences.strategy
        val threshold = preferences.threshold
        val config = SmartMatcherConfig(
            strategy = MatchStrategy.valueOf(strategy.uppercase()),
            threshold = threshold,
        )

        if (config.strategy == MatchStrategy.MANUAL) {
            Logger.d(TAG) { "Strategy=MANUAL → skip" }
            return@withContext ReverseAutoLinkResult.Skipped("Strategy is MANUAL")
        }

        Logger.i(TAG) {
            "Reverse auto-link START: title='$anilistTitle', year=$anilistYear, " +
                "strategy=$strategy, threshold=$threshold"
        }

        // Get all enabled catalogue sources.
        val allSources = extensionManager.sources.value.values
            .filterIsInstance<AnimeCatalogueSource>()

        // Filter to sources where auto-link is enabled (per-source override or global).
        val searchSources = allSources.filter { source ->
            preferences.isAutoLinkEnabledForSource(source.id)
        }.take(MAX_SOURCES_TO_SEARCH)

        if (searchSources.isEmpty()) {
            Logger.w(TAG) { "No enabled sources to search" }
            return@withContext ReverseAutoLinkResult.Skipped("No enabled sources")
        }

        Logger.i(TAG) { "Searching ${searchSources.size} sources (max $MAX_SOURCES_TO_SEARCH)" }

        var bestScore = 0f
        var bestSource: AnimeCatalogueSource? = null
        var bestSAnime: SAnime? = null

        for (source in searchSources) {
            try {
                Logger.d(TAG) { "Searching source: ${source.name} (id=${source.id})" }

                val page = source.getSearchAnime(1, anilistTitle, AnimeFilterList())
                val results = page.animes

                if (results.isEmpty()) {
                    Logger.d(TAG) { "  Source ${source.name}: 0 results" }
                    continue
                }

                Logger.d(TAG) { "  Source ${source.name}: ${results.size} results" }

                // Use SmartMatcher to find the best match among the results.
                // SAnime doesn't have seasonYear, so we pass null for the year bonus.
                for (sAnime in results) {
                    val candidateTitle = sAnime.title
                    val candidateNorm = TitleNormalizer.normalize(candidateTitle)
                    val anilistNorm = TitleNormalizer.normalize(anilistTitle)

                    if (anilistNorm.isBlank()) continue

                    // Base: Levenshtein similarity
                    var score = LevenshteinDistance.similarity(anilistNorm, candidateNorm)

                    // Contains bonus
                    val anilistCore = TitleNormalizer.coreTokens(anilistTitle)
                    val candidateCore = TitleNormalizer.coreTokens(candidateTitle)
                    if (anilistCore.isNotBlank() && candidateCore.isNotBlank()) {
                        if (candidateNorm.contains(anilistCore) || anilistNorm.contains(candidateCore)) {
                            score += config.containsBonus
                        }
                    }

                    score = score.coerceAtMost(1.0f)

                    Logger.v(TAG) {
                        "  vs '$candidateTitle' → score=$score"
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestSource = source
                        bestSAnime = sAnime
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Source ${source.name} search failed: ${e.message}" }
                // Continue to next source — one failure shouldn't stop the search.
            }
        }

        if (bestSource != null && bestSAnime != null && bestScore >= threshold) {
            Logger.i(TAG) {
                "MATCH: source=${bestSource.name}, anime='${bestSAnime.title}', " +
                    "score=$bestScore (threshold=$threshold)"
            }
            ReverseAutoLinkResult.Matched(bestSource, bestSAnime, bestScore)
        } else {
            Logger.i(TAG) {
                "NO MATCH: bestScore=$bestScore < threshold=$threshold"
            }
            ReverseAutoLinkResult.NoMatch(bestScore, anilistTitle)
        }
    }
}

/**
 * Result of a reverse auto-link attempt.
 */
sealed class ReverseAutoLinkResult {
    /** A matching extension source + SAnime was found. */
    data class Matched(
        val source: AnimeCatalogueSource,
        val sAnime: SAnime,
        val score: Float,
    ) : ReverseAutoLinkResult()

    /** No match found across all searched sources. */
    data class NoMatch(
        val bestScore: Float,
        val searchedTitle: String,
    ) : ReverseAutoLinkResult()

    /** Auto-link was skipped (disabled, MANUAL strategy, or no sources). */
    data class Skipped(val reason: String) : ReverseAutoLinkResult()

    /** An error occurred during the search. */
    data class Error(val message: String) : ReverseAutoLinkResult()
}
