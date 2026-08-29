// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// SKELETONS (doc 23 §4): the 16 built-in extractor BASE classes plugins subclass
// (mirror-domain overriding). Only the declaration shape + name/mainUrl values are
// interop facts; the SCRAPING bodies are deliberately unimplemented in session 1 —
// plugins that subclass these load + register cleanly, and extraction throws a clear
// error when invoked (the playback session implements real scrapers, doc 23 §7).
// The upstream mirror-domain subclasses (Mwish, DoodWf, …) are library-internal —
// zero census plugins reference them, so they are not mirrored.
@file:Suppress("ktlint")

package com.lagradost.cloudstream3.extractors

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink

private const val NOT_IMPLEMENTED =
    "Built-in extractor runtime is not implemented in this build yet (playback session, doc 23 §7)"

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
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

open class VidStack : ExtractorApi() {
    override var name = "Vidstack"
    override var mainUrl = "https://vidstack.io"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }

    fun decryptAES(inputHex: String, key: String, iv: String): String {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
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
        throw NotImplementedError(NOT_IMPLEMENTED)
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
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

/** Chain: VidhideExtractor → VidHidePro → ExtractorApi. */
open class VidhideExtractor : VidHidePro() {
    override var name = "VidHide"
    override var mainUrl = "https://vidhide.com"
    override val requiresReferer = false
}

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
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

open class PixelDrain : ExtractorApi() {
    override val name = "PixelDrain"
    override val mainUrl = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

open class StreamTape : ExtractorApi() {
    override var name = "StreamTape"
    override var mainUrl = "https://streamtape.com"
    override val requiresReferer = false

    // Overrides the OLD 2-arg getUrl (List<ExtractorLink>? return).
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

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
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

open class MixDrop : ExtractorApi() {
    override var name = "MixDrop"
    override var mainUrl = "https://mixdrop.co"
    override val requiresReferer = false

    override fun getExtractorUrl(id: String): String {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }

    // Overrides the OLD 2-arg getUrl.
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

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
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
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
        throw NotImplementedError(NOT_IMPLEMENTED)
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
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

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
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}

open class EmturbovidExtractor : ExtractorApi() {
    override val name = "Emturbovid"
    override val mainUrl = "https://emturbovid.com"
    override val requiresReferer = false

    // Overrides the OLD 2-arg getUrl.
    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        throw NotImplementedError(NOT_IMPLEMENTED)
    }
}
