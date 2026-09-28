package com.confused.anikuta.core.share

import com.confused.anikuta.core.common.Logger

/**
 * ROUND 101 (WS-C): the share-link factory — :core:share's whole brain.
 *
 * Pure Kotlin (no Android imports): building the three link families and
 * parsing the deep link back. The UI layer (the details feature's share
 * sheet) consumes [build]; MainActivity's deep-link intake consumes
 * [parseDeepLink]. Everything is mainId/URL string math — trivially unit
 * testable and free of framework coupling.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Share".
 */
object ContentShareLinkFactory {

    private const val TAG = "Anikuta:Core:Share"

    /** The custom scheme the app owns (already used by the AniList OAuth flow). */
    const val DEEP_LINK_SCHEME = "anikuta"

    /** The deep-link host — `anikuta://content/{mainId}`. */
    const val DEEP_LINK_HOST = "content"

    /** AniList's canonical anime page prefix (the DATA_SOURCE target, v1). */
    const val ANILIST_ANIME_URL_PREFIX = "https://anilist.co/anime/"

    /**
     * Build every AVAILABLE share link for the content, in the sheet's row
     * order: the extension's site first (the most "this exact content"
     * target), then each data-source provider page, then the app's own deep
     * link. Absent identities are simply absent rows — the sheet stays
     * honest about what this entry actually has.
     */
    fun build(content: ShareableContent): List<ShareLink> {
        val links = mutableListOf<ShareLink>()

        // 1 — the extension's page for this content.
        content.extensionUrl?.takeIf { it.isNotBlank() }?.let { url ->
            links += ShareLink(
                kind = ShareTargetKind.EXTENSION_URL,
                url = url,
                label = "Extension link",
                description = content.extensionSourceName
                    ?: "Opens this content's page on the extension's site",
            )
        }

        // 2 — every linked data-source provider page (AniList today; the
        // list shape keeps MAL/TMDb/future providers additive).
        content.dataSourceLinks.forEach { source ->
            links += ShareLink(
                kind = ShareTargetKind.DATA_SOURCE,
                url = source.url,
                label = source.providerName,
                description = "Opens this content's ${source.providerName} page",
            )
        }

        // 3 — the app's own deep link.
        content.mainId.takeIf { it.isNotBlank() }?.let { mainId ->
            links += ShareLink(
                kind = ShareTargetKind.APP_DEEP_LINK,
                url = buildDeepLink(mainId),
                label = "ANI-KUTA link",
                description = "Opens ANI-KUTA straight on this content",
            )
        }

        Logger.d(TAG) { "Built ${links.size} share link(s) for '${content.title}'" }
        return links
    }

    /** The app's own link for a content mainId: `anikuta://content/{mainId}`. */
    fun buildDeepLink(mainId: String): String =
        "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/$mainId"

    /** The AniList page URL for an anime id (the v1 DATA_SOURCE target). */
    fun buildAniListUrl(anilistId: Int): String =
        "$ANILIST_ANIME_URL_PREFIX$anilistId"

    /**
     * Parse a deep link back into its mainId — MainActivity's intake calls
     * this with `intent.dataString`. Returns null for anything that is not
     * an `anikuta://content/{mainId}` URI (the OAuth URI
     * `anikuta://content`-never — its host is `anilist-auth`, so the two
     * never collide).
     */
    fun parseDeepLink(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        val prefix = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/"
        if (!rawUrl.startsWith(prefix)) return null
        val mainId = rawUrl.removePrefix(prefix).substringBefore('/').substringBefore('?')
        if (mainId.isBlank()) {
            Logger.w(TAG) { "Deep link carried no mainId: $rawUrl" }
            return null
        }
        return mainId
    }
}
