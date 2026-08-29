// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Task 47 (playback session): the REAL built-in extractor runtime.
//
// Census over the 80 phisher-repo plugins (dex string analysis, session 6):
//   • 53/80 call loadExtractor() — dispatch only reaches REGISTERED extractors.
//   • 17 subclass StreamWishExtractor, 16 VidStack, 12 Filesim, 11 VidHidePro,
//     7 StreamSB, 6 StreamTape, 6 PixelDrain, 5 Voe, 5 DoodLaExtractor,
//     5 MixDrop, 4 Vidmoly, 3 Emturbovid, 3 Dailymotion, 2 FilemoonV2,
//     2 Mp4Upload + 19 single-plugin mirror classes (FileMoon, VidHidePro3/5/6,
//     OkRuSSL/HTTP, …) — all of them just override name/mainUrl and INHERIT the
//     base getUrl, so implementing the families below covers the whole set.
//   • 20/80 also call M3u8Helper.generateM3u8 (implemented in M3u8Helper.kt).
//
// The dominant embed pattern is jwplayer/videojs with P.A.C.K.E.R.-packed
// sources — one shared engine ([extractJwPlayerLinks]) serves that family;
// Dood / StreamTape / MixDrop / StreamSB / Voe / Dailymotion / PixelDrain /
// Ok.ru / Streamlare have dedicated implementations.
//
// Registration: real CloudStream registers its built-ins at app start so
// loadExtractor can dispatch; plugin-registered mirrors append LATER and the
// reverse-order dispatch prefers them (their overridden mainUrl wins).
@file:Suppress("ktlint")

package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.inferTypeFromUrl
import com.lagradost.api.Log
import java.util.concurrent.atomic.AtomicBoolean

// ─────────────────────────────────────────────────────────────────────────────
// The shared jwplayer/videojs engine (StreamWish / Filesim / Filemoon / Vidmoly /
// VidHide / Vidstream / Emturbovid / Mp4Upload / LuluStream / mirror families)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fetches the embed page, unpacks every P.A.C.K.E.R. block, then harvests the
 * player's `sources`/`file:` declarations (+ subtitle tracks) and emits one
 * [ExtractorLink] per unique media URL.
 *
 * Emitted link shape: `source = [sourceName]`, `name = label or ""` — display
 * formatting is the consumer's job (the bridge builds "Source - label 720p").
 */
