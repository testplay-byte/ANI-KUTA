package com.confused.anikuta.core.videoresolver

import com.confused.anikuta.core.common.Logger
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "Anikuta:Core:VideoResolver"

/**
 * Task 50 (round 10, Fix B / FM-4): probe memoization for sources whose
 * extension library has NO hoster API at all (ext-lib ≤ 15 —
 * `getHosterList` throws [AbstractMethodError] when invoked through the
 * mismatched bytecode). The outcome is deterministic for a given source id,
 * so it is memoized per-process and the probe is never repeated — no more
 * "one wasted AbstractMethodError per resolve" noise, and more importantly
 * no chance of the deterministic AME being captured as a *failure* on a
 * later resolve (which would mask the real getVideoList error).
 */
internal object AniyomiHosterApiMemo {
    private val unsupported = ConcurrentHashMap<Long, Boolean>()

    fun isUnsupported(id: Long): Boolean = unsupported.containsKey(id)

    fun markUnsupported(id: Long) {
        unsupported[id] = true
    }
}

/** A resolution failure captured while isolating per-hoster/per-call errors. */
private class CapturedFailure(val throwable: Throwable, val stage: String)

/**
 * Task 50 (round 10, Fix C): the ANIYOMI resolution pipeline.
 *
 * Extracted verbatim from the old `VideoResolver.resolveVideoEntries` (the
 * pre-split body) with three fixes applied (FM-3/4/5 — see below). This is
 * the pipeline for every source that is NOT a bridged CloudStream provider
 * ([AnimeHttpSource.isCloudStreamBridged] == false):
 *
 * 1. Probe `getHosterList` first (ext-lib 16+ hoster-based API).
 * 2. Fall back to `getVideoList(episode)` (ext-lib < 16 direct API).
 *
 * Task 47: the timeout budget is the SOURCE's own
 * [AnimeHttpSource.videoListTimeoutMs].
 *
 * Task 49 (R9-A FM-2 — the error black hole): failures are captured while
 * isolation is preserved (one dead hoster never aborts the others); when the
 * FINAL result is empty, the captured failure is rethrown so the caller
 * surfaces the REAL reason.
 *
 * Round-10 fixes folded in:
 * - **FM-4** — probe memoization: a memoized "hoster API unsupported" source
 *   skips the probe entirely (no call, no captured failure); a probe
 *   [AbstractMethodError] is memoized and NOT captured (deterministic
 *   "not implemented", not an error).
 * - **FM-5** — honest fallback failure: when `getVideoList(episode)` itself
 *   throws [AbstractMethodError] (hoster-API-only extension), the CAPTURED
 *   probe failure is rethrown (the real story), or an honest ISE when no
 *   probe failure was captured.
 * - **FM-3** — lazy `resolveVideo`: entries with a blank/"null" videoUrl get
 *   one bounded `source.resolveVideo(video)` chance before the caller's
 *   filter drops them (see [applyLazyResolveVideo]).
 */
