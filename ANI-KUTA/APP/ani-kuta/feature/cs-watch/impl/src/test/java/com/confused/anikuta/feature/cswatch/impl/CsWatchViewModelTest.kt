package com.confused.anikuta.feature.cswatch.impl

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks for the CS watch ViewModel's pure helpers (R12-REVIEW: the key format
 * + unit conversions had no test coverage).
 *
 * The episode-key format MUST stay byte-identical to feature:watch's builder
 * — watch progress, history and resume all key on it.
 */
class CsWatchViewModelTest {

    @Test
    fun `episode key matches the aniyomi padded format`() {
        assertEquals("main-uuid|00012", CsWatchViewModel.episodeKey("main-uuid", 12f))
        assertEquals("main-uuid|00103", CsWatchViewModel.episodeKey("main-uuid", 103f))
    }

    @Test
    fun `fractional episode numbers truncate to the int part`() {
        assertEquals("m|00005", CsWatchViewModel.episodeKey("m", 5.5f))
    }

    @Test
    fun `blank main id falls back to the unknown prefix`() {
        assertEquals("unknown|00007", CsWatchViewModel.episodeKey("", 7f))
    }
}
