package com.confused.anikuta.core.download

import java.io.File

/**
 * The pluggable "bytes → temp File" stage of the download pipeline
 * (test-feature/video-cache-new-download — Parallel Download Engine, PLAN.md Part B).
 *
 * [HttpDownloader] remains the FACADE: routing, validation, subtitles, publish,
 * `.data.json` upsert + completion shape all stay there (D-241/D-242 logic must
 * not be forked). Only the byte-transfer strategy is pluggable:
 *  - [SingleConnectionFetcher] — today's downloadNormal, extracted verbatim.
 *  - [ParallelHttpFetcher] — the new multi-connection Range engine (parallel
 *    byte-range workers, per-chunk exponential backoff, stall watchdog, active-call
 *    registry, re-resolve on localhost failures, chunk sidecar for resume).
 *
 * HLS is NOT routed through fetchers — [HlsDownloader] gains its own parallel mode
 * (concurrent segment workers + in-memory AES-128 decryption) gated by the same
 * `advancedDownloader` preference.
 *
 * THREAD-SAFETY CONTRACT: [onProgress] is invoked from the fetcher's own context
 * ONLY (DownloadQueue's progress lambda mutates non-thread-safe state — ArrayDeque,
 * captured vars — so parallel engines MUST serialize progress emission through a
 * single reporter).
 */
interface VideoFetcher {

    /**
     * Fetches [url]'s bytes into [tempFile] (creating/overwriting it).
     *
     * @param headers MPV comma-format header string ("Key: Value,Key2: Value2").
     * @param tempFile The seekable temp file (java.io.File in the task's temp dir).
     * @param taskId For DB updates (re-resolve writes the fresh URL via
     *   `store.updateDownloadVideoUrl`).
     * @param resolveContextJson The proxy-churn re-resolve context (null = the
     *   re-resolve path is disabled).
     * @param onProgress `(downloadedBytes, totalBytes)` — totalBytes = -1 if unknown.
     * @return The total bytes of the completed file.
     */
    suspend fun fetch(
        url: String,
        headers: String?,
        tempFile: File,
        taskId: Long,
        resolveContextJson: String?,
        onProgress: (Long, Long) -> Unit,
    ): Long
}