internal suspend fun resolveAniyomiEntries(
    source: AnimeHttpSource,
    episode: SEpisode,
): List<VideoEntry> {
    var failure: CapturedFailure? = null

    val hosters: List<Hoster> = if (AniyomiHosterApiMemo.isUnsupported(source.id)) {
        // FM-4: known unsupported — skip the probe entirely (hosters stay
        // empty and NOTHING is captured: this is a routing decision, not a
        // failure).
        Logger.d(TAG) {
            "getHosterList known-unsupported for ${source.name} (memoized) — skipping probe"
        }
        emptyList()
    } else {
        try {
            withTimeoutOrNull(source.videoListTimeoutMs) {
                source.getHosterList(episode)
            } ?: run {
                // null = the budget expired — capture (don't throw: the fallback
                // getVideoList path below may still resolve).
                failure = CapturedFailure(
                    IllegalStateException("Resolution timed out after ${source.videoListTimeoutMs / 1000}s (getHosterList)"),
                    "getHosterList",
                )
                Logger.w(TAG) { "getHosterList timed out after ${source.videoListTimeoutMs}ms for ${source.name}" }
                emptyList()
            }
        } catch (e: IllegalStateException) {
            // The CloudStream bridge intentionally throws ISE("getHosterList not
            // supported…") for instant fallback — NOT a failure worth capturing.
            Logger.d(TAG) { "getHosterList not supported by ${source.name}, falling back to getVideoList" }
            emptyList()
        } catch (e: AbstractMethodError) {
            // FM-4: ext-lib ≤ 15 — the hoster API does not exist in this
            // extension's bytecode. Deterministic "not implemented", NOT an
            // error: memoize and skip the probe forever, capture nothing.
            AniyomiHosterApiMemo.markUnsupported(source.id)
            Logger.d(TAG) {
                "getHosterList not implemented (ext-lib ≤ 15) for ${source.name} — memoized, falling back to getVideoList"
            }
            emptyList()
        } catch (e: Throwable) {
            failure = failure ?: CapturedFailure(e, "getHosterList")
            Logger.w(TAG, e) { "getHosterList failed for ${source.name}: ${e.message}" }
            emptyList()
        }
    }

    if (hosters.isNotEmpty()) {
        Logger.i(TAG) { "Got ${hosters.size} hosters from ${source.name}" }
        val entries = mutableListOf<VideoEntry>()
        for (hoster in hosters) {
            val hosterVideos = hoster.videoList
            if (hosterVideos != null && hosterVideos.isNotEmpty()) {
                Logger.d(TAG) { "Hoster '${hoster.hosterName}' has ${hosterVideos.size} pre-loaded videos" }
                for (video in hosterVideos) {
                    entries.add(VideoEntry(video, hoster.hosterName))
                }
            } else {
                Logger.d(TAG) { "Hoster '${hoster.hosterName}' is lazy — calling getVideoList(hoster)" }
                try {
                    val resolved = withTimeoutOrNull(source.videoListTimeoutMs) {
                        source.getVideoList(hoster)
                    } ?: run {
                        failure = failure ?: CapturedFailure(
                            IllegalStateException("Hoster '${hoster.hosterName}' timed out after ${source.videoListTimeoutMs / 1000}s"),
                            "getVideoList(hoster)",
                        )
                        emptyList()
                    }
                    for (video in resolved) {
                        entries.add(VideoEntry(video, hoster.hosterName))
                    }
                } catch (e: Throwable) {
                    failure = failure ?: CapturedFailure(e, "getVideoList(hoster=${hoster.hosterName})")
                    Logger.w(TAG, e) { "getVideoList for hoster ${hoster.hosterName} failed: ${e.message}" }
                }
            }
        }
        if (entries.isEmpty()) {
            // Every hoster failed and nothing resolved — rethrow the first
            // captured failure so the user sees WHY (not "No videos available").
            failure?.let { throw it.throwable }
        }
        // FM-3: shared lazy-resolveVideo pass (hoster path).
        return applyLazyResolveVideo(source, entries)
    }

    // Fallback: old direct API (ext-lib < 16) — no hoster names available.
    Logger.d(TAG) { "Falling back to getVideoList(episode) for ${source.name}" }
    val videos = try {
        withTimeoutOrNull(source.videoListTimeoutMs) {
            source.getVideoList(episode)
        } ?: run {
            Logger.w(TAG) { "getVideoList(episode) timed out after ${source.videoListTimeoutMs}ms for ${source.name}" }
            throw IllegalStateException("Resolution timed out after ${source.videoListTimeoutMs / 1000}s")
        }
    } catch (e: AbstractMethodError) {
        // FM-5: the direct API isn't implemented either — this extension is
        // hoster-API-only. The probe failure (if one was captured) is the
        // real story (its timeout/error is why we're here); otherwise tell
        // the user what actually happened instead of a bare linkage error.
        Logger.d(TAG) { "getVideoList(episode) not implemented for ${source.name} (hoster-API-only extension)" }
        failure?.let { throw it.throwable }
        throw IllegalStateException(
            "This extension only supports the hoster-based API and its hoster list failed — try again or update the extension",
        )
    } catch (e: Throwable) {
        Logger.e(TAG, e) { "getVideoList(episode) failed for ${source.name}: ${e.message}" }
        throw e
    }
    // FM-3: shared lazy-resolveVideo pass (fallback path).
    return applyLazyResolveVideo(source, videos.map { VideoEntry(it, null) })
}

/**
 * Task 50 (round 10, Fix B / FM-3 — lazy resolveVideo): ONE shared pass over
 * the FINAL entry list (both the hoster path and the getVideoList(episode)
 * fallback feed through here). Entries whose `videoUrl` is blank or the
 * literal `"null"` (the deprecated [eu.kanade.tachiyomi.animesource.model.Video]
 * constructor maps a null videoUrl to `"null"`) get one bounded
 * [AnimeHttpSource.resolveVideo] chance — `resolveVideo` is open-with-body
 * (identity default) in AnimeHttpSource, so calling it on ANY source is safe
 * and sources that implement it can fill the URL lazily.
 *
 * A video whose URL still cannot be resolved is left as-is — the caller's
 * blank/"null" filter drops it. Per-video failures are logged and swallowed
 * (one dead lazy video never aborts its siblings).
 */
internal suspend fun applyLazyResolveVideo(
    source: AnimeHttpSource,
    entries: List<VideoEntry>,
): List<VideoEntry> {
    if (entries.all { it.video.videoUrl.isNotBlank() && it.video.videoUrl != "null" }) {
        return entries
    }
    return entries.map { entry ->
        val url = entry.video.videoUrl
        if (url.isNotBlank() && url != "null") return@map entry
        try {
            val resolved = withTimeoutOrNull(source.videoListTimeoutMs) {
                source.resolveVideo(entry.video)
            }
            if (resolved != null && resolved.videoUrl.isNotBlank() && resolved.videoUrl != "null") {
                Logger.i(TAG) {
                    "Lazy resolveVideo filled videoUrl for '${entry.video.videoTitle.take(40)}' (hoster=${entry.hosterName})"
                }
                entry.copy(video = resolved)
            } else {
                // Identity default / still blank — leave it; the caller's filter drops it.
                entry
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.w(TAG, e) {
                "Lazy resolveVideo failed for '${entry.video.videoTitle.take(40)}' — keeping placeholder"
            }
            entry
        }
    }
}
