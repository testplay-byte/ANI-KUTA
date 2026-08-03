package com.confused.anikuta.data.extension.api

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.model.AnimeExtension
import com.confused.anikuta.data.extension.repo.ExtensionRepo
import com.confused.anikuta.data.extension.repo.ExtensionRepoApi
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrator over repos — fetches available extensions from all configured repos.
 *
 * Ported from the old project. First repo wins on pkgName conflict.
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Api".
 */
class AnimeExtensionApi(
    private val repoRepository: ExtensionRepoRepository,
    private val repoApi: ExtensionRepoApi,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Api"
    }

    /**
     * Fetch all available extensions from all configured repos.
     * Returns a deduplicated list (first repo wins on pkgName conflict).
     */
    suspend fun findAvailableExtensions(): List<AnimeExtension.Available> = coroutineScope {
        val repos = repoRepository.getAll()
        if (repos.isEmpty()) {
            Logger.i(TAG) { "No repos configured — returning empty list" }
            return@coroutineScope emptyList()
        }

        Logger.i(TAG) { "Fetching from ${repos.size} repos..." }
        val results = repos.map { repo ->
            async { fetchFromRepo(repo) }
        }.awaitAll()

        val all = results.flatten()
        val deduped = all.distinctBy { it.pkgName }
        Logger.i(TAG) { "Fetched ${all.size} extensions (${deduped.size} unique)" }
        deduped
    }

    /** Get the APK URL for an available extension. */
    fun getApkUrl(extension: AnimeExtension.Available): String =
        ExtensionRepo(baseUrl = extension.repoUrl).apkUrl(extension.apkName)

    private suspend fun fetchFromRepo(repo: ExtensionRepo): List<AnimeExtension.Available> {
        return runCatching {
            repoApi.fetchExtensions(repo)
        }.getOrElse { e ->
            Logger.e(TAG, e) { "Failed to fetch from ${repo.baseUrl}" }
            emptyList()
        }
    }
}
