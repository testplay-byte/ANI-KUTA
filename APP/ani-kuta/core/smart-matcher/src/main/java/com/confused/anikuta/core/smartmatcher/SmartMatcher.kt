package com.confused.anikuta.core.smartmatcher

import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.common.Logger

/**
 * Fuzzy title matcher — compares an extension SAnime title against a list of
 * AniList search results and picks the best match.
 *
 * ## Algorithm
 * 1. Normalize both titles via [TitleNormalizer].
 * 2. Compute Levenshtein similarity ratio (0.0..1.0).
 * 3. Apply bonuses:
 *    - **Contains bonus** (+0.05): if one normalized title's core tokens fully
 *      contain the other's. Handles "Frieren: Beyond Journey's End" vs "Frieren".
 *    - **Year bonus** (+0.10): if both have a year and they match.
 * 4. Cap the final score at 1.0.
 * 5. If best score >= [SmartMatcherConfig.threshold] → [MatchResult.Match].
 *
 * ## Strategy
 * - [MatchStrategy.FUZZY]: full algorithm (above).
 * - [MatchStrategy.STRICT]: only match if normalized titles are EXACTLY equal.
 * - [MatchStrategy.MANUAL]: always [MatchResult.Skipped] — caller shows manual sheet.
 *
 * CORE_RULES §7: Pure logic — no I/O, no UI. Logger used for debugging.
 */
class SmartMatcher {

    companion object {
        private const val TAG = "Anikuta:Core:SmartMatcher"
    }

    /**
     * Find the best AniList match for [extensionTitle] among [candidates].
     *
     * @param extensionTitle The title from the extension SAnime.
     * @param extensionYear Optional year from the extension (if known).
     * @param candidates AniList search results (title + seasonYear).
     * @param config Matcher configuration.
     * @return The best [MatchResult], or [NoMatch] if no candidate exceeded threshold.
     */
    fun findBestMatch(
        extensionTitle: String,
        extensionYear: Int?,
        candidates: List<AniListAnime>,
        config: SmartMatcherConfig,
    ): MatchResult {
        if (config.strategy == MatchStrategy.MANUAL) {
            Logger.d(TAG) { "Strategy=MANUAL → skip" }
            return MatchResult.Skipped("Strategy is MANUAL")
        }

        if (candidates.isEmpty()) {
            return MatchResult.NoMatch(0f, "No AniList candidates")
        }

        val extNorm = TitleNormalizer.normalize(extensionTitle)
        if (extNorm.isBlank()) {
            return MatchResult.NoMatch(0f, "Extension title normalized to empty")
        }
        val extCore = TitleNormalizer.coreTokens(extensionTitle)
        Logger.d(TAG) { "findBestMatch: extNorm='$extNorm', extCore='$extCore', ${candidates.size} candidates, threshold=${config.threshold}, strategy=${config.strategy}" }

        var bestScore = 0f
        var bestTitle = ""
        var bestReason = "no candidate scored above 0"

        for (candidate in candidates) {
            val candidateTitle = candidate.displayName
            val candidateYear = candidate.seasonYear
            val score = scorePair(
                extNorm = extNorm,
                extCore = extCore,
                extYear = extensionYear,
                candidate = candidate,
            )

            Logger.v(TAG) { "  vs '${candidate.displayName}' (year=$candidateYear) → score=$score" }

            if (config.strategy == MatchStrategy.STRICT) {
                // Strict: only exact normalized match counts
                val candidateNorm = TitleNormalizer.normalize(candidateTitle)
                if (candidateNorm == extNorm) {
                    Logger.d(TAG) { "STRICT match: '$candidateTitle' (score=1.0)" }
                    return MatchResult.Match(1.0f, candidateTitle)
                }
                if (score > bestScore) {
                    bestScore = score
                    bestTitle = candidateTitle
                    bestReason = "strict: no exact match (best normalized diff: '$candidateNorm' vs '$extNorm')"
                }
                continue
            }

            // FUZZY: track best, return if >= threshold
            if (score > bestScore) {
                bestScore = score
                bestTitle = candidateTitle
                bestReason = "best=$score threshold=${config.threshold}"
            }
        }

        return if (bestScore >= config.threshold) {
            Logger.i(TAG) { "MATCH: '$bestTitle' score=$bestScore (threshold=${config.threshold})" }
            MatchResult.Match(bestScore, bestTitle)
        } else {
            Logger.i(TAG) { "NO MATCH: best='$bestTitle' score=$bestScore < threshold=${config.threshold}" }
            MatchResult.NoMatch(bestScore, bestReason)
        }
    }

    /**
     * Score one (extension, candidate) pair.
     */
    private fun scorePair(
        extNorm: String,
        extCore: String,
        extYear: Int?,
        candidate: AniListAnime,
        config: SmartMatcherConfig = SmartMatcherConfig(),
    ): Float {
        val candidateNorm = TitleNormalizer.normalize(candidate.displayName)
        if (candidateNorm.isBlank()) return 0f

        // Base: Levenshtein similarity
        var score = LevenshteinDistance.similarity(extNorm, candidateNorm)

        // Contains bonus: one title's core tokens fully inside the other
        val candidateCore = TitleNormalizer.coreTokens(candidate.displayName)
        if (extCore.isNotBlank() && candidateCore.isNotBlank()) {
            if (candidateNorm.contains(extCore) || extNorm.contains(candidateCore)) {
                score += config.containsBonus
            }
        }

        // Year bonus: if both have a year and they match
        if (extYear != null && candidate.seasonYear != null && extYear == candidate.seasonYear) {
            score += config.yearBonus
        }

        return score.coerceAtMost(1.0f)
    }
}
