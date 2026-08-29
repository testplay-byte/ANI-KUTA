package com.confused.anikuta.core.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Task 48 (device round 7 — CS downloads): a MINIMAL mirror of the app-level
 * `ResolveContext` used ONLY to read the [linkRotates] flag from the stored
 * `resolve_context` JSON without `:core:download` depending on the app module
 * (the same boundary trick as [HttpDownloader.ReResolver] + ReResolverAdapter).
 *
 * `linkRotates = true` marks short-TTL, host-rotating extractor links
 * (CloudStream providers) — the fetchers re-resolve those with the same
 * machinery as localhost proxy URLs when they 403 mid-download.
 */
@Serializable
private data class ResolveContextPeek(
    val linkRotates: Boolean = false,
)

private val peekJson = Json { ignoreUnknownKeys = true }

/** True when the resolve context marks the link as short-TTL (may 403 → re-resolve). */
internal fun resolveContextLinkRotates(resolveContextJson: String?): Boolean {
    if (resolveContextJson.isNullOrBlank()) return false
    return runCatching {
        peekJson.decodeFromString(ResolveContextPeek.serializer(), resolveContextJson).linkRotates
    }.getOrDefault(false)
}
