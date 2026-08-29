// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// THIS FILE'S NAME MATTERS: top-level declarations compile into the `ExtractorApiKt`
// facade referenced by 79/80 real plugins (newExtractorLink / loadExtractor).
//
// Session-1 note: the built-in extractor REGISTRY ships EMPTY (upstream registers
// 321 instances of ~97 scraper classes — GPL code we do not copy). Plugin-registered
// extractors land in [extractorApis] and dispatch works; built-in scraper
// implementations arrive in the playback session (doc 23 §7).
@file:Suppress("DEPRECATION_ERROR", "ktlint")
@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class, Prerelease::class)

package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.annotation.JsonIgnore
import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.IDownloadableMinimum
import com.lagradost.cloudstream3.Prerelease
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException as KCancellationException
import kotlin.math.min
import kotlin.uuid.Uuid

// ─────────────────────────────────────────────────────────────────────────────
// Playlist models
// ─────────────────────────────────────────────────────────────────────────────

/** For use in the ConcatenatingMediaSource. @param durationUs use Long.toUs() for easier input. */
data class PlayListItem(
    val url: String,
    val durationUs: Long,
)

/** Converts Seconds to MicroSeconds, multiplication by 1_000_000. */
fun Long.toUs(): Long = this * 1_000_000

/**
 * If your site has an unorthodox m3u8-like system where there are multiple smaller
 * videos concatenated, use this.
 */
data class ExtractorLinkPlayList(
    override val source: String,
    override val name: String,
    val playlist: List<PlayListItem>,
    override var referer: String,
    override var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob(). */
    override var extractorData: String? = null,
    override var type: ExtractorLinkType,
    override var audioTracks: List<AudioFile> = emptyList(),
) : ExtractorLink(
    source = source,
    name = name,
    url = "",
    referer = referer,
    quality = quality,
    headers = headers,
    extractorData = extractorData,
    type = type,
    audioTracks = audioTracks,
) {
    constructor(
        source: String,
        name: String,
        playlist: List<PlayListItem>,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        playlist = playlist,
        referer = referer,
        quality = quality,
        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        headers = headers,
        extractorData = extractorData,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Link type + DRM constants
// ─────────────────────────────────────────────────────────────────────────────

/** Metadata about the file type used for downloads and player hints. */
enum class ExtractorLinkType {
    /** Single stream of bytes no matter the actual file type. */
    VIDEO,

    /** Split into several .ts files, has support for encrypted m3u8s. */
    M3U8,

    /** Like m3u8 but uses xml, currently no download support. */
    DASH,

    /** No support at the moment. */
    TORRENT,

    /** No support at the moment. */
    MAGNET;

    // See https://www.iana.org/assignments/media-types/media-types.xhtml
    @JsonIgnore
    fun getMimeType(): String = when (this) {
        VIDEO -> "video/mp4"
        M3U8 -> "application/x-mpegURL"
        DASH -> "application/dash+xml"
        TORRENT, MAGNET -> "application/x-bittorrent"
    }
}

/** Infers the link type from a URL's extension (needed by ctors + the built-in extractors). */
internal fun inferTypeFromUrl(url: String): ExtractorLinkType = when {
    url.endsWith(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
    url.endsWith(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
    url.endsWith(".torrent", ignoreCase = true) -> ExtractorLinkType.TORRENT
    url.startsWith("magnet:", ignoreCase = true) -> ExtractorLinkType.MAGNET
    else -> ExtractorLinkType.VIDEO
}

/** Sentinel: auto-infer the type from the URL. */
val INFER_TYPE: ExtractorLinkType? = null

@Prerelease
val CLEARKEY_DRM_UUID: Uuid = Uuid.fromLongs(-0x1d8e62a7567a4c37L, 0x781AB030AF78D30EL)

@Prerelease
val WIDEVINE_DRM_UUID: Uuid = Uuid.fromLongs(-0x121074568629b532L, -0x5c37d8232ae2de13L)

@Prerelease
val PLAYREADY_DRM_UUID: Uuid = Uuid.fromLongs(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)

val CLEARKEY_UUID: java.util.UUID = java.util.UUID.fromString(CLEARKEY_DRM_UUID.toString())

val WIDEVINE_UUID: java.util.UUID = java.util.UUID.fromString(WIDEVINE_DRM_UUID.toString())

val PLAYREADY_UUID: java.util.UUID = java.util.UUID.fromString(PLAYREADY_DRM_UUID.toString())

// ─────────────────────────────────────────────────────────────────────────────
// Builders
// ─────────────────────────────────────────────────────────────────────────────

suspend fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    initializer: suspend ExtractorLink.() -> Unit = { },
): ExtractorLink {
    val builder = ExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = "",
        quality = Qualities.Unknown.value,
        headers = mapOf(),
        extractorData = null,
        type = type ?: inferTypeFromUrl(url),
    )
    builder.initializer()
    return builder
}

suspend fun newDrmExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    uuid: java.util.UUID,
    initializer: suspend DrmExtractorLink.() -> Unit = { },
): DrmExtractorLink {
    val kotlinUuid = Uuid.fromLongs(uuid.mostSignificantBits, uuid.leastSignificantBits)
    val builder = DrmExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = "",
        quality = Qualities.Unknown.value,
        headers = mapOf(),
        extractorData = null,
        type = type ?: inferTypeFromUrl(url),
        kid = null,
        key = null,
        uuid = kotlinUuid,
        kty = "oct",
        keyRequestParameters = hashMapOf(),
        licenseUrl = null,
    )
    builder.initializer()
    return builder
}

