package com.confused.anikuta.data.extension.model

/**
 * Result of attempting to load an extension.
 */
sealed interface LoadResult {

    /**
     * The extension was loaded successfully.
     */
    data class Success(val extension: Extension) : LoadResult

    /**
     * The extension APK was not found or is corrupted.
     */
    data class Error(val packageName: String, val message: String) : LoadResult

    /**
     * The extension's signature is not trusted.
     */
    data class Untrusted(val packageName: String, val signatureFingerprint: String) : LoadResult
}
