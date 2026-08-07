package com.confused.anikuta.core.download

import com.confused.anikuta.core.videoresolver.ResolverAudioVersion
import com.confused.anikuta.core.videoresolver.ResolverServer
import com.confused.anikuta.core.videoresolver.ResolverVideo

/**
 * The NEW auto-download priority resolution engine.
 *
 * D.2 + REVIEW-5 M44/M45: A 5-step pure-function pipeline that replaces the
 * old project's hardcoded + inconsistent `selectBestVideo` logic.
 *
 * The user can configure the priority order of 3 dimensions (AUDIO, QUALITY,
 * SERVER) via `dimensionPriority` pref. The engine resolves conflicts based on
 * this priority — e.g. if AUDIO is top-priority and the preferred dub is only
 * available at a lower quality on a non-preferred server, the engine picks the
 * dub at the lower quality rather than switching to sub at the preferred quality.
 *
 * ## The 5-step pipeline:
 *
 * 1. **flatten** — convert the 3-tier `List<ResolverServer>` into a flat list
 *    of [Candidate]s (each carrying its server/audio/quality context).
 * 2. **rank** — compute a rank tuple per candidate based on the user's
 *    `dimensionPriority` order. Lower rank = better match.
 * 3. **applyFallbacks** — filter candidates based on per-dimension fallback
 *    strategies (TRY_NEXT = keep non-preferred values; DONT = abort if the
 *    top preference is unavailable).
 * 4. **pick** — sort by rank tuple, return the first (best) candidate.
 * 5. **globalFallback** — if no candidate matches (or the best candidate is a
 *    non-preferred best-effort), apply the global fallback strategy
 *    (BEST_EFFORT / ASK / DO_NOT_DOWNLOAD).
 *
 * ## Why pure functions?
 *
 * The pipeline is pure functions over data classes → trivially unit-testable.
 * Adding a 4th dimension = adding an enum value + a prefs list (no algorithm
 * change). Adding weighted scoring = swapping the lexicographic rank for a
 * weighted score (pipeline shape unchanged). Per-source priority = lifting
 * `dimensionPriority` from a single pref to `Map<sourceId, List<...>>`.
 */
object AutoDownloadEngine {

    /** The 3 preference dimensions. */
    enum class PreferenceDimension { AUDIO, QUALITY, SERVER }

    /** Per-dimension fallback strategy. */
    enum class FallbackStrategy { TRY_NEXT, DONT }

    /** Global fallback when no candidate matches any preference. */
    enum class GlobalFallback { BEST_EFFORT, ASK, DO_NOT_DOWNLOAD }

    /**
     * A flattened candidate — a video + its server/audio/quality context.
     */
    data class Candidate(
        val server: String,
        val audio: String,
        val video: ResolverVideo,
    )

    /**
     * The result of the pipeline.
     */
    sealed class Selection {
        /** A video was selected. [isPerfectMatch] = true if all 3 dimensions match the user's top preference. */
        data class Selected(
            val candidate: Candidate,
            val isPerfectMatch: Boolean,
        ) : Selection()

        /** No candidates available at all. */
        data object NoCandidates : Selection()

        /** Global fallback = DO_NOT_DOWNLOAD. The caller should not enqueue. */
        data object DoNotDownload : Selection()
    }

    /**
     * Runs the 5-step pipeline.
     *
     * @param servers The resolved server list (3-tier: Server → AudioVersion → Video).
     * @param dimensionPriority The user's priority order (e.g. [AUDIO, QUALITY, SERVER]).
     * @param preferredAudio Ordered list of preferred audio labels (e.g. ["SUB", "DUB"]).
     * @param preferredQualities Ordered list of preferred qualities (e.g. ["1080p", "720p"]).
     * @param preferredServers Ordered list of preferred server names.
     * @param audioFallback Fallback strategy for the audio dimension.
     * @param qualityFallback Fallback strategy for the quality dimension.
     * @param serverFallback Fallback strategy for the server dimension.
     * @param globalFallback What to do when no candidate matches.
     */
    fun selectBestVideo(
        servers: List<ResolverServer>,
        dimensionPriority: List<PreferenceDimension>,
        preferredAudio: List<String>,
        preferredQualities: List<String>,
        preferredServers: List<String>,
        audioFallback: FallbackStrategy,
        qualityFallback: FallbackStrategy,
        serverFallback: FallbackStrategy,
        globalFallback: GlobalFallback,
    ): Selection {
        // Step 1: flatten
        val candidates = flatten(servers)
        if (candidates.isEmpty()) return Selection.NoCandidates

        // Step 2: rank (compute rank tuple per candidate)
        val ranked = candidates.map { candidate ->
            CandidateRanked(
                candidate = candidate,
                audioRank = rankOf(candidate.audio, preferredAudio),
                qualityRank = rankOf(candidate.video.quality, preferredQualities),
                serverRank = rankOf(candidate.server, preferredServers),
            )
        }

        // Step 3: applyFallbacks
        val fallbackFiltered = applyFallbacks(
            ranked,
            dimensionPriority,
            audioFallback,
            qualityFallback,
            serverFallback,
            preferredAudio,
            preferredQualities,
            preferredServers,
        )
        if (fallbackFiltered.isEmpty()) {
            // All candidates were filtered out by a DONT fallback.
            return when (globalFallback) {
                GlobalFallback.DO_NOT_DOWNLOAD -> Selection.DoNotDownload
                GlobalFallback.ASK -> Selection.NoCandidates // caller shows picker
                GlobalFallback.BEST_EFFORT -> {
                    // Fall back to the best-ranked candidate from the full list.
                    val best = ranked.minWithOrNull(candidateComparator(dimensionPriority))
                        ?: return Selection.NoCandidates
                    Selection.Selected(best.candidate, isPerfectMatch = false)
                }
            }
        }

        // Step 4: pick (sort by rank tuple, return first)
        val best = fallbackFiltered.minWithOrNull(candidateComparator(dimensionPriority))
            ?: return Selection.NoCandidates

        // Step 5: globalFallback — check if the best candidate is a perfect match.
        val isPerfectMatch = best.audioRank == 0 && best.qualityRank == 0 && best.serverRank == 0
        return if (isPerfectMatch) {
            Selection.Selected(best.candidate, isPerfectMatch = true)
        } else {
            // Best-effort pick (at least one dimension didn't match the top preference).
            when (globalFallback) {
                GlobalFallback.BEST_EFFORT -> Selection.Selected(best.candidate, isPerfectMatch = false)
                GlobalFallback.ASK -> Selection.NoCandidates // caller shows picker
                GlobalFallback.DO_NOT_DOWNLOAD -> Selection.DoNotDownload
            }
        }
    }