@Prerelease
suspend fun newDrmExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType? = null,
    uuid: Uuid,
    initializer: suspend DrmExtractorLink.() -> Unit = { },
): DrmExtractorLink {
    val builder = DrmExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = "",
        quality = Qualities.Unknown.value,
        headers = mapOf(),
        extractorData = null,
        type = type ?: inferTypeFromUrl(url),
        kid = null,
        key = null,
        uuid = uuid,
        kty = "oct",
        keyRequestParameters = hashMapOf(),
        licenseUrl = null,
    )
    builder.initializer()
    return builder
}

// ─────────────────────────────────────────────────────────────────────────────
// DrmExtractorLink + ExtractorLink
// ─────────────────────────────────────────────────────────────────────────────

/** Class holds extracted DRM media info to be passed to the player. */
open class DrmExtractorLink private constructor(
    override val source: String,
    override val name: String,
    override val url: String,
    override var referer: String,
    override var quality: Int,
    override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob(). */
    override var extractorData: String? = null,
    override var type: ExtractorLinkType,
    open var kid: String? = null,
    open var key: String? = null,
    open var uuid: Uuid,
    open var kty: String? = null,
    open var keyRequestParameters: HashMap<String, String>,
    open var licenseUrl: String? = null,
    override var audioTracks: List<AudioFile> = emptyList(),
) : ExtractorLink(
    source, name, url, referer, quality, headers, extractorData, type, audioTracks,
) {
    @Deprecated("Use newDrmExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String? = null,
        quality: Int? = null,
        /** The type of the media, use INFER_TYPE if you want to auto infer the type from the url. */
        type: ExtractorLinkType? = INFER_TYPE,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob(). */
        extractorData: String? = null,
        kid: String? = null,
        key: String? = null,
        uuid: Uuid = CLEARKEY_DRM_UUID,
        kty: String? = "oct",
        keyRequestParameters: HashMap<String, String> = hashMapOf(),
        licenseUrl: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer ?: "",
        quality = quality ?: Qualities.Unknown.value,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
        kid = kid,
        key = key,
        uuid = uuid,
        kty = kty,
        keyRequestParameters = keyRequestParameters,
        licenseUrl = licenseUrl,
    )

    @Deprecated("Use newDrmExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        /** The type of the media, use INFER_TYPE if you want to auto infer the type from the url. */
        type: ExtractorLinkType?,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob(). */
        extractorData: String? = null,
        kid: String? = null,
        key: String? = null,
        uuid: Uuid = CLEARKEY_DRM_UUID,
        kty: String? = "oct",
        keyRequestParameters: HashMap<String, String> = hashMapOf(),
        licenseUrl: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
        kid = kid,
        key = key,
        uuid = uuid,
        kty = kty,
        keyRequestParameters = keyRequestParameters,
        licenseUrl = licenseUrl,
    )

    @Deprecated(message = "Use Kotlin Uuid", level = DeprecationLevel.HIDDEN)
    fun setUuid(uuid: java.util.UUID) {
        this.uuid = Uuid.fromLongs(uuid.mostSignificantBits, uuid.leastSignificantBits)
    }

    @Deprecated(message = "Use Kotlin Uuid", level = DeprecationLevel.HIDDEN)
    fun getUuid(): java.util.UUID = java.util.UUID.fromString(uuid.toString())
}

