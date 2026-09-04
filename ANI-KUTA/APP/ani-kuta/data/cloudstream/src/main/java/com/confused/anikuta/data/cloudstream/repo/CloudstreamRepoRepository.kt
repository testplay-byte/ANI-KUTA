package com.confused.anikuta.data.cloudstream.repo

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.lagradost.cloudstream3.plugins.Repository
import com.lagradost.cloudstream3.plugins.SitePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * CRUD for saved CloudStream repositories, persisted in a DEDICATED SharedPreferences
 * file (doc 23 §5.2 — mirrors :data:extension's ExtensionRepoRepository, separate
 * file so the two ecosystems' repo stores never collide: watch-list W2).
 */
class CloudstreamRepoRepository(
    private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _repos = MutableStateFlow(loadRepos())
    val repos: StateFlow<List<CloudstreamRepo>> = _repos.asStateFlow()

    private fun loadRepos(): List<CloudstreamRepo> = runCatching {
        val raw = prefs.getString(KEY_REPOS, null) ?: return emptyList()
        json.decodeFromString<List<CloudstreamRepo>>(raw)
    }.getOrElse {
        Logger.w(TAG) { "Failed to read CS repo store: ${it.message}" }
        emptyList()
    }

    private fun persist(repos: List<CloudstreamRepo>) {
        prefs.edit().putString(KEY_REPOS, json.encodeToString(repos)).apply()
        _repos.value = repos
    }

    suspend fun insert(repo: CloudstreamRepo) = mutex.withLock {
        withContext(Dispatchers.IO) {
            // Dedup by url — re-adding updates the stored metadata.
            val next = _repos.value.filterNot { it.url == repo.url } + repo
            persist(next.sortedBy { it.name.lowercase() })
        }
    }

    suspend fun delete(url: String) = mutex.withLock {
        withContext(Dispatchers.IO) {
            persist(_repos.value.filterNot { it.url == url })
        }
    }

    fun find(url: String): CloudstreamRepo? = _repos.value.firstOrNull { it.url == url }

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:RepoStore"
        private const val PREFS_NAME = "anikuta_cloudstream_repos"
        private const val KEY_REPOS = "repos_json"
    }
}
