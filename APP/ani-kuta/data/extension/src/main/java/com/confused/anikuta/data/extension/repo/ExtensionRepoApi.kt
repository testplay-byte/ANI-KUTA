package com.confused.anikuta.data.extension.repo

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.model.AnimeExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches and parses extension repo indexes.
 *
 * Ported from the old project. Two operations:
 * 1. [fetchExtensions] — fetch + parse a repo's `index.json` into [AnimeExtension.Available] list.
 * 2. [verifyRepo] — verify a candidate repo URL before adding it.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:RepoApi".
 */
class ExtensionRepoApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:RepoApi"

        /** Extension lib version range (Aniyomi compat — 12.0 to 16.0). */
        private const val LIB_MIN = 12.0
        private const val LIB_MAX = 16.0
    }

    /**
     * Fetch the list of available extensions from a repo.
     * Returns an empty list on any failure (logged).
     */
    suspend fun fetchExtensions(repo: ExtensionRepo): List<AnimeExtension.Available> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client.newCall(
                    Request.Builder().url(repo.indexUrl).build()
                ).execute()
                if (!response.isSuccessful) {
                    Logger.w(TAG) { "fetchExtensions: HTTP ${response.code} for ${repo.baseUrl}" }
                    return@runCatching emptyList()
                }
                val body = response.body?.string().orEmpty()
                parseIndex(body, repo.baseUrl)
            }.getOrElse { e ->
                Logger.e(TAG, e) { "fetchExtensions failed for ${repo.baseUrl}" }
                emptyList()
            }
        }

    /**
     * Verify a repo URL before adding it. Tries `index.min.json` then `index.json`,
     * then fetches optional `repo.json` for proper name/website.
     */
    suspend fun verifyRepo(baseUrl: String): RepoVerificationResult =
        withContext(Dispatchers.IO) {
            // Normalize URL
            var cleanUrl = baseUrl.trim()
            cleanUrl = cleanUrl.removeSuffix("/index.json")
            cleanUrl = cleanUrl.removeSuffix("/index.min.json")
            cleanUrl = cleanUrl.trimEnd('/')

            if (!cleanUrl.startsWith("http")) {
                return@withContext RepoVerificationResult.Error("URL must start with http:// or https://")
            }

            // Try index.min.json first, then index.json
            val indexBody = fetchUrl("$cleanUrl/index.min.json")
                ?: fetchUrl("$cleanUrl/index.json")
                ?: return@withContext RepoVerificationResult.Error("Could not fetch index.json from $cleanUrl")

            val entries = runCatching {
                json.decodeFromString<List<RepoIndexEntry>>(indexBody)
            }.getOrElse {
                return@withContext RepoVerificationResult.Error("Failed to parse index.json: ${it.message}")
            }

            if (entries.isEmpty()) {
                return@withContext RepoVerificationResult.Error("Repository index is empty")
            }

            // Filter by lib version
            val valid = entries.filter { entry ->
                val lib = entry.extractLibVersion()
                lib in LIB_MIN..LIB_MAX
            }

            // Fetch optional repo.json for name/website
            var repoName = cleanUrl.substringAfterLast("/").ifEmpty { cleanUrl }
            var website = ""
            fetchUrl("$cleanUrl/repo.json")?.let { repoJsonBody ->
                runCatching {
                    val meta = json.decodeFromString<RepoMetaDto>(repoJsonBody)
                    repoName = meta.meta.name.ifEmpty { repoName }
                    website = meta.meta.website
                }
            }

            Logger.i(TAG) { "Verified repo: $cleanUrl (${valid.size} extensions)" }
            RepoVerificationResult.Success(cleanUrl, repoName, website, valid.size)
        }

    /** Parse the index.json body into [AnimeExtension.Available] list. */
    internal fun parseIndex(jsonBody: String, repoBaseUrl: String): List<AnimeExtension.Available> {
        val entries = json.decodeFromString<List<RepoIndexEntry>>(jsonBody)
        return entries
            .filter { it.extractLibVersion() in LIB_MIN..LIB_MAX }
            .map { entry ->
                AnimeExtension.Available(
                    name = entry.name.removePrefix("Aniyomi: ").removePrefix("Animiru: "),
                    pkgName = entry.pkg,
                    versionName = entry.version,
                    versionCode = entry.code,
                    libVersion = entry.extractLibVersion(),
                    lang = entry.lang.takeIf { it.isNotBlank() },
                    isNsfw = entry.nsfw == 1,
                    isTorrent = entry.torrent == 1,
                    sources = entry.sources?.map { it.toMetadata() } ?: emptyList(),
                    apkName = entry.apk,
                    iconUrl = "$repoBaseUrl/icon/${entry.pkg}.png",
                    repoUrl = repoBaseUrl,
                )
            }
    }

    private fun fetchUrl(url: String): String? = runCatching {
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        if (response.isSuccessful) response.body?.string() else null
    }.getOrElse { null }

    // ── Internal DTOs (repo index.json format) ────────────────────────────────

    @Serializable
    internal data class RepoIndexEntry(
        val name: String,
        val pkg: String,
        val apk: String,
        val lang: String,
        val code: Long,
        val version: String,
        val nsfw: Int = 0,
        val torrent: Int = 0,
        val sources: List<RepoIndexSource>? = null,
    ) {
        fun extractLibVersion(): Double =
            version.substringBeforeLast('.').toDoubleOrNull() ?: -1.0
    }

    @Serializable
    internal data class RepoIndexSource(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String,
    ) {
        fun toMetadata() = AnimeExtension.Available.AnimeSourceMetadata(id, lang, name, baseUrl)
    }

    @Serializable
    internal data class RepoMetaDto(val meta: RepoMetaContent)

    @Serializable
    internal data class RepoMetaContent(
        val name: String,
        val website: String = "",
    )
}