internal suspend fun ExtractorApi.extractJwPlayerLinks(
    pageUrl: String,
    referer: String?,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    sourceName: String = name,
): Boolean {
    val response = app.get(pageUrl, referer = referer)
    // Unpack every packed block (some hosts pack twice — the JsUnpacker
    // multi-passes until nothing packed remains).
    val script = getAndUnpack(response.text)
    var foundAny = false

    // 1) jwplayer/videojs "sources" arrays: {"file":"URL","label":"720p"} —
    //    objects may also carry "tracks". Capture file+label pairs.
    val sourceObjects = Regex(
        pattern = """\{\s*"(?:file|src|url)"\s*:\s*"((?:\\.|[^"\\])+)"(?:[^{}]*?"label"\s*:\s*"((?:\\.|[^"\\])*)")?[^{}]*\}""",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    )
    // 2) bare jw setup "file":"URL" without an object wrapper.
    val bareFile = Regex("""(?:"file"|file)\s*[=:]\s*"((?:\\.|[^"\\])+)"\s*[,}]""")
    // 3) subtitle/caption tracks.
    // NOTE: raw strings cannot end with a quote before the """ terminator,
    // so each alternative ends with a semantically-free "\s*".
    val trackRegex = Regex(
        pattern = """\{\s*"file"\s*:\s*"([^"]+)"[^{}]*?"kind"\s*:\s*"captions"[^{}]*?"label"\s*:\s*"([^"]*)"\s*""" +
            """|\{\s*"kind"\s*:\s*"captions"[^{}]*?"file"\s*:\s*"([^"]+)"[^{}]*?"label"\s*:\s*"([^"]*)"\s*""",
    )
    // 4) last resort: any absolute m3u8/mp4 URL in the unpacked script.
    val bareMediaUrl = Regex("""(https?://[^\s"'<>\\]+?\.(?:m3u8|mp4)[^\s"'<>\\]*)""")

    // Subtitles first (independent of video links).
    trackRegex.findAll(script).forEach { match ->
        val (url, lang) = (match.groupValues[1].ifBlank { match.groupValues[3] }) to
            (match.groupValues[2].ifBlank { match.groupValues[4] })
        if (url.startsWith("http") && !url.endsWith(".m3u8")) {
            subtitleCallback(SubtitleFile(lang = lang.ifBlank { "Subtitle" }, url = url))
        }
    }

    val seen = mutableSetOf<String>()
    fun emit(url: String, label: String) {
        val clean = url.replace("\\/", "/")
        if (clean.isBlank() || !seen.add(clean)) return
        val quality = when {
            label.isNotBlank() -> getQualityFromName(label)
            else -> qualityFromMediaUrl(clean) ?: Qualities.Unknown.value
        }
        callback(
            ExtractorLink(
                source = sourceName,
                name = label.trim(),
                url = clean,
                referer = pageUrl,
                quality = quality,
                headers = mapOf("User-Agent" to USER_AGENT),
                type = inferTypeFromUrl(clean),
            ),
        )
        foundAny = true
    }

    sourceObjects.findAll(script).forEach { match ->
        val file = match.groupValues[1].replace("\\/", "/")
        if (file.startsWith("http")) emit(file, match.groupValues[2])
    }
    if (!foundAny) {
        bareFile.findAll(script).forEach { match ->
            val file = match.groupValues[1].replace("\\/", "/")
            if (file.startsWith("http")) emit(file, "")
        }
    }
    if (!foundAny) {
        bareMediaUrl.findAll(script).forEach { match ->
            emit(match.groupValues[1], "")
        }
    }
    return foundAny
}

/** Best-effort pixel height out of a media URL's path ("-1080p.", "/1080/") —
 *  only the path segment is searched so long token query params can't
 *  masquerade as resolutions. */
private fun qualityFromMediaUrl(url: String): Int? {
    Regex("""[^\w/](\d{3,4})[pP]?[^\w/]?""").findAll(url.substringBefore('?')).forEach { match ->
        match.groupValues[1].toIntOrNull()?.let { return it }
    }
    return null
}

// ─────────────────────────────────────────────────────────────────────────────
// The jwplayer-family bases (subclassed by 60+ mirrors across the plugin set)
// ─────────────────────────────────────────────────────────────────────────────

open class StreamWishExtractor : ExtractorApi() {
    override val name = "Streamwish"
    override val mainUrl = "https://streamwish.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class VidStack : ExtractorApi() {
    override var name = "Vidstack"
    override var mainUrl = "https://vidstream.io"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }

    fun decryptAES(inputHex: String, key: String, iv: String): String =
        throw NotImplementedError("VidStack.decryptAES is not implemented in this build")
}