    // ── Step 1: flatten ──────────────────────────────────────────────────────

    private fun flatten(servers: List<ResolverServer>): List<Candidate> {
        val result = mutableListOf<Candidate>()
        for (server in servers) {
            for (audio in server.audioVersions) {
                for (video in audio.videos) {
                    result.add(Candidate(server.name, audio.label, video))
                }
            }
        }
        return result
    }

    // ── Step 2: rank ─────────────────────────────────────────────────────────

    /** Returns 0 if [value] is the top preference, 1 for second, etc. Int.MAX_VALUE if not in the list. */
    private fun rankOf(value: String, preferences: List<String>): Int {
        val index = preferences.indexOfFirst { it.equals(value, ignoreCase = true) }
        return if (index >= 0) index else Int.MAX_VALUE
    }

    private data class CandidateRanked(
        val candidate: Candidate,
        val audioRank: Int,
        val qualityRank: Int,
        val serverRank: Int,
    ) {
        /** Builds the rank tuple in the user's priority order. Lower = better. */
        fun rankTuple(priority: List<PreferenceDimension>): List<Int> {
            return priority.map { dim ->
                when (dim) {
                    PreferenceDimension.AUDIO -> audioRank
                    PreferenceDimension.QUALITY -> qualityRank
                    PreferenceDimension.SERVER -> serverRank
                }
            }
        }
    }

    /** Creates a Comparator that compares candidates by their dimension priority. */
    private fun candidateComparator(priority: List<PreferenceDimension>): Comparator<CandidateRanked> {
        return Comparator { a, b ->
            for (dim in priority) {
                val (aRank, bRank) = when (dim) {
                    PreferenceDimension.AUDIO -> a.audioRank to b.audioRank
                    PreferenceDimension.QUALITY -> a.qualityRank to b.qualityRank
                    PreferenceDimension.SERVER -> a.serverRank to b.serverRank
                }
                if (aRank != bRank) return@Comparator aRank.compareTo(bRank)
            }
            0
        }
    }

    // ── Step 3: applyFallbacks ───────────────────────────────────────────────

    private fun applyFallbacks(
        candidates: List<CandidateRanked>,
        priority: List<PreferenceDimension>,
        audioFallback: FallbackStrategy,
        qualityFallback: FallbackStrategy,
        serverFallback: FallbackStrategy,
        preferredAudio: List<String>,
        preferredQualities: List<String>,
        preferredServers: List<String>,
    ): List<CandidateRanked> {
        var filtered = candidates

        // Apply fallbacks in the user's priority order.
        for (dim in priority) {
            val fallback = when (dim) {
                PreferenceDimension.AUDIO -> audioFallback
                PreferenceDimension.QUALITY -> qualityFallback
                PreferenceDimension.SERVER -> serverFallback
            }
            val prefs = when (dim) {
                PreferenceDimension.AUDIO -> preferredAudio
                PreferenceDimension.QUALITY -> preferredQualities
                PreferenceDimension.SERVER -> preferredServers
            }

            if (fallback == FallbackStrategy.DONT && prefs.isNotEmpty()) {
                // Check if the top preference is available in the current filtered set.
                val topAvailable = filtered.any { candidate ->
                    when (dim) {
                        PreferenceDimension.AUDIO -> candidate.audioRank == 0
                        PreferenceDimension.QUALITY -> candidate.qualityRank == 0
                        PreferenceDimension.SERVER -> candidate.serverRank == 0
                    }
                }
                if (!topAvailable) {
                    // Top preference unavailable + DONT → filter to empty (will trigger globalFallback).
                    return emptyList()
                }
                // Keep only candidates that match the top preference for this dimension.
                filtered = filtered.filter { candidate ->
                    when (dim) {
                        PreferenceDimension.AUDIO -> candidate.audioRank == 0
                        PreferenceDimension.QUALITY -> candidate.qualityRank == 0
                        PreferenceDimension.SERVER -> candidate.serverRank == 0
                    }
                }
            }
            // TRY_NEXT = keep all candidates (the rank tuple handles ordering).
        }
        return filtered
    }
}
