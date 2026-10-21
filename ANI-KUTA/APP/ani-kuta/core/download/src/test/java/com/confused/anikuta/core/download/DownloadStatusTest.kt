package com.confused.anikuta.core.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStatusTest {

    @Test
    fun `waiting for network is pausable and resumable`() {
        assertTrue(DownloadStatus.WAITING_FOR_NETWORK.canPause)
        assertTrue(DownloadStatus.WAITING_FOR_NETWORK.canResume)
    }

    @Test
    fun `waiting for network does not consume a download slot`() {
        assertFalse(DownloadStatus.WAITING_FOR_NETWORK.isActive)
        assertFalse(DownloadStatus.WAITING_FOR_NETWORK.isTerminal)
    }

    @Test
    fun `active statuses still consume a download slot`() {
        assertTrue(DownloadStatus.DOWNLOADING.isActive)
        assertTrue(DownloadStatus.RETRYING.isActive)
    }

    @Test
    fun `terminal statuses cannot be resumed`() {
        assertTrue(DownloadStatus.COMPLETED.isTerminal)
        assertTrue(DownloadStatus.CANCELLED.isTerminal)
        assertFalse(DownloadStatus.COMPLETED.canResume)
        assertFalse(DownloadStatus.CANCELLED.canResume)
    }
}
