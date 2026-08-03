package com.confused.anikuta.data.extension.repo

import android.content.Context
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * CRUD for [ExtensionRepo] rows, backed by SharedPreferences.
 *
 * Ported from the old project. Uses SharedPreferences (NOT SQLDelight) to keep
 * `:data:extension` free of the `:core:database` dependency — repos are a
 * simple list, no relational queries needed.
 *
 * CORE_RULES §23: [repos] is a [StateFlow] so the UI updates live when repos
 * are added/removed.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:RepoRepo".
 */
class ExtensionRepoRepository(context: Context) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:RepoRepo"
        private const val PREFS_NAME = "anikuta_extension_repos"
        private const val KEY_REPOS = "repos_json"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val _repos = MutableStateFlow<List<ExtensionRepo>>(loadAll())
    val repos: StateFlow<List<ExtensionRepo>> = _repos.asStateFlow()

    fun getAll(): List<ExtensionRepo> = _repos.value

    fun getRepo(baseUrl: String): ExtensionRepo? =
        _repos.value.firstOrNull { it.baseUrl == baseUrl }

    fun insert(repo: ExtensionRepo): Boolean {
        if (_repos.value.any { it.baseUrl == repo.baseUrl }) {
            Logger.w(TAG) { "Repo already exists: ${repo.baseUrl}" }
            return false
        }
        val updated = _repos.value + repo
        persist(updated)
        Logger.i(TAG) { "Inserted repo: ${repo.name} (${repo.baseUrl})" }
        return true
    }

    fun delete(baseUrl: String): Boolean {
        val updated = _repos.value.filterNot { it.baseUrl == baseUrl }
        if (updated.size == _repos.value.size) return false
        persist(updated)
        Logger.i(TAG) { "Deleted repo: $baseUrl" }
        return true
    }

    private fun persist(repos: List<ExtensionRepo>) {
        val jsonStr = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExtensionRepo.serializer()),
            repos,
        )
        prefs.edit().putString(KEY_REPOS, jsonStr).apply()
        _repos.value = repos
    }

    private fun loadAll(): List<ExtensionRepo> {
        val jsonStr = prefs.getString(KEY_REPOS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ExtensionRepo.serializer()),
                jsonStr,
            )
        }.getOrElse { e ->
            Logger.e(TAG, e) { "Failed to load repos from prefs" }
            emptyList()
        }
    }
}
