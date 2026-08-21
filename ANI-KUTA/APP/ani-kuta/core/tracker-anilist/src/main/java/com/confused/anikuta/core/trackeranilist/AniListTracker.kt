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
import kotlinx.serialization.json.longOrNull
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
                // D-242-fix: MERGE instead of overwrite. AniList's MediaListCollection
                // can return multiple MediaListGroup objects with the SAME status
                // (e.g. when "Split Completed List" is enabled, or when lists exceed
                // ~5000 entries and are auto-chunked). The old code overwrote, keeping
                // only the last chunk — now we merge all chunks for the same status.
                result[status] = result[status].orEmpty() + mediaEntries
            }
            Logger.i(TAG) { "Fetched ${result.values.sumOf { it.size }} media entries across ${result.size} lists" }
            result
        } catch (e: Exception) {
            Logger.e(TAG, e) { "fetchUserMediaList failed: ${e.message}" }
            emptyMap()
        }
    }

    // ── D-242: Implemented syncEntry / fetchEntry / search ──

    /**
     * Pushes [entry] to AniList via the `SaveMediaListEntry` mutation.
     *
     * Creates OR updates the MediaList entry for `entry.trackerId` (the AniList
     * anime ID). AniList upserts by `(userId, mediaId)` — if no entry exists,
     * one is created; if one exists, it's updated.
     *
     * Returns `true` on success, `false` on failure (not logged in, network
     * error, API error). The [TrackEntryRepository] cache is NOT updated here —
     * the caller is responsible for calling `repository.upsert(entry)` after
     * a successful sync.
     */
    override suspend fun syncEntry(entry: TrackEntry): Boolean = withContext(dispatchers.io) {
        val token = accessToken ?: run {
            _syncState.value = TrackerSyncState.Failed("Not logged in")
            Logger.w(TAG) { "syncEntry — not logged in; aborting" }
            return@withContext false
        }
        _syncState.value = TrackerSyncState.Syncing
        try {
            val query = """
                mutation (
                    ${'$'}mediaId: Int!,
                    ${'$'}status: MediaListStatus,
                    ${'$'}scoreRaw: Int,
                    ${'$'}progress: Int,
                    ${'$'}startedAt: FuzzyDateInput,
                    ${'$'}completedAt: FuzzyDateInput
                ) {
                    SaveMediaListEntry(
                        mediaId: ${'$'}mediaId,
                        status: ${'$'}status,
                        scoreRaw: ${'$'}scoreRaw,
                        progress: ${'$'}progress,
                        startedAt: ${'$'}startedAt,
                        completedAt: ${'$'}completedAt
                    ) { id status progress score }
                }
            """.trimIndent()
            val variables = buildJsonObject {
                put("mediaId", entry.trackerId)
                put("status", AniListStatusMapper.toAniList(entry.status))
                put("scoreRaw", entry.score ?: 0)
                put("progress", entry.progress)
                put("startedAt", epochToFuzzyDate(entry.startedAt))
                put("completedAt", epochToFuzzyDate(entry.completedAt))
            }
            val response = executeAuthenticatedQuery(query, variables, token)
            val root = json.parseToJsonElement(response).jsonObject
            val data = root["data"]?.jsonObject
            val errors = root["errors"]?.jsonArray
            if (errors != null && errors.isNotEmpty()) {
                val msg = errors.first().jsonObject["message"]?.jsonPrimitive?.content ?: "Unknown API error"
                Logger.e(TAG) { "syncEntry — API error: $msg" }
                _syncState.value = TrackerSyncState.Failed(msg)
                return@withContext false
            }
            Logger.i(TAG) {
                "syncEntry — OK: mediaId=${entry.trackerId}, status=${entry.status}, " +
                    "progress=${entry.progress}, score=${entry.score}"
            }
            _syncState.value = TrackerSyncState.Success(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            Logger.e(TAG, e) { "syncEntry failed: ${e.message}" }
            _syncState.value = TrackerSyncState.Failed(e.message ?: "Unknown error")
            false
        }
    }

    /**
     * Fetches the user's MediaList entry for [trackerId] (the AniList anime ID).
     *
     * Returns the [TrackEntry] (with `listId` populated for future updates), or
     * `null` if the user has no entry for this anime (not in their list), or if
     * not logged in, or on network error.
     */
    override suspend fun fetchEntry(trackerId: Int): TrackEntry? = withContext(dispatchers.io) {
        val token = accessToken ?: return@withContext null
        val uid = userId ?: return@withContext null
        try {
            val query = """
                query (${'$'}userId: Int!, ${'$'}mediaId: Int!) {
                    MediaList(userId: ${'$'}userId, mediaId: ${'$'}mediaId) {
                        id status score(format: POINT_100) progress
                        startedAt { year month day }
                        completedAt { year month day }
                        updatedAt
                    }
                }
            """.trimIndent()
            val variables = buildJsonObject {
                put("userId", uid)
                put("mediaId", trackerId)
            }
            val response = executeAuthenticatedQuery(query, variables, token)
            val root = json.parseToJsonElement(response).jsonObject
            val ml = root["data"]?.jsonObject?.get("MediaList")?.jsonObject
                ?: return@withContext null
            val status = ml["status"]?.jsonPrimitive?.contentOrNull()
            val entry = TrackEntry(
                contentKey = "",  // caller fills in
                trackerId = trackerId,
                status = AniListStatusMapper.fromAniList(status),
                score = ml["score"]?.jsonPrimitive?.intOrNull,
                progress = ml["progress"]?.jsonPrimitive?.intOrNull ?: 0,
                listId = ml["id"]?.jsonPrimitive?.intOrNull,
                startedAt = fuzzyDateToEpoch(ml["startedAt"]?.jsonObject),
                completedAt = fuzzyDateToEpoch(ml["completedAt"]?.jsonObject),
                updatedAt = (ml["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L) * 1000,
            )
            Logger.i(TAG) {
                "fetchEntry — OK: mediaId=$trackerId, status=${entry.status}, " +
                    "progress=${entry.progress}, score=${entry.score}"
            }
            entry
        } catch (e: Exception) {
            Logger.e(TAG, e) { "fetchEntry failed: ${e.message}" }
            null
        }
    }

    /**
     * Searches AniList for anime by title. Returns a list of [TrackEntry]s
     * with `trackerId` + `totalEpisodes` populated (for the manual-link flow).
     *
     * Uses the PUBLIC GraphQL endpoint (no auth needed for search).
     */
    override suspend fun search(query: String): List<TrackEntry> = withContext(dispatchers.io) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val gqlQuery = """
                query (${'$'}search: String!) {
                    Page(perPage: 10) {
                        media(search: ${'$'}search, type: ANIME) {
                            id
                            title { romaji english native }
                            episodes
                            coverImage { large }
                        }
                    }
                }
            """.trimIndent()
            val variables = buildJsonObject { put("search", query) }
            val response = executeAuthenticatedQuery(gqlQuery, variables, accessToken ?: "")
            val root = json.parseToJsonElement(response).jsonObject
            val media = root["data"]?.jsonObject?.get("Page")?.jsonObject
                ?.get("media")?.jsonArray ?: return@withContext emptyList()
            media.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                TrackEntry(
                    contentKey = "",
                    trackerId = id,
                    totalEpisodes = obj["episodes"]?.jsonPrimitive?.intOrNull,
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "search failed: ${e.message}" }
            emptyList()
        }
    }

    /**
     * D-242: Deletes the user's MediaList entry for [trackerId] (the AniList anime ID).
     *
     * Uses the `DeleteMediaListEntry` mutation. Returns `true` on success,
     * `false` on failure (not logged in, network error, or the entry didn't exist).
     *
     * The caller is responsible for deleting the local cache entry via
     * `TrackEntryRepository.delete(mainId)`.
     */
    suspend fun deleteEntry(trackerId: Int): Boolean = withContext(dispatchers.io) {
        val token = accessToken ?: run {
            Logger.w(TAG) { "deleteEntry — not logged in; aborting" }
            return@withContext false
        }
        try {
            // AniList's DeleteMediaListEntry takes the list entry ID (not the mediaId).
            // We need to first fetch the entry to get its `id`, then delete by `id`.
            val existing = fetchEntry(trackerId)
            val listId = existing?.listId
            if (listId == null) {
                Logger.w(TAG) { "deleteEntry — no existing entry for mediaId=$trackerId; nothing to delete" }
                return@withContext true // not an error — already absent
            }
            val query = """
                mutation (${'$'}id: Int!) {
                    DeleteMediaListEntry(id: ${'$'}id) {
                        deleted
                    }
                }
            """.trimIndent()
            val variables = buildJsonObject { put("id", listId) }
            val response = executeAuthenticatedQuery(query, variables, token)
            val root = json.parseToJsonElement(response).jsonObject
            val errors = root["errors"]?.jsonArray
            if (errors != null && errors.isNotEmpty()) {
                val msg = errors.first().jsonObject["message"]?.jsonPrimitive?.content ?: "Unknown API error"
                Logger.e(TAG) { "deleteEntry — API error: $msg" }
                return@withContext false
            }
            Logger.i(TAG) { "deleteEntry — OK: mediaId=$trackerId, listId=$listId deleted" }
            true
        } catch (e: Exception) {
            Logger.e(TAG, e) { "deleteEntry failed: ${e.message}" }
            false
        }
    }

    // ── Private helpers ──

    /** Converts an epoch-millis timestamp to AniList's FuzzyDateInput JSON object. */
    private fun epochToFuzzyDate(epochMillis: Long?): kotlinx.serialization.json.JsonObject {
        if (epochMillis == null || epochMillis <= 0) {
            return buildJsonObject {
                put("year", kotlinx.serialization.json.JsonNull)
                put("month", kotlinx.serialization.json.JsonNull)
                put("day", kotlinx.serialization.json.JsonNull)
            }
        }
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
        return buildJsonObject {
            put("year", cal.get(java.util.Calendar.YEAR))
            put("month", cal.get(java.util.Calendar.MONTH) + 1) // Calendar.MONTH is 0-based
            put("day", cal.get(java.util.Calendar.DAY_OF_MONTH))
        }
    }

    /** Converts AniList's FuzzyDateInput JSON object to an epoch-millis timestamp. */
    private fun fuzzyDateToEpoch(fuzzy: kotlinx.serialization.json.JsonObject?): Long? {
        if (fuzzy == null) return null
        val year = fuzzy["year"]?.jsonPrimitive?.intOrNull ?: return null
        val month = fuzzy["month"]?.jsonPrimitive?.intOrNull ?: 1
        val day = fuzzy["day"]?.jsonPrimitive?.intOrNull ?: 1
        return java.util.Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }.timeInMillis
    }

    /** Returns the content of a JsonPrimitive as a String, or null if the primitive is null. */
    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        if (this == kotlinx.serialization.json.JsonNull) null else content

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
