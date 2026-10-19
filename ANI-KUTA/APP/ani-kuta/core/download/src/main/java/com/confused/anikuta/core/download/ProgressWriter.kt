package com.confused.anikuta.core.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch

/**
 * D-758 (round 123 — the download reliability round): the PROGRESS WRITER.
 *
 * ## Why this exists
 *
 * The queue used to create the progress channel + its single writer coroutine
 * inline, and close them ONLY on the success path:
 *
 * ```
 * val progressChannel = Channel<ProgressUpdate>(Channel.CONFLATED)
 * val dbWriter = scope.launch { progressChannel.consumeEach { ... } }
 * val completed = downloader.download(task) { ... }   // ← throws on failure
 * progressChannel.close()                              // ← never reached on failure
 * dbWriter.join()                                      // ← never reached on failure
 * ```
 *
 * So every FAILED attempt leaked a suspended `consumeEach` coroutine and an
 * open channel — and the retry loop created a new pair per attempt, so a
 * download that failed three times leaked three writers. The channel was
 * never closed, so its consumer stayed suspended forever on a channel nobody
 * would ever send to again.
 *
 * ## The contract now
 *
 * The writer OWNS the channel and the coroutine, and [close] is idempotent and
 * safe to call from a `finally` block: it closes the channel (the consumer
 * drains what is pending and ends) and joins the coroutine. Nothing else in
 * the app may touch the channel.
 *
 * The channel stays CONFLATED (only the newest progress matters) and the
 * writes stay throttled (one DB write per interval) — the behavior is
 * unchanged; only the LIFECYCLE is now correct.
 */
internal class ProgressWriter(
    scope: CoroutineScope,
    /** The minimum gap between two persisted writes. */
    private val intervalMs: Long,
    /** The sink — the DB write. Called at most once per [intervalMs]. */
    private val write: (progress: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
) {

    private val channel = Channel<Update>(Channel.CONFLATED)

    private val job: Job = scope.launch {
        var lastFlush = 0L
        channel.consumeEach { update ->
            val now = System.currentTimeMillis()
            if (now - lastFlush >= intervalMs) {
                write(update.progress, update.downloadedBytes, update.totalBytes)
                lastFlush = now
            }
        }
    }

    /**
     * Publish a progress update. Never blocks and never throws: when the
     * writer is already closed the update is simply dropped (there is nothing
     * left to write it to).
     */
    fun send(progress: Int, downloadedBytes: Long, totalBytes: Long) {
        channel.trySend(Update(progress, downloadedBytes, totalBytes))
    }

    /**
     * Close the channel and wait for the writer to finish. IDEMPOTENT — safe
     * in a `finally` block, and safe to call twice (the success path closes
     * explicitly to flush the last progress BEFORE the completion write, and
     * the `finally` closes again as the belt-and-braces guard).
     */
    suspend fun close() {
        channel.close()
        job.join()
    }

    /**
     * Whether the writer coroutine is still running. The leak lock: after
     * [close] this must be false — a running writer after a failed attempt is
     * exactly the leak this class exists to prevent.
     */
    internal val isRunning: Boolean
        get() = job.isActive

    private data class Update(
        val progress: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )
}
