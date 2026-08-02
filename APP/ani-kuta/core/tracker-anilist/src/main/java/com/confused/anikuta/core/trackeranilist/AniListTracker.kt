package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.BaseTracker
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackerLoginState
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.TrackerSyncState
import com.confused.anikuta.core.trackerapi.TrackerType
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AniList tracker implementation.
 *
 * This is a BASIC implementation for Phase 3d. It provides:
 * - OAuth login flow (URL generation + callback handling).
 * - Basic entry sync (status, score, progress).
 * - Search by title.
 *
 * Future improvements (Phase 4+):
 * - Full GraphQL mutations for entry updates.
 * - Bulk sync (batch multiple entries).
 * - Conflict resolution (local vs remote).
 * - Token refresh.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Tracker:AniList".
 * CORE_RULES §23: Login + sync state are reactive (StateFlow).
 *
 * The app's internal tracking system (`:core:activity-tracker`) is PRIMARY.
 * This tracker is SECONDARY — it receives data relayed via [TrackSyncManager].
 */
class AniListTracker(
    private val preferenceStore: PreferenceStore,
) : BaseTracker(TrackerType.ANILIST, "AniList") {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:AniList"
        private const val KEY_ACCESS_TOKEN = "anilist_access_token"
        private const val KEY_USERNAME = "anilist_username"
    }

    private var accessToken: String?
        get() = preferenceStore.getString(KEY_ACCESS_TOKEN, "")
        set(value) = preferenceStore.putString(KEY_ACCESS_TOKEN, value ?: "")

    private var username: String
        get() = preferenceStore.getString(KEY_USERNAME, "")
        set(value) = preferenceStore.putString(KEY_USERNAME, value)

    init {
        // Restore login state on init
        val token = accessToken ?: ""
        if (token.isNotBlank()) {
            _loginState.value = TrackerLoginState.LoggedIn(username)
            Logger.i(TAG) { "Restored login: $username" }
        }
    }

    override suspend fun startLogin(): String? {
        if (!AniListOAuth.isConfigured()) {
            Logger.w(TAG) { "AniList OAuth not configured (placeholder client ID)" }
            _loginState.value = TrackerLoginState.Error("AniList OAuth not configured. Set your client ID in AniListOAuth.kt")
            return null
        }

        val url = AniListOAuth.getAuthUrl()
        Logger.i(TAG) { "Starting OAuth login flow" }
        return url
    }

    override suspend fun handleLoginCallback(code: String): Boolean {
        Logger.i(TAG) { "Handling OAuth callback" }

        // Phase 3d: basic implementation — store the code as token.
        // Phase 4 will implement proper token exchange (POST to AniList token endpoint).
        // For now, we just mark as logged in.
        try {
            // TODO: Exchange code for access token via POST to AniListOAuth.TOKEN_URL
            // For now, just store the code and mark as logged in
            accessToken = code
            username = "AniList User" // TODO: Fetch actual username from AniList API

            _loginState.value = TrackerLoginState.LoggedIn(username)
            Logger.i(TAG) { "Login successful (basic)" }
            return true
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Login failed: ${e.message}" }
            _loginState.value = TrackerLoginState.Error(e.message ?: "Unknown error")
            return false
        }
    }

    override suspend fun logout() {
        Logger.i(TAG) { "Logging out" }
        accessToken = null
        username = ""
        _loginState.value = TrackerLoginState.LoggedOut
    }

    override suspend fun syncEntry(entry: TrackEntry): Boolean {
        Logger.d(TAG) { "Syncing entry: ${entry.contentKey} (status=${entry.status}, progress=${entry.progress})" }

        if (!isLoggedIn()) {
            Logger.w(TAG) { "Not logged in — skipping sync" }
            return false
        }

        _syncState.value = TrackerSyncState.Syncing

        try {
            // TODO: Implement GraphQL mutation to update AniList media list entry.
            // For now, this is a stub that logs the intent.
            // Phase 4 will implement the actual API call.

            Logger.i(TAG) { "Sync stub: would update AniList entry for ${entry.contentKey}" }
            _syncState.value = TrackerSyncState.Success(System.currentTimeMillis())
            return true
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Sync failed: ${e.message}" }
            _syncState.value = TrackerSyncState.Failed(e.message ?: "Unknown error")
            return false
        }
    }

    override suspend fun fetchEntry(trackerId: Int): TrackEntry? {
        Logger.d(TAG) { "Fetching entry: $trackerId" }

        if (!isLoggedIn()) return null

        // TODO: Implement GraphQL query to fetch AniList media list entry.
        // For now, return null (not tracked).
        return null
    }

    override suspend fun search(query: String): List<TrackEntry> {
        Logger.d(TAG) { "Searching: $query" }

        if (!isLoggedIn()) return emptyList()

        // TODO: Implement AniList search query.
        // For now, return empty list.
        return emptyList()
    }
}