open class Filesim : ExtractorApi() {
    override val name = "Filesim"
    override val mainUrl = "https://files.im"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class VidHidePro : ExtractorApi() {
    override val name = "VidHidePro"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

/** Chain: VidhideExtractor → VidHidePro → ExtractorApi. */
open class VidhideExtractor : VidHidePro() {
    override var name = "VidHide"
    override var mainUrl = "https://vidhide.com"
    override val requiresReferer = false
}

open class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = "https://filemoon.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class Vidmoly : ExtractorApi() {
    override val name = "Vidmoly"
    override val mainUrl = "https://vidmoly.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class EmturbovidExtractor : ExtractorApi() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dedicated implementations (page structure is NOT jwplayer-generic)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * DoodStream — the pass_md5 dance:
 *  1. the embed page references `/pass_md5/<hash>`;
 *  2. GET `https://<host>/pass_md5/<hash>` (referer = embed page) returns a
 *     CDN base URL as plain text;
 *  3. the playable URL is that text + a filename filler + `?token=…&expiry=…`
 *     (token/expiry literals sit in the page's download link).
 */
open class DoodLaExtractor : ExtractorApi() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.la"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val page = app.get(url, referer = referer).text
        val md5 = Regex("""/pass_md5/([A-Za-z0-9]+)""").find(page)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("DoodStream: no pass_md5 token in page")
        val host = runCatching { java.net.URI(url).host }.getOrNull()
            ?: throw ErrorLoadingException("DoodStream: bad url")
        val token = Regex("""token=([A-Za-z0-9]+)""").find(page)?.groupValues?.get(1)
        val expiry = Regex("""expiry=([0-9]+)""").find(page)?.groupValues?.get(1)

        val base = app.get("https://$host/pass_md5/$md5", referer = url).text.trim()
        if (base.isBlank()) throw ErrorLoadingException("DoodStream: empty pass_md5 response")
        val query = buildString {
            if (token != null) append("?token=$token")
            if (expiry != null) append(if (isEmpty()) "?" else "&").append("expiry=$expiry")
        }
        // The filler segment mimics the random filename dood's own player uses.
        val trueLink = "${base}30jofjgmm3$query"
        callback(
            ExtractorLink(
                source = name,
                name = "",
                url = trueLink,
                referer = url,
                quality = Qualities.Unknown.value,
                headers = mapOf("User-Agent" to USER_AGENT),
                type = inferTypeFromUrl(trueLink),
            ),
        )
    }
}

/**
 * StreamTape — the famous `robotlink` two-piece concat: the page builds the
 * download URL as `'part1' + 'part2'` inside a script tag.
 */
open class StreamTape : ExtractorApi() {
    override var name = "StreamTape"
    override var mainUrl = "https://streamtape.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val page = app.get(url, referer = referer).text
        val parts = Regex(
            """innerHTML\s*=\s*'(.*?)'\s*\+\s*'(.*?)'""",
        ).find(page)?.groupValues ?: throw ErrorLoadingException("StreamTape: no robotlink")
        var link = (parts[1] + parts[2]).replace("\\'", "'")
        if (link.startsWith("//")) link = "https:$link"
        if (!link.startsWith("http")) throw ErrorLoadingException("StreamTape: unparsable link")
        callback(
            ExtractorLink(
                source = name,
                name = "",
                url = link,
                referer = url,
                quality = Qualities.Unknown.value,
                headers = mapOf("User-Agent" to USER_AGENT),
                type = inferTypeFromUrl(link),
            ),
        )
    }
}

/**
 * MixDrop — after unpacking, the player script assigns `MDCore.wurl = "…"`
 * (a protocol-relative or host-relative path to the media file).
 */
open class MixDrop : ExtractorApi() {
    override var name = "MixDrop"
    override var mainUrl = "https://mixdrop.co"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val page = app.get(url, referer = referer).text
        val unpacked = getAndUnpack(page)
        val wurl = Regex("""wurl\s*=\s*["']([^"']+)["']""").find(unpacked)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("MixDrop: no wurl")
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: "mixdrop.co"
        val link = when {
            wurl.startsWith("http") -> wurl
            wurl.startsWith("//") -> "https:$wurl"
            wurl.startsWith("/") -> "https://$host$wurl"
            else -> "https://$host/$wurl"
        }
        callback(
            ExtractorLink(
                source = name,
                name = "",
                url = link,
                referer = url,
                quality = Qualities.Unknown.value,
                headers = mapOf("User-Agent" to USER_AGENT),
                type = inferTypeFromUrl(link),
            ),
        )
    }

    override fun getExtractorUrl(id: String): String =
        "https://mixdrop.co/e/$id"
}

/**
 * StreamSB — the sources API: `/sources{N}/{id}` (host varies by mirror,
 * index drifts over the years) returns JSON with stream_data.file = m3u8.
 */
