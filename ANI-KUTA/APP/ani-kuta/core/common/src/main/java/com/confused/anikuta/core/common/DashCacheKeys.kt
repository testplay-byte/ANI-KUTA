package com.confused.anikuta.core.common

/**
 * D-539: the DASH offline-cache key scheme — the ONE naming contract shared
 * by the two ends of the CS DASH download pipeline:
 *
 *  - the DOWNLOAD side (`:core:download`'s DashDownloader) writes every
 *    cached resource (the manifest itself, init segments, media segments)
 *    under `csdash|<manifestUrl>|<resourceUrl>`;
 *  - the PLAYBACK side (`:core:cs-player`'s CsPlayerEngine.startOfflineDash)
 *    reads them back through a CacheDataSource whose CacheKeyFactory maps
 *    every incoming DataSpec to the SAME key — so a fully-downloaded episode
 *    plays with ZERO upstream requests (true offline), and a partially-
 *    downloaded one transparently fetches only the missing spans.
 *
 * The manifest URL is embedded in every key so one SimpleCache can hold many
 * episodes (even from the same CDN), and episode deletion is a prefix sweep:
 * `cache.getKeys().filter { it.startsWith(prefix(manifestUrl)) }` →
 * `cache.removeResource(key)` each.
 *
 * Pure string logic (no Media3 imports) so both modules can depend on it
 * without a dependency edge between `:core:download` and `:core:cs-player`.
 */
object DashCacheKeys {

    /** The marker prefix on EVERY DASH cache key. */
    const val PREFIX = "csdash|"

    /**
     * The `video_uri` / `file_path` marker for a DASH-cache downloaded episode
     * row (DB + `.data.json` reconstruction). A row whose uri starts with this
     * scheme has NO published video file — its media lives in the SimpleCache
     * under the manifest URL embedded after the scheme.
     */
    const val URI_SCHEME = "csdash:"

    /** The cache key for ONE resource of a DASH episode (the manifest included — a resource equal to the manifest URL IS the manifest key). */
    fun build(manifestUrl: String, resourceUrl: String): String = "$PREFIX$manifestUrl|$resourceUrl"

    /** The cache key for the manifest itself. */
    fun manifestKey(manifestUrl: String): String = build(manifestUrl, manifestUrl)

    /** The prefix shared by ALL keys of one episode (deletion sweep). */
    fun episodePrefix(manifestUrl: String): String = "$PREFIX$manifestUrl|"

    /**
     * The marker payload decoders for downloaded DASH episode rows.
     *
     * D-548: a `csdash:` row's payload now has TWO modes, and every playback
     * router must branch on them:
     *  - LEGACY (v1.1.20–v1.1.22 downloads): payload = the REMOTE manifest
     *    URL — the media lives in the app-private SimpleCache under
     *    `csdash|<manifestUrl>|…` keys; playback = startOfflineDash over a
     *    CacheDataSource; delete = a cache purge.
     *  - LOCAL-FILES (v1.1.23+ downloads): payload = the content:// document
     *    uri of the episode's published `.mp4.dashmeta` sidecar (the manifest
     *    bytes + the segment→file-range index live in it) — the media lives
     *    in the user's SAF download folder as real files (`<name>.mp4` +
     *    `<name>.audio<N>.mp4`); playback = startOfflineDashLocal over the
     *    sideloaded manifest; delete = the SAF files die with the folder.
     */

    /**
     * The raw payload after the `csdash:` marker — a manifest URL (legacy) or
     * a `.dashmeta` document uri (local-files). Null when [uri] carries no
     * marker. The ONE "route to the CS offline player" test.
     */
    fun payloadFromUri(uri: String?): String? {
        if (uri == null || !uri.startsWith(URI_SCHEME)) return null
        return uri.removePrefix(URI_SCHEME).takeIf { it.isNotBlank() }
    }

    /**
     * The manifest URL for a LEGACY cache-mode marker uri (`csdash:<manifestUrl>`),
     * else null — the decode the cache playback + cache purge paths ride.
     */
    fun manifestUrlFromUri(uri: String?): String? =
        payloadFromUri(uri)?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    /**
     * The `.dashmeta` document uri for a LOCAL-FILES marker uri
     * (`csdash:<content://…dashmeta>`), else null — the decode the local-file
     * playback path rides ([CsPlayerEngine.startOfflineDashLocal]).
     */
    fun offlineMetaUriFromUri(uri: String?): String? =
        payloadFromUri(uri)?.takeIf { it.startsWith("content://") }

    /** True when [uri] is a DASH-offline marker uri in EITHER mode. */
    fun isOfflineDashUri(uri: String?): Boolean = payloadFromUri(uri) != null
}
