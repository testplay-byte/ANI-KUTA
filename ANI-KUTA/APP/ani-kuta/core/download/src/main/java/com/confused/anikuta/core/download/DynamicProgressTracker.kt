package com.confused.anikuta.core.download

/**
 * Smooth dynamic progress tracking.
 *
 * D.1.7 + REVIEW-5 M31/M34/M35/M38:
 * - Byte-count-based for ALL engines (including HLS — track total bytes across
 *   all segments, even if the total size is unknown).
 * - Moving average (window of 5 ticks) to smooth out network jitter.
 * - Cap at 95% during download (not 90% — the user complained about 90→100 jumps).
 * - Emit intermediate 96/97/98/99% ticks during validation/subtitles/metadata/publish
 *   (M35 — so the bar doesn't jump 95→100).
 * - [recentRatios] is persisted across pause/resume (M38).
 *
 * The [compute] function is pure — takes the current state + returns the smoothed
 * progress. The caller (DownloadQueue) maintains the [recentRatios] ArrayDeque
 * and passes it in.
 */
object DynamicProgressTracker {

    /** The moving-average window size. */
    const val WINDOW_SIZE = 5

    /** Cap during active download (the bar never shows >95% until completion). */
    const val ACTIVE_CAP = 95

    /**
     * Computes the smoothed progress.
     *
     * @param downloadedBytes Bytes downloaded so far.
     * @param totalBytes Total bytes (-1 = unknown).
     * @param prevTotal The previous total bytes estimate (for HLS refinement).
     * @param prevEstimate The previous smoothed estimate (for continuity).
     * @param recentRatios The moving-average window (ArrayDeque<Float>, max 5).
     *   Updated IN PLACE by this function (the caller persists it).
     * @return The smoothed progress (0..100).
     */
    fun compute(
        downloadedBytes: Long,
        totalBytes: Long,
        prevTotal: Long,
        prevEstimate: Int,
        recentRatios: ArrayDeque<Float>,
    ): Int {
        val rawRatio: Float = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            // Unknown total — use the "10MB ahead" strategy.
            // The ratio grows as downloadedBytes approaches 10MB, then plateaus.
            val estimate = 10L * 1024 * 1024 // 10MB
            (downloadedBytes.toFloat() / estimate.toFloat()).coerceIn(0f, 0.9f)
        }

        // Update the moving-average window.
        recentRatios.addLast(rawRatio)
        while (recentRatios.size > WINDOW_SIZE) {
            recentRatios.removeFirst()
        }

        // Smoothed ratio = average of the window.
        val smoothedRatio = recentRatios.average().toFloat()

        // Convert to percentage, capped at ACTIVE_CAP during download.
        val rawProgress = (smoothedRatio * 100).toInt()
        val cappedProgress = rawProgress.coerceAtMost(ACTIVE_CAP)

        // Ensure progress never goes backward (unless the user seeks back).
        return maxOf(cappedProgress, prevEstimate).coerceIn(0, ACTIVE_CAP)
    }

    /**
     * The progress to show when the download is in the validation/subtitle/
     * metadata/publish phase (after the main download completes but before
     * the task is marked COMPLETED).
     *
     * M35: emits intermediate ticks (96/97/98/99) so the bar doesn't jump 95→100.
     *
     * @param phase 0 = validation, 1 = subtitles, 2 = metadata, 3 = publish.
     */
    fun postDownloadProgress(phase: Int): Int = when (phase) {
        0 -> 96  // validation
        1 -> 97  // subtitles
        2 -> 98  // metadata
        3 -> 99  // publish
        else -> 99
    }

    /**
     * The progress to show when the download is COMPLETE.
     * Uses [complete] instead of hardcoding 100 for clarity.
     */
    fun complete(): Int = 100
}