open class StreamSB : ExtractorApi() {
    override var name = "StreamSB"
    override var mainUrl = "https://watchsb.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringBefore("?").trimEnd('/').substringAfterLast("/")
        if (id.isBlank()) throw ErrorLoadingException("StreamSB: no id in url")
        val hosts = buildList {
            runCatching { add(java.net.URI(url).host!!) }
            add("watchsb.com")
            add("sbspeed.com")
        }
        val indices = listOf("sources49", "sources50", "sources48", "sources51")
        for (host in hosts) {
            for (index in indices) {
                val response = runCatching {
                    app.get("https://$host/$index/$id", referer = url)
                }.getOrNull() ?: continue
                val text = response.text
                val file = Regex(""""file"\s*:\s*"(https?://[^"]+)"\s*""").find(text)?.groupValues?.get(1)
                if (!file.isNullOrBlank()) {
                    val label = Regex(""""label"\s*:\s*"([^"]*)"\s*""").find(text)?.groupValues?.get(1).orEmpty()
                    val quality = when {
                        label.isNotBlank() -> getQualityFromName(label)
                        else -> qualityFromMediaUrl(file) ?: Qualities.Unknown.value
                    }
                    callback(
                        ExtractorLink(
                            source = name,
                            name = label,
                            url = file,
                            referer = url,
                            quality = quality,
                            headers = mapOf("User-Agent" to USER_AGENT),
                            type = inferTypeFromUrl(file),
                        ),
                    )
                    return
                }
            }
        }
        throw ErrorLoadingException("StreamSB: all sources endpoints failed for $id")
    }
}

/**
 * Voe — heavily obfuscated and rotating. Multiple known layouts are tried in
 * order; failure is the extractor's normal outcome (other mirrors serve).
 */
open class Voe : ExtractorApi() {
    override val name = "Voe"
    override val mainUrl = "https://voe.sx"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val page = app.get(url, referer = referer).text
        val script = getAndUnpack(page)
        val candidates = listOf(
            // direct mp4 redirect inside the page script
            Regex("""["'](https?://[^"']+\.mp4[^"']*)["']"""),
            // master playlist reference
            Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']"""),
            // preload hint
            Regex("""href\s*=\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']"""),
        )
        for (regex in candidates) {
            val match = regex.find(script) ?: continue
            val link = match.groupValues[1]
            callback(
                ExtractorLink(
                    source = name,
                    name = "",
                    url = link,
                    referer = url,
                    quality = Qualities.Unknown.value,
                    headers = mapOf("User-Agent" to USER_AGENT),
                    type = inferTypeFromUrl(link),
                ),
            )
            return
        }
        throw ErrorLoadingException("Voe: no link found")
    }
}

/** Dailymotion — the public player metadata API returns per-quality m3u8s. */
open class Dailymotion : ExtractorApi() {
    override val mainUrl = "https://www.dailymotion.com"
    override val name = "Dailymotion"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringBefore("?").trimEnd('/').substringAfterLast("/")
        if (id.isBlank()) throw ErrorLoadingException("Dailymotion: no id")
        val metadata = app.get("https://www.dailymotion.com/player/metadata/video/$id").text
        // qualities: {"auto":[{…}], "1080":[{"type":"application/x-mpegURL","url":"…"}], …}
        val qualityEntries = Regex(
            """"(\w+)"\s*:\s*\[\s*\{\s*"(?:type|url)"[^]]*?"url"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)"\s*""",
        )
        var found = false
        qualityEntries.findAll(metadata).forEach { match ->
            val qualityKey = match.groupValues[1]
            val streamUrl = match.groupValues[2].replace("\\/", "/")
            if (streamUrl.isNotBlank()) {
                callback(
                    ExtractorLink(
                        source = name,
                        name = if (qualityKey == "auto") "" else "${qualityKey}p",
                        url = streamUrl,
                        referer = url,
                        quality = qualityKey.toIntOrNull() ?: Qualities.Unknown.value,
                        headers = mapOf("User-Agent" to USER_AGENT),
                        type = ExtractorLinkType.M3U8,
                    ),
                )
                found = true
            }
        }
        if (!found) throw ErrorLoadingException("Dailymotion: no qualities for $id")
    }
}

