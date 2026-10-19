package com.confused.anikuta.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the D-747 contract: the registry only answers "does the service need to
 * stay alive for an auto-resume?", and the snapshot hand-off is atomic + clears
 * the markers exactly once.
 */
class NetworkPauseRegistryTest {

    @Test
    fun `add and remove track user intent`() {
        val registry = NetworkPauseRegistry()

        assertTrue(registry.isEmpty())

        registry.add(7L)
        registry.add(9L)
        assertFalse(registry.isEmpty())

        registry.remove(7L)
        assertFalse(registry.isEmpty())

        registry.remove(9L)
        assertTrue(registry.isEmpty())
    }

    @Test
    fun `duplicate adds do not duplicate the snapshot`() {
        val registry = NetworkPauseRegistry()

        registry.add(3L)
        registry.add(3L)

        assertEquals(listOf(3L), registry.snapshotAndClear())
        assertTrue(registry.isEmpty())
    }

    @Test
    fun `snapshot clears the registry for the next network event`() {
        val registry = NetworkPauseRegistry()
        registry.add(1L)
        registry.add(2L)

        val first = registry.snapshotAndClear()
        assertEquals(listOf(1L, 2L), first)
        assertTrue(registry.isEmpty())
        assertTrue(registry.snapshotAndClear().isEmpty())
    }

    @Test
    fun `clear drops every marker`() {
        val registry = NetworkPauseRegistry()
        registry.add(4L)
        registry.add(5L)

        registry.clear()

        assertTrue(registry.isEmpty())
    }
}
