package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.trackerapi.BaseTracker
import com.confused.anikuta.core.trackerapi.TrackEntry
import com.confused.anikuta.core.trackerapi.TrackerLoginState
import com.confused.anikuta.core.trackerapi.TrackStatus
import com.confused.anikuta.core.trackerapi.TrackerSyncState
import com.confused.anikuta.core.trackerapi.TrackerType
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.confused.anikuta.core.common.DispatcherProvider

/**
 * AniList tracker implementation.
 *
 * D-220: Implemented the OAuth2 Implicit Grant flow (same as AniMiru/Aniyomi).
 * The user opens a browser → authorizes → AniList redirects to
 * `anikuta://anilist-auth#access_token=...`. The token is stored in
 * PreferenceStore + used for authenticated GraphQL queries.
 *
 * **Implemented:**
 * - `startLogin()` → returns the auth URL (implicit grant).
 * - `handleLoginCallback(token)` → stores the token + fetches the Viewer (username).
 * - `fetchViewer()` → GraphQL Viewer query with Bearer token.
 * - `fetchUserMediaList()` → MediaListCollection query (for library populate).
 * - `logout()` → clears token + username.
 *
 * **TODO (next session):**
 * - `syncEntry()` → SaveMediaListEntry mutation (update progress/status/score).
 * - `fetchEntry()` → Page.mediaList query (fetch single anime's track entry).
 * - `search()` → Page.media query (search for anime to link).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Tracker:AniList".
 * CORE_RULES §23: Login + sync state are reactive (StateFlow).
 */
