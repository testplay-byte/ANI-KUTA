package com.confused.anikuta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-759 (round 124 — the navigation reliability round): the pending-request
 * identity locks.
 *
 * Two failures lived in the old one-slot StateFlow:
 *  - two IDENTICAL deliveries compared equal, so the StateFlow never emitted
 *    the second one and the tap was swallowed;
 *  - a delivery that arrived while the previous one was being handled was
 *    cleared by the previous handler's `consume()`.
 */
class PendingRequestMailboxTest {

    @Test
    fun `an offer produces a pending entry`() {
        val mailbox = PendingRequestMailbox<String>()
        val entry = mailbox.offer("main-1")
        assertEquals("main-1", mailbox.pending?.payload)
        assertEquals(entry.id, mailbox.pending?.id)
    }

    @Test
    fun `two identical payloads get different ids`() {
        // The conflation lock: the StateFlow only emits on a CHANGE, so two
        // identical deliveries MUST differ — otherwise the second tap is lost.
        val mailbox = PendingRequestMailbox<String>()
        val first = mailbox.offer("main-1")
        val second = mailbox.offer("main-1")
        assertNotEquals(first.id, second.id)
        assertEquals(second.id, mailbox.pending?.id)
    }

    @Test
    fun `consuming the handled request clears it`() {
        val mailbox = PendingRequestMailbox<String>()
        val entry = mailbox.offer("main-1")
        assertTrue(mailbox.consume(entry.id))
        assertNull(mailbox.pending)
    }

    @Test
    fun `a late handler cannot clear a newer request`() {
        // The clear-race lock: request 1 is being handled when request 2
        // arrives. The handler for request 1 finishes and consumes — request 2
        // must SURVIVE.
        val mailbox = PendingRequestMailbox<String>()
        val first = mailbox.offer("main-1")
        val second = mailbox.offer("main-2")
        assertFalse("the stale handler must not clear the newer request", mailbox.consume(first.id))
        assertEquals(second.id, mailbox.pending?.id)
        assertEquals("main-2", mailbox.pending?.payload)
    }

    @Test
    fun `consuming an unknown id changes nothing`() {
        val mailbox = PendingRequestMailbox<String>()
        mailbox.offer("main-1")
        assertFalse(mailbox.consume(999L))
        assertEquals("main-1", mailbox.pending?.payload)
    }

    @Test
    fun `consuming twice is safe`() {
        val mailbox = PendingRequestMailbox<String>()
        val entry = mailbox.offer("main-1")
        assertTrue(mailbox.consume(entry.id))
        assertFalse(mailbox.consume(entry.id))
        assertNull(mailbox.pending)
    }

    @Test
    fun `the ids increase monotonically`() {
        val mailbox = PendingRequestMailbox<String>()
        val ids = (1..5).map { mailbox.offer("payload-$it").id }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.distinct().size, ids.size)
    }
}