/** PixelDrain — public list/file APIs; /u/<id> is a list, /v|/f/<id> a file. */
open class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringBefore("?").trimEnd('/').substringAfterLast("/")
        if (id.isBlank()) throw ErrorLoadingException("PixelDrain: no id")
        val listJson = runCatching { app.get("https://pixeldrain.com/api/list/$id").text }.getOrNull()
        val fileIds = Regex(""""id"\s*:\s*"([^"]+)"\s*""").findAll(listJson.orEmpty())
            .map { it.groupValues[1] }.toList()
        val ids = fileIds.ifEmpty { listOf(id) }
        var found = false
        for (fileId in ids.distinct()) {
            val link = "https://pixeldrain.com/api/file/$fileId?download"
            callback(
                ExtractorLink(
                    source = name,
                    name = "",
                    url = link,
                    referer = url,
                    quality = Qualities.Unknown.value,
                    headers = mapOf("User-Agent" to USER_AGENT),
                    type = inferTypeFromUrl(link),
                ),
            )
            found = true
        }
        if (!found) throw ErrorLoadingException("PixelDrain: nothing for $id")
    }
}

/** Ok.ru — the videoPlayerMetadata API returns labeled mp4 renditions. */
open class OkRuSSL : ExtractorApi() {
    override val name = "OkRu"
    override val mainUrl = "https://ok.ru"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringBefore("?").trimEnd('/').substringAfterLast("/")
        if (id.isBlank()) throw ErrorLoadingException("OkRu: no id")
        val metadata = app.get("https://ok.ru/dk?cmd=videoPlayerMetadata&mid=$id", referer = url).text
        // "videos":[{"name":"mobile","url":"…mp4"},…] (+ master m3u8)
        val entries = Regex(
            """"name"\s*:\s*"([^"]*)"[^{}]*?"url"\s*:\s*"(https?://[^"]+)"\s*|""" +
                """"url"\s*:\s*"(https?://[^"]+\.m3u8[^"]*)"\s*""",
        )
        val nameToQuality = mapOf("mobile" to 144, "lowest" to 240, "low" to 360, "sd" to 360, "hd" to 720, "full" to 1080, "quad" to 2160)
        var found = false
        entries.findAll(metadata).forEach { match ->
            val mediaUrl = (match.groupValues[2].ifBlank { match.groupValues[3] }).replace("\\/", "/")
            if (mediaUrl.isNotBlank() && (mediaUrl.contains(".mp4") || mediaUrl.contains(".m3u8"))) {
                val label = match.groupValues[1]
                callback(
                    ExtractorLink(
                        source = name,
                        name = label,
                        url = mediaUrl,
                        referer = url,
                        quality = nameToQuality[label.lowercase()] ?: qualityFromMediaUrl(mediaUrl) ?: Qualities.Unknown.value,
                        headers = mapOf("User-Agent" to USER_AGENT),
                        type = inferTypeFromUrl(mediaUrl),
                    ),
                )
                found = true
            }
        }
        if (!found) throw ErrorLoadingException("OkRu: no videos for $id")
    }
}