/** Class holds extracted media info to be passed to the player. */
@Serializable
open class ExtractorLink(
    @SerialName("source") open val source: String,
    @SerialName("name") open val name: String,
    @SerialName("url") override val url: String,
    @SerialName("referer") override var referer: String,
    @SerialName("quality") open var quality: Int,
    @SerialName("headers") override var headers: Map<String, String> = mapOf(),
    /** Used for getExtractorVerifierJob(). */
    @SerialName("extractorData") open var extractorData: String? = null,
    @SerialName("type") open var type: ExtractorLinkType,
    /** List of separate audio tracks that can be merged with this video. */
    @SerialName("audioTracks") open var audioTracks: List<AudioFile> = emptyList(),
) : IDownloadableMinimum {
    @get:JsonIgnore val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
    @get:JsonIgnore val isDash: Boolean get() = type == ExtractorLinkType.DASH

    /** Get video size in bytes with one head request. Only available for ExtractorLinkType.Video. */
    suspend fun getVideoSize(timeoutSeconds: Long = 3L): Long? {
        if (type != ExtractorLinkType.VIDEO) return null
        val response = app.head(url, headers = getAllHeaders(), referer = referer, timeout = timeoutSeconds)
        return response.headers["content-length"]?.toLongOrNull()
    }

    @JsonIgnore
    fun getAllHeaders(): Map<String, String> {
        val headerMap = headers.toMutableMap()
        if (referer.isNotBlank() && !headerMap.keys.any { it.equals("referer", true) }) {
            headerMap["referer"] = referer
        }
        return headerMap
    }

    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String? = null,
        quality: Int? = null,
        /** The type of the media, use INFER_TYPE if you want to auto infer the type from the url. */
        type: ExtractorLinkType? = INFER_TYPE,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob(). */
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer ?: "",
        quality = quality ?: Qualities.Unknown.value,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
    )

    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        /** The type of the media, use INFER_TYPE if you want to auto infer the type from the url. */
        type: ExtractorLinkType?,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob(). */
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type ?: inferTypeFromUrl(url),
    )

    /**
     * Old constructor without isDash — backwards compatibility with extensions.
     */
    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob(). */
        extractorData: String? = null,
    ) : this(source, name, url, referer, quality, headers, extractorData, if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO)

    @Deprecated("Use newExtractorLink", level = DeprecationLevel.ERROR)
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = mapOf(),
        /** Used for getExtractorVerifierJob(). */
        extractorData: String? = null,
        isDash: Boolean,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = if (isDash) ExtractorLinkType.DASH else if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
    )

    override fun toString(): String = "ExtractorLink($source, $name, $url, ${Qualities.getStringByInt(quality)})"
}

// ─────────────────────────────────────────────────────────────────────────────
// Qualities
// ─────────────────────────────────────────────────────────────────────────────

enum class Qualities(var value: Int, val defaultPriority: Int) {
    Unknown(400, 4),
    P144(144, 0), // 144p
    P240(240, 2), // 240p
    P360(360, 3), // 360p
    P480(480, 4), // 480p
    P720(720, 5), // 720p
    P1080(1080, 6), // 1080p
    P1440(1440, 7), // 1440p
    P2160(2160, 8); // 4k or 2160p

    companion object {
        fun getStringByInt(qual: Int?): String {
            if (qual == null) return ""
            return when (qual) {
                0 -> "Auto"
                2160 -> "4K"
                else -> "$qual" + "p"
            }
        }

        fun getStringByIntFull(quality: Int): String {
            return when (quality) {
                0 -> "Auto"
                2160 -> "4K"
                400 -> "Unknown"
                else -> "$quality" + "p"
            }
        }
    }
}

/** Maps a free-text quality label (e.g. "1080p") to a [Qualities] pixel height. */
fun getQualityFromName(qualityName: String?): Int {
    if (qualityName == null) return Qualities.Unknown.value
    return qualityName.filter { it.isDigit() }.toIntOrNull() ?: Qualities.Unknown.value
}

