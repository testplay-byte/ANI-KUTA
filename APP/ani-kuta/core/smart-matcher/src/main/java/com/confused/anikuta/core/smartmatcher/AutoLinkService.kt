package com.confused.anikuta.core.smartmatcher

import com.confused.anikuta.core.anilist.api.AniListApi
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.AutoLinkPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the auto-link flow for an extension anime entry.
 *
 * Flow:
 * 1. Check [AutoLinkPreferences.isAutoLinkEnabledForSource] — if false → [AutoLinkResult.Skipped].
 * 2. Check [AutoLinkPreferences.getCachedAniListId] — if hit → [AutoLinkResult.Cached].
 * 3. Build [SmartMatcherConfig] from preferences (strategy + threshold).
 * 4. If strategy = MANUAL → [AutoLinkResult.Skipped] (caller shows manual sheet).
 * 5. Search AniList by title ([anilistApi.searchAnime]).
 * 6. Run [SmartMatcher.findBestMatch] against the results.
 * 7. On [MatchResult.Match] → cache the ID → [AutoLinkResult.Matched].
 * 8. On [MatchResult.NoMatch] → [AutoLinkResult.NoMatch] (caller shows manual sheet).
 *
 * CORE_RULES §7: Backend logic — no UI. Returns sealed result.
 * CORE_RULES §20: Logged with tag "Anikuta:Core:SmartMatcher:Service".
 */
class AutoLinkService(
    private val anilistApi: AniListApi,
    private val preferences: AutoLinkPreferences,
    private val smartMatcher: SmartMatcher = SmartMatcher(),
) {

    companion object {
        private const val TAG = "Anikuta:Core:SmartMatcher:Service"
    }

    /**
     * Attempt to auto-link an extension anime entry to AniList.
     *
     * @param sourceId The extension source ID.
     * @param animeUrl The anime's URL on the source (cache key).
     * @param title The title from the extension SAnime.
     * @param year Optional year hint (improves match quality).
     */
    suspend fun attemptAutoLink(
        sourceId: Long,
        animeUrl: String,
        title: String,
        year: Int? = null,
    ): AutoLinkResult = withContext(Dispatchers.IO) {
        // 1. Per-source / global check
        if (!preferences.isAutoLinkEnabledForSource(sourceId)) {
            Logger.i(TAG) { "Auto-link disabled for source=$sourceId → skip" }
            return@withContext AutoLinkResult.Skipped("Auto-link disabled for this source")
        }

        // 2. Cache check
        val cachedId = preferences.getCachedAniListId(sourceId, animeUrl)
        if (cachedId > 0) {
            Logger.i(TAG) { "Cache HIT: ($sourceId, $animeUrl) → anilistId=$cachedId" }
            return@withContext AutoLinkResult.Cached(cachedId)
        }

        // 3. Build config from preferences
        val strategy = when (preferences.strategy) {
            "strict" -> MatchStrategy.STRICT
            "manual" -> MatchStrategy.MANUAL
            else -> MatchStrategy.FUZZY
        }
        val config = SmartMatcherConfig(
            strategy = strategy,
            threshold = preferences.threshold,
        )

        // 4. MANUAL strategy → caller shows manual sheet
        if (strategy == MatchStrategy.MANUAL) {
            Logger.i(TAG) { "Strategy=MANUAL → skip (caller shows manual sheet)" }
            return@withContext AutoLinkResult.Skipped("Strategy is MANUAL")
        }

        // 5. Search AniList
        val candidates = try {
            anilistApi.searchAnime(title, page = 1, perPage = 20)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "AniList search failed for '$title'" }
            return@withContext AutoLinkResult.Error("AniList search failed: ${e.message}")
        }

        if (candidates.isEmpty()) {
            Logger.w(TAG) { "AniList returned 0 results for '$title'" }
            return@withContext AutoLinkResult.NoMatch(0f, title)
        }

        // 6. SmartMatcher
        val matchResult = smartMatcher.findBestMatch(title, year, candidates, config)

        // 7. Handle result
        when (matchResult) {
            is MatchResult.Match -> {
                val matchedAnilist = candidates.firstOrNull {
                    TitleNormalizer.normalize(it.displayName) == TitleNormalizer.normalize(matchResult.matchedTitle)
                } ?: candidates.firstOrNull()
                if (matchedAnilist != null) {
                    preferences.cacheAniListId(sourceId, animeUrl, matchedAnilist.id)
                    Logger.i(TAG) { "Caching link: ($sourceId, $animeUrl) → anilistId=${matchedAnilist.id} (score=${matchResult.score})" }
                    AutoLinkResult.Matched(matchedAnilist.id, matchResult.score)
                } else {
                    AutoLinkResult.NoMatch(matchResult.score, title)
                }
            }
            is MatchResult.NoMatch -> AutoLinkResult.NoMatch(matchResult.bestScore, title)
            is MatchResult.Skipped -> AutoLinkResult.Skipped(matchResult.reason)
            is MatchResult.Error -> AutoLinkResult.Error(matchResult.message)
        }
    }

    /**
     * Cache a manually-selected AniList ID (from the manual link sheet).
     */
    fun cacheManualLink(sourceId: Long, animeUrl: String, anilistId: Int) {
        preferences.cacheAniListId(sourceId, animeUrl, anilistId)
        Logger.i(TAG) { "Cached manual link: ($sourceId, $animeUrl) → anilistId=$anilistId" }
    }

    /**
     * Clear a cached link (used when unlinking).
     */
    fun clearCachedLink(sourceId: Long, animeUrl: String) {
        preferences.clearCachedAniListId(sourceId, animeUrl)
        Logger.i(TAG) { "Cleared cached link: ($sourceId, $animeUrl)" }
    }
}
