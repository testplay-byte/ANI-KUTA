package com.confused.anikuta.core.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-758 (round 123 — the download reliability round): the progress writer's
 * lifecycle locks.
 *
 * The leak this class exists to prevent: the old inline channel + writer were
 * closed ONLY on the success path, so every failed attempt left a suspended
 * `consumeEach` coroutine and an open channel behind — three failures, three
 * leaked writers.
 */
class ProgressWriterTest {

    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test
    fun `the writer runs until it is closed`() {
        val scope = scope()
        val writer = ProgressWriter(scope, intervalMs = 0L) { _, _, _ -> }
        try {
            assertTrue("the writer coroutine must be alive while it is open", writer.isRunning)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `closing the writer stops its coroutine`() = runBlocking {
        val scope = scope()
        try {
            val writer = ProgressWriter(scope, intervalMs = 0L) { _, _, _ -> }
            writer.close()
            assertFalse("a closed writer must not leave a running coroutine", writer.isRunning)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `closing twice is safe`() = runBlocking {
        // The success path closes explicitly (to flush before the completion
        // write) and the finally closes again — close MUST be idempotent.
        val scope = scope()
        try {
            val writer = ProgressWriter(scope, intervalMs = 0L) { _, _, _ -> }
            writer.close()
            writer.close()
            assertFalse(writer.isRunning)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `sending after the close does not throw`() = runBlocking {
        val scope = scope()
        try {
            val writer = ProgressWriter(scope, intervalMs = 0L) { _, _, _ -> }
            writer.close()
            // The download callback can fire once more after the failure that
            // closed the writer — that must not crash the attempt.
            writer.send(progress = 50, downloadedBytes = 100L, totalBytes = 200L)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the sink receives the progress`() = runBlocking {
        val scope = scope()
        try {
            val seen = mutableListOf<Triple<Int, Long, Long>>()
            val writer = ProgressWriter(scope, intervalMs = 0L) { p, d, t -> seen += Triple(p, d, t) }
            writer.send(progress = 42, downloadedBytes = 420L, totalBytes = 1000L)
            writer.close()
            assertTrue("the sink must have received the update", seen.isNotEmpty())
            assertEquals(42, seen.last().first)
            assertEquals(420L, seen.last().second)
            assertEquals(1000L, seen.last().third)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the writes are throttled by the interval`() = runBlocking {
        val scope = scope()
        try {
            var writes = 0
            // A huge interval: the FIRST write passes (the throttle compares
            // against 0), every later one is skipped.
            val writer = ProgressWriter(scope, intervalMs = 60_000L) { _, _, _ -> writes++ }
            writer.send(progress = 1, downloadedBytes = 1L, totalBytes = 10L)
            writer.send(progress = 2, downloadedBytes = 2L, totalBytes = 10L)
            writer.send(progress = 3, downloadedBytes = 3L, totalBytes = 10L)
            writer.close()
            assertTrue("the throttle must suppress the writes inside the interval", writes <= 1)
        } finally {
            scope.cancel()
        }
    }
}
