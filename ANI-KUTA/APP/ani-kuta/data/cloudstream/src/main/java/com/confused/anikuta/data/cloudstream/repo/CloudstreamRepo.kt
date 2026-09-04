package com.confused.anikuta.data.cloudstream.repo

import kotlinx.serialization.Serializable

/**
 * A saved CloudStream repository (repo.json URL the user pasted). Persisted as JSON
 * in SharedPreferences by [CloudstreamRepoRepository] — deliberately NOT SQLDelight,
 * mirroring the aniyomi repo store convention (doc 23 §5.2; also kills W2, the
 * repo.json filename collision between ecosystems, via a separate prefs file).
 */
@Serializable
data class CloudstreamRepo(
    val url: String,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
)

/** Result of the add-dialog verification fetch (mirrors RepoVerificationResult). */
sealed interface CsRepoVerificationResult {
    data class Success(
        val repoUrl: String,
        val repoName: String,
        val description: String?,
        val iconUrl: String?,
        val pluginCount: Int,
    ) : CsRepoVerificationResult

    data class Error(val message: String) : CsRepoVerificationResult
}
