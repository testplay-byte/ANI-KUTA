package com.confused.anikuta.data.extension.repo

/**
 * Result of verifying a repo URL before adding it.
 *
 * Ported from the old project. Declared here (not in ExtensionRepoApi) so the
 * UI can import it without depending on the API class.
 */
sealed interface RepoVerificationResult {
    data class Success(
        val cleanUrl: String,
        val repoName: String,
        val website: String,
        val extensionCount: Int,
    ) : RepoVerificationResult

    data class Error(val message: String) : RepoVerificationResult
}
