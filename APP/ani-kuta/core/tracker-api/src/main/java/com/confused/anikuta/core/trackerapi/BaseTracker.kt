package com.confused.anikuta.core.trackerapi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base class for [Tracker] implementations.
 *
 * Provides reactive login + sync state (CORE_RULES §23).
 * Implementations extend this and implement the abstract methods.
 */
abstract class BaseTracker(
    override val type: TrackerType,
    override val displayName: String,
) : Tracker {

    protected val _loginState = MutableStateFlow<TrackerLoginState>(TrackerLoginState.LoggedOut)
    override fun observeLoginState(): StateFlow<TrackerLoginState> = _loginState.asStateFlow()

    protected val _syncState = MutableStateFlow<TrackerSyncState>(TrackerSyncState.Idle)
    override fun observeSyncState(): StateFlow<TrackerSyncState> = _syncState.asStateFlow()

    override suspend fun isLoggedIn(): Boolean {
        return _loginState.value is TrackerLoginState.LoggedIn
    }
}