/** Streamlare — the stream/get API returns the direct file URL. */
open class Streamlare : ExtractorApi() {
    override val name = "Streamlare"
    override val mainUrl = "https://streamlare.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val id = url.substringBefore("?").trimEnd('/').substringAfterLast("/")
        if (id.isBlank()) throw ErrorLoadingException("Streamlare: no id")
        val response = app.post(
            "https://streamlare.com/api/video/stream/get",
            json = mapOf("id" to id),
            referer = url,
        ).text
        val file = Regex(""""file"\s*:\s*"(https?://[^"]+)"\s*""").find(response)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Streamlare: no file for $id")
        callback(
            ExtractorLink(
                source = name,
                name = "",
                url = file.replace("\\/", "/"),
                referer = url,
                quality = Qualities.Unknown.value,
                headers = mapOf("User-Agent" to USER_AGENT),
                type = inferTypeFromUrl(file),
            ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mirror/variant declarations (single-plugin census classes — plugins override
// name/mainUrl and inherit the family behavior above)
// ─────────────────────────────────────────────────────────────────────────────

/** filemoon.sx mirror family (distinct from FilemoonV2's filemoon.to). */
open class FileMoon : FilemoonV2() {
    override var name = "FileMoon"
    override var mainUrl = "https://filemoon.sx"
}

open class Mp4Upload : ExtractorApi() {
    override var name = "Mp4Upload"
    override var mainUrl = "https://www.mp4upload.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class Vidmolyme : Vidmoly() {
    override var name = "Vidmoly"
    override var mainUrl = "https://vidmoly.me"
}

open class VidHidePro3 : VidHidePro() {
    override var name = "VidHidePro"
    override var mainUrl = "https://vidhidepro.com"
}

open class VidHidePro5 : VidHidePro() {
    override var name = "VidHidePro"
    override var mainUrl = "https://vidhide.pro"
}

open class VidHidePro6 : VidHidePro() {
    override var name = "VidHidePro"
    override var mainUrl = "https://vidhide.io"
}

open class StreamSB8 : StreamSB() {
    override var name = "StreamSB"
    override var mainUrl = "https://sbthe.com"
}

open class OkRuHTTP : OkRuSSL() {
    override var name = "OkRu"
    override var mainUrl = "http://ok.ru"
}

open class DoodYtExtractor : DoodLaExtractor() {
    override var name = "DoodStream"
    override var mainUrl = "https://dood.yt"
}

open class Geodailymotion : Dailymotion() {
    override var name = "Dailymotion"
    override var mainUrl = "https://geo.dailymotion.com"
}

open class Upstream : VidStack() {
    override var name = "Upstream"
    override var mainUrl = "https://upstream.to"
}

open class Vtbe : VidStack() {
    override var name = "Vtbe"
    override var mainUrl = "https://vtbe.to"
}

open class Krakenfiles : ExtractorApi() {
    override val name = "Krakenfiles"
    override val mainUrl = "https://krakenfiles.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class LuluStream : ExtractorApi() {
    override val name = "LuluStream"
    override val mainUrl = "https://lulustream.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class ByseVepoin : ExtractorApi() {
    override val name = "ByseVepoin"
    override val mainUrl = "https://bysevepoin.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class GDMirrorbot : ExtractorApi() {
    override val name = "GDMirrorbot"
    override val mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class XStreamCdn : ExtractorApi() {
    override val name = "XStreamCdn"
    override val mainUrl = "https://xstreamcdn.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

open class Jeniusplay : ExtractorApi() {
    override val name = "Jeniusplay"
    override val mainUrl = "https://jeniusplay.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        extractJwPlayerLinks(url, referer, subtitleCallback, callback)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Registration (mirrors the upstream behavior of registering built-ins at app
// start — BEFORE any plugin loads, so plugin-registered mirror extractors win
// the reverse-order dispatch)
// ─────────────────────────────────────────────────────────────────────────────

private val registered = AtomicBoolean(false)

/** Instantiates + registers every built-in extractor (idempotent, once). */
fun registerBuiltinExtractors() {
    if (registered.getAndSet(true)) return
    val builtins: List<() -> ExtractorApi> = listOf(
        ::StreamWishExtractor, ::VidStack, ::Filesim, ::VidHidePro, ::VidhideExtractor,
        ::FilemoonV2, ::Vidmoly, ::EmturbovidExtractor, ::DoodLaExtractor, ::StreamTape,
        ::MixDrop, ::StreamSB, ::Voe, ::Dailymotion, ::PixelDrain, ::OkRuSSL, ::Streamlare,
        ::FileMoon, ::Mp4Upload, ::Vidmolyme, ::VidHidePro3, ::VidHidePro5, ::VidHidePro6,
        ::StreamSB8, ::OkRuHTTP, ::DoodYtExtractor, ::Geodailymotion, ::Upstream, ::Vtbe,
        ::Krakenfiles, ::LuluStream, ::ByseVepoin, ::GDMirrorbot, ::XStreamCdn, ::Jeniusplay,
    )
    builtins.forEach { constructor -> extractorApis.add(constructor.invoke()) }
    Log.i("BuiltinExtractors", "registered ${builtins.size} built-in extractor(s)")
}
