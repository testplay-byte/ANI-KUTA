package com.confused.anikuta.data.extension.model

/**
 * Result of loading an extension.
 *
 * Ported from the old project's `AnimeLoadResult`.
 */
sealed interface LoadResult {
    /** Successfully loaded — the extension is trusted and its sources are available. */
    data class Success(val extension: AnimeExtension.Installed) : LoadResult

    /** The extension is installed but its signature is not trusted. */
    data class Untrusted(val extension: AnimeExtension.Untrusted) : LoadResult

    /** Failed to load — the extension is corrupted or incompatible.
     *  D-295: [message] carries the REAL failure reason (exception class + message
     *  per source class) and [name] the display name, so the Errored row in the
     *  extensions screen can tell the user exactly what went wrong. */
    data class Error(
        val packageName: String,
        val message: String,
        val name: String = packageName,
    ) : LoadResult

    /** The package doesn't look like a valid extension. */
    data object UnrecognizedExtension : LoadResult
}
