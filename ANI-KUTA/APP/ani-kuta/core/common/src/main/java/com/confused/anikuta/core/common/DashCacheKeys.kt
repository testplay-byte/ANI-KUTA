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
     * Returns the manifest URL when [uri] is a DASH-cache marker uri
     * (`csdash:<manifestUrl>`), else null — the ONE decoder for the
     * downloaded-episode rows and the playback routers.
     */
    fun manifestUrlFromUri(uri: String?): String? {
        if (uri == null || !uri.startsWith(URI_SCHEME)) return null
        val url = uri.removePrefix(URI_SCHEME)
        return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
