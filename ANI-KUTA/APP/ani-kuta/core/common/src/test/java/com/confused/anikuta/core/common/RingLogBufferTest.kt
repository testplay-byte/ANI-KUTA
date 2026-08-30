// Task 49 (console logging tool): pins the release-available ring buffer —
// capacity eviction, ordering, clear, and string capping. Pure JVM (no android
// framework touched by append/snapshot).
package com.confused.anikuta.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingLogBufferTest {

    @Test
    fun `append and snapshot preserve order`() {
        RingLogBuffer.clear()
        RingLogBuffer.append(LogLevel.INFO, "T1", "first", null)
        RingLogBuffer.append(LogLevel.WARN, "T2", "second", null)
        val snapshot = RingLogBuffer.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals("first", snapshot[0].message)
        assertEquals("second", snapshot[1].message)
        assertEquals(LogLevel.WARN, snapshot[1].level)
        RingLogBuffer.clear()
    }

    @Test
    fun `capacity evicts the oldest entries`() {
        RingLogBuffer.clear()
        repeat(RingLogBuffer.CAPACITY + 250) { i ->
            RingLogBuffer.append(LogLevel.DEBUG, "T", "message-$i", null)
        }
        val snapshot = RingLogBuffer.snapshot()
        assertEquals(RingLogBuffer.CAPACITY, snapshot.size)
        assertEquals("message-250", snapshot.first().message)
        assertEquals("message-${RingLogBuffer.CAPACITY + 249}", snapshot.last().message)
        RingLogBuffer.clear()
    }

    @Test
    fun `message and throwable strings are capped`() {
        RingLogBuffer.clear()
        val huge = "x".repeat(50_000)
        RingLogBuffer.append(LogLevel.ERROR, "T", huge, IllegalStateException(huge))
        val entry = RingLogBuffer.snapshot().single()
        assertTrue(entry.message.length <= 4_000)
        assertTrue((entry.throwableString ?: "").length <= 2_000)
        RingLogBuffer.clear()
    }

    @Test
    fun `clear empties the buffer`() {
        RingLogBuffer.append(LogLevel.INFO, "T", "x", null)
        RingLogBuffer.clear()
        assertEquals(0, RingLogBuffer.size())
        assertTrue(RingLogBuffer.snapshot().isEmpty())
    }
}