// ─────────────────────────────────────────────────────────────────────────────
// Packed-JS helpers
// ─────────────────────────────────────────────────────────────────────────────

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)""")

/** Detects P.A.C.K.E.R.-style packed JS. */
fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

/** Unpacks packed JS if packed; returns the input otherwise (skeleton-grade, doc 23 §4). */
fun getAndUnpack(string: String): String {
    return if (getPacked(string) != null) {
        JsUnpacker(string).unpack() ?: string
    } else {
        string
    }
}

/** Resolves a short link by following redirects (head request). */
suspend fun unshortenLinkSafe(url: String): String {
    return runCatching {
        app.head(url, allowRedirects = true).url
    }.getOrDefault(url)
}

// ─────────────────────────────────────────────────────────────────────────────
// Extractor dispatch
// ─────────────────────────────────────────────────────────────────────────────

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean = loadExtractor(url, null, subtitleCallback, callback)

/**
 * Tries to load the appropriate extractor based on the link, returns true if any
 * extractor is loaded. Dispatch (our own implementation of the documented contract,
 * doc 03 §6): reverse registration order (newest extractor wins), URL prefix match
 * after schema/www strip, then a fuzzy mirror-domain pass (partial-ratio > 80).
 */
@Throws(CancellationException::class)
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    if (url.isBlank()) return false
    val currentUrl = url.lowercase().let {
        if (it.startsWith("https") || it.startsWith("http")) {
            schemaStripRegex.replace(url, "")
        } else {
            url
        }
    }

    // Pass 1: exact prefix match, reverse registration order.
    val target = extractorApis.withLock { extractorApis.toList() }
    for (index in target.indices.reversed()) {
        val extractor = target[index]
        if (currentUrl.startsWith(extractor.mainUrl.lowercase(), 0)) {
            extractor.getSafeUrl(url, referer, subtitleCallback, callback)
            return true
        }
    }

    // Pass 2: fuzzy mirror-domain match.
    for (index in target.indices.reversed()) {
        val extractor = target[index]
        if (partialRatio(extractor.mainUrl.lowercase(), currentUrl) > 80) {
            extractor.getSafeUrl(url, referer, subtitleCallback, callback)
            return true
        }
    }
    return false
}

/** Our own bounded Levenshtein partial-ratio (0-100) for mirror-domain matching. */
private fun partialRatio(a: String, b: String): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    val window = min(a.length, b.length)
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    var best = 0
    for (start in 0..(longer.length - window)) {
        val slice = longer.substring(start, start + window)
        val dist = levenshtein(shorter, slice)
        val score = ((window - dist) * 100) / window
        if (score > best) best = score
        if (best == 100) break
    }
    return best
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..b.length) {
            val temp = dp[j]
            dp[j] = min(
                min(dp[j] + 1, dp[j - 1] + 1),
                prev + if (a[i - 1] == b[j - 1]) 0 else 1,
            )
            prev = temp
        }
    }
    return dp[b.length]
}

// ─────────────────────────────────────────────────────────────────────────────
// The extractor registry + abstract base
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The global extractor registry. EMPTY in this build — plugins register their own
 * extractors into it (reverse-registration dispatch means plugin extractors win).
 * The upstream built-in scraper set is deliberately not copied (doc 23 §3).
 */
val extractorApis: AtomicMutableList<ExtractorApi> = atomicListOf()

fun getExtractorApiFromName(name: String): ExtractorApi =
    extractorApis.first { it.name == name }

fun requireReferer(name: String): Boolean =
    extractorApis.firstOrNull { it.name == name }?.requiresReferer ?: false

fun httpsify(url: String): String =
    if (url.startsWith("http://") && !url.startsWith("http://localhost") && !url.startsWith("http://127.")) {
        url.replaceFirst("http://", "https://")
    } else {
        url
    }

/** Parses a POST form action out of an HTML page (runtime helper — not wired yet). */
suspend fun getPostForm(requestUrl: String, html: String): String? = null

/** Extractor-relative URL joiner (mirrors the MainAPI.fixUrl contract). */
fun ExtractorApi.fixUrl(url: String): String {
    if (url.isBlank()) return url
    if (url.contains("://") || url.startsWith("//") || url.startsWith("{\"") || url.startsWith("[")) return url
    return mainUrl.trimEnd('/') + "/" + url.trimStart('/')
}

/** Removes https:// and www. */
val schemaStripRegex = Regex("""^(https:|)//(www\.|)""")

/**
 * The extractor base class. Two overridable getUrl forms: the modern 4-arg
 * (subtitle + link streaming) and the legacy 2-arg (returns a list).
 */
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    // this is the new extractorapi, override to add subtitles and stuff
    @Throws
    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        try {
            getUrl(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            if (e is KCancellationException) throw e
            com.lagradost.api.Log.e("ExtractorApi", "getSafeUrl error: ${e.message}")
        }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself.
     */
    @Throws
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return emptyList()
    }

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
