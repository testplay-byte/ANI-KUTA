package com.confused.anikuta.data.cloudstream.repo

import com.confused.anikuta.core.common.Logger
import com.lagradost.cloudstream3.plugins.Repository
import com.lagradost.cloudstream3.plugins.SitePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * CloudStream repository protocol client (doc 04): repo.json → pluginLists[] →
 * plugins.json, with the 5-minute in-memory cache CS3's contract documents
 * (freshness comes from the cache, not from re-fetch loops).
 *
 * All parsing goes through the clean-room [Repository]/[SitePlugin] models so the
 * wire format and the plugin-visible format are one and the same.
 */
class CloudstreamRepoApi(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // repoUrl -> (fetchedAtMillis, Repository); pluginListUrl -> (fetchedAtMillis, List<SitePlugin>)
    private val repoCache = HashMap<String, Cached<Repository>>()
    private val pluginsCache = HashMap<String, Cached<List<SitePlugin>>>()

    private class Cached<T>(val fetchedAt: Long, val value: T)

    /** Returns the cached value when fresh (< 5 min), null on miss/expiry. */
    private fun <T> cacheHit(cache: Map<String, Cached<T>>, key: String): T? {
        val hit = cache[key] ?: return null
        if (System.currentTimeMillis() - hit.fetchedAt >= CACHE_MS) return null
        return hit.value
    }

    private suspend fun fetchText(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(okhttp3.Request.Builder().url(url).build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
        }.getOrElse {
            Logger.w(TAG) { "Fetch failed for $url: ${it.message}" }
            null
        }
    }

    /** Fetches + parses repo.json (cached 5 min). Null on network/parse failure. */
    suspend fun fetchRepository(url: String): Repository? {
        cacheHit(repoCache, url)?.let { return it }
        val text = fetchText(url) ?: return null
        val parsed = parseRepositoryOrNull(text) ?: return null
        repoCache[url] = Cached(System.currentTimeMillis(), parsed)
        return parsed
    }

    /** Fetches every pluginLists entry in parallel, flattens, dedupes by plugin url. */
    suspend fun fetchPlugins(repository: Repository, repoUrl: String = ""): List<SitePlugin> = coroutineScope {
        val baseUrl = repoUrl.substringBeforeLast('/')
        repository.pluginLists.map { listUrl ->
            val resolved = if (listUrl.startsWith("http")) listUrl else "$baseUrl/$listUrl"
            async {
                cacheHit(pluginsCache, resolved)?.let { return@async it }
                val text = fetchText(resolved) ?: return@async emptyList()
                val parsed = runCatching { json.decodeFromString<List<SitePlugin>>(text) }
                    .getOrElse {
                        Logger.w(TAG) { "plugins.json parse failed for $resolved: ${it.message}" }
                        return@async emptyList()
                    }
                pluginsCache[resolved] = Cached(System.currentTimeMillis(), parsed)
                parsed
            }
        }.awaitAll().flatten().distinctBy { it.url }
    }

    /**
     * The add-dialog verification: fetch repo.json at [url], then count its plugins
     * (doc 04 §4.2 — CS3 shows "no plugins found" when the index yields nothing).
     */
    suspend fun verifyRepo(url: String): CsRepoVerificationResult {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http")) {
            return CsRepoVerificationResult.Error("URL must start with http(s)://")
        }
        val repository = fetchRepository(trimmed)
            ?: return CsRepoVerificationResult.Error(
                "Could not fetch or parse repo.json — is this a CloudStream repository URL?",
            )
        val plugins = fetchPlugins(repository, trimmed)
        if (plugins.isEmpty()) {
            return CsRepoVerificationResult.Error("No plugins found in this repository")
        }
        return CsRepoVerificationResult.Success(
            repoUrl = trimmed,
            repoName = repository.name,
            description = repository.description,
            iconUrl = repository.iconUrl,
            pluginCount = plugins.size,
        )
    }

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:RepoApi"
        private const val CACHE_MS = 5 * 60 * 1000L // doc 04 §1 — 5-minute cache

        /**
         * Pure structural detector used by the SHARED add-repo dialog to tell a
         * CloudStream repo.json apart from an aniyomi index: a JSON object with a
         * non-null name + manifestVersion + pluginLists (doc 04 §2.1 — the only
         * de-facto schema validation the ecosystem performs).
         */
        fun parseRepositoryOrNull(text: String): Repository? = runCatching {
            Json { ignoreUnknownKeys = true }.decodeFromString<Repository>(text)
        }.getOrNull()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