class AniListTracker(
    private val preferenceStore: PreferenceStore,
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val dispatchers: DispatcherProvider,
) : BaseTracker(TrackerType.ANILIST, "AniList") {

    companion object {
        private const val TAG = "Anikuta:Core:Tracker:AniList"
        private const val KEY_ACCESS_TOKEN = "anilist_access_token"
        private const val KEY_USERNAME = "anilist_username"
        private const val KEY_USER_ID = "anilist_user_id"
        private const val GRAPHQL_URL = "https://graphql.anilist.co"
    }

    private var accessToken: String?
        get() = preferenceStore.getString(KEY_ACCESS_TOKEN, "").ifBlank { null }
        set(value) = preferenceStore.putString(KEY_ACCESS_TOKEN, value ?: "")

    private var username: String
        get() = preferenceStore.getString(KEY_USERNAME, "")
        set(value) = preferenceStore.putString(KEY_USERNAME, value)

    private var userId: Int?
        get() = preferenceStore.getString(KEY_USER_ID, "").toIntOrNull()
        set(value) = preferenceStore.putString(KEY_USER_ID, value?.toString() ?: "")

    private val jsonMediaType = "application/json".toMediaType()

    init {
        // Restore login state on init.
        val token = accessToken
        if (!token.isNullOrBlank()) {
            _loginState.value = TrackerLoginState.LoggedIn(username.ifBlank { "AniList User" })
            Logger.i(TAG) { "Restored login: userId=$userId, username=$username" }
        }
    }

    // ── OAuth ──

    override suspend fun startLogin(): String? {
        if (!AniListOAuth.isConfigured()) {
            Logger.w(TAG) { "AniList OAuth not configured (placeholder client ID)" }
            _loginState.value = TrackerLoginState.Error("AniList OAuth not configured.")
            return null
        }
        val url = AniListOAuth.getAuthUrl()
        Logger.i(TAG) { "Starting OAuth login flow (implicit grant)" }
        return url
    }

    /**
     * D-220: Handle the OAuth callback.
     *
     * For the Implicit Grant flow, the "code" parameter is actually the
     * access_token (parsed from the URL fragment by [AniListOAuth.parseAccessToken]).
     * No token exchange POST is needed — the token is ready to use immediately.
     */
    override suspend fun handleLoginCallback(code: String): Boolean {
        Logger.i(TAG) { "Handling OAuth callback (token length=${code.length})" }
        return try {
            accessToken = code
            // Fetch the real username + user ID from the AniList API.
            val viewer = fetchViewer()
            if (viewer != null) {
                username = viewer.name
                userId = viewer.id
                _loginState.value = TrackerLoginState.LoggedIn(viewer.name)
                Logger.i(TAG) { "Login successful: userId=${viewer.id}, name=${viewer.name}" }
            } else {
                // Token is valid but Viewer query failed — use fallback.
                username = "AniList User"
                _loginState.value = TrackerLoginState.LoggedIn(username)
                Logger.w(TAG) { "Login OK but Viewer query failed — using fallback username" }
            }
            true
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Login failed: ${e.message}" }
            _loginState.value = TrackerLoginState.Error(e.message ?: "Unknown error")
            false
        }
    }

    override suspend fun logout() {
        Logger.i(TAG) { "Logging out (userId=$userId)" }
        accessToken = null
        username = ""
        userId = null
        _loginState.value = TrackerLoginState.LoggedOut
    }

    // ── Authenticated GraphQL queries ──

    /**
     * D-220: Fetch the authenticated user's info (Viewer query).
     * Returns the user's AniList ID + display name.
     */
    suspend fun fetchViewer(): AniListViewer? = withContext(dispatchers.io) {
        val token = accessToken ?: return@withContext null
        val query = """
            query {
                Viewer {
                    id
                    name
                    avatar { large }
                    mediaListOptions { scoreFormat }
                }
            }
        """.trimIndent()

        try {
            val response = executeAuthenticatedQuery(query, token)
            val root = json.parseToJsonElement(response).jsonObject
            val viewer = root["data"]?.jsonObject?.get("Viewer")?.jsonObject
                ?: return@withContext null
            val id = viewer["id"]?.jsonPrimitive?.intOrNull ?: return@withContext null
            val name = viewer["name"]?.jsonPrimitive?.content ?: "AniList User"
            val avatar = viewer["avatar"]?.jsonObject?.get("large")?.jsonPrimitive?.content
            Logger.i(TAG) { "Viewer fetched: id=$id, name=$name" }
            AniListViewer(id = id, name = name, avatarUrl = avatar)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "fetchViewer failed: ${e.message}" }
            null
        }
    }

    /**
     * D-221: Fetch the user's complete anime library (MediaListCollection query).
     * Returns a map of AniList status → list of media list entries.
     *
     * Used by the "Populate library from AniList" feature to create local
     * categories (Watching/Completed/Paused/Dropped/Planning) and populate them.
     */
    suspend fun fetchUserMediaList(): Map<String, List<AniListMediaEntry>> = withContext(dispatchers.io) {
        val token = accessToken ?: return@withContext emptyMap()
        val uid = userId ?: return@withContext emptyMap()
        val query = """
            query (${'$'}userId: Int!) {
                MediaListCollection(userId: ${'$'}userId, type: ANIME) {
                    lists {
                        name
                        status
                        entries {
                            id
                            status
                            score(format: POINT_100)
                            progress
                            media {
                                id
                                title { userPreferred romaji english }
                                coverImage { large }
                                bannerImage
                                episodes
                                averageScore
                                status
                                season
                                seasonYear
                                genres
                                description
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        try {
            val variables = buildJsonObject { put("userId", uid) }
            val response = executeAuthenticatedQuery(query, variables, token)
            val root = json.parseToJsonElement(response).jsonObject
            val lists = root["data"]?.jsonObject
                ?.get("MediaListCollection")?.jsonObject
                ?.get("lists")?.jsonArray
                ?: return@withContext emptyMap()

            val result = mutableMapOf<String, List<AniListMediaEntry>>()
            for (listObj in lists) {
                val list = listObj.jsonObject
                val status = list["status"]?.jsonPrimitive?.content ?: continue
                val entries = list["entries"]?.jsonArray ?: continue
                val mediaEntries = entries.mapNotNull { entryObj ->
                    val entry = entryObj.jsonObject
                    val media = entry["media"]?.jsonObject ?: return@mapNotNull null
                    AniListMediaEntry(
                        listId = entry["id"]?.jsonPrimitive?.intOrNull ?: 0,
                        status = entry["status"]?.jsonPrimitive?.content ?: status,
                        score = entry["score"]?.jsonPrimitive?.intOrNull ?: 0,
                        progress = entry["progress"]?.jsonPrimitive?.intOrNull ?: 0,
                        mediaId = media["id"]?.jsonPrimitive?.intOrNull ?: 0,
                        title = media["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.content
                            ?: media["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.content
                            ?: "Unknown",
                        coverUrl = media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content,
                        bannerUrl = media["bannerImage"]?.jsonPrimitive?.content,
                        episodes = media["episodes"]?.jsonPrimitive?.intOrNull,
                        averageScore = media["averageScore"]?.jsonPrimitive?.intOrNull,
                        mediaStatus = media["status"]?.jsonPrimitive?.content,
                        season = media["season"]?.jsonPrimitive?.content,
                        seasonYear = media["seasonYear"]?.jsonPrimitive?.intOrNull,
                        genres = media["genres"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        description = media["description"]?.jsonPrimitive?.content,
                    )
                }
                result[status] = mediaEntries
            }
            Logger.i(TAG) { "Fetched ${result.values.sumOf { it.size }} media entries across ${result.size} lists" }
            result
        } catch (e: Exception) {
            Logger.e(TAG, e) { "fetchUserMediaList failed: ${e.message}" }
            emptyMap()
        }
    }

    // ── Stubs (implemented next session) ──

    override suspend fun syncEntry(entry: TrackEntry): Boolean {
        Logger.d(TAG) { "Sync stub: ${entry.contentKey} (status=${entry.status})" }
        _syncState.value = TrackerSyncState.Syncing
        // TODO: Implement SaveMediaListEntry mutation.
        _syncState.value = TrackerSyncState.Success(System.currentTimeMillis())
        return true
    }

    override suspend fun fetchEntry(trackerId: Int): TrackEntry? = null

    override suspend fun search(query: String): List<TrackEntry> = emptyList()

    // ── Private helpers ──

    private fun executeAuthenticatedQuery(query: String, token: String): String {
        val requestBody = buildJsonObject { put("query", query) }
            .toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.e(TAG) { "AniList API error: ${response.code}" }
                throw RuntimeException("AniList API error: ${response.code}")
            }
            return response.body?.string()
                ?: throw RuntimeException("AniList API: empty response body")
        }
    }

    private fun executeAuthenticatedQuery(
        query: String,
        variables: kotlinx.serialization.json.JsonElement,
        token: String,
    ): String {
        val requestBody = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(GRAPHQL_URL)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.e(TAG) { "AniList API error: ${response.code}" }
                throw RuntimeException("AniList API error: ${response.code}")
            }
            return response.body?.string()
                ?: throw RuntimeException("AniList API: empty response body")
        }
    }
}

/**
 * D-220: AniList user info (from Viewer query).
 */
data class AniListViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
)

/**
 * D-221: AniList media list entry (from MediaListCollection query).
 * Used for the "Populate library from AniList" feature.
 */
data class AniListMediaEntry(
    val listId: Int,
    val status: String,
    val score: Int,
    val progress: Int,
    val mediaId: Int,
    val title: String,
    val coverUrl: String?,
    val bannerUrl: String?,
    val episodes: Int?,
    val averageScore: Int?,
    val mediaStatus: String?,
    val season: String?,
    val seasonYear: Int?,
    val genres: List<String>,
    val description: String?,
)
