// CLEAN-ROOM: original ANI-KUTA code (no CloudStream source copied).
//
// Task 49 (round 9 — DASH surfacing): a PURE, XXE-hardened MPD (DASH manifest)
// parser. MovieBox returns 5 .mpd links per episode that the bridge previously
// hid unconditionally ("[DASH=5 … hidden=5]"). The original app plays them via
// ExoPlayer; MPV cannot demux manifests — but a STATIC manifest whose
// representations are complete single files (BaseURL / SegmentBase) is directly
// playable progressively: the BaseURL IS the whole file. This parser extracts
// exactly that shape and reports everything else honestly (dynamic /
// multi-segment → stays hidden, but the bridge now LOGS why).
//
// The sandbox cannot fetch MovieBox manifests (API geo-blocked — R9-C), so the
// parser keys off runtime structure detection instead of any provider-specific
// assumption, and its behavior is locked by MpdParserTest fixtures.
package com.lagradost.cloudstream3.utils

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/** One DASH <Representation> reduced to what playback decisions need. */
data class MpdRepresentation(
    val id: String?,
    /** "video" or "audio" — from contentType/mimeType. */
    val kind: String,
    val height: Int?,
    val bandwidth: Long?,
    /** Absolutized against the manifest URL. Empty when the rep has no BaseURL. */
    val url: String,
    /**
     * True when [url] points at a COMPLETE progressive file (representation
     * BaseURL, optionally with SegmentBase byte-range indexing) — directly
     * playable by MPV. False for SegmentTemplate/SegmentList (many segment
     * files — needs a real DASH client) or reps without any BaseURL.
     */
    val singleFile: Boolean,
)

data class MpdInfo(
    /** type="dynamic" or a minimumUpdatePeriod — a live/rolling window manifest. */
    val dynamic: Boolean,
    val videoReps: List<MpdRepresentation>,
    val audioReps: List<MpdRepresentation>,
)

object MpdParser {

    /**
     * Parses an MPD manifest body. Never throws for malformed input — returns
     * [MpdInfo] with empty lists (the caller logs and keeps the link hidden);
     * a broken manifest must never abort resolution of the OTHER links.
     */
    fun parse(manifestXml: String, manifestUrl: String): MpdInfo {
        val document = runCatching {
            // DocumentBuilderFactory → DocumentBuilder → parse (the factory
            // itself has no parse — common slip, caught by CI round 1).
            newHardenedFactory().newDocumentBuilder().parse(manifestXml.byteInputStream())
        }
            .getOrElse { return MpdInfo(dynamic = false, videoReps = emptyList(), audioReps = emptyList()) }
        return parseDocument(document, manifestUrl)
    }

    internal fun parseDocument(document: Document, manifestUrl: String): MpdInfo {
        val root = document.documentElement ?: return MpdInfo(false, emptyList(), emptyList())
        if (root.tagName != "MPD") return MpdInfo(false, emptyList(), emptyList())

        val dynamic = root.getAttribute("type").equals("dynamic", ignoreCase = true) ||
            root.getAttribute("minimumUpdatePeriod").isNotBlank()

        // BaseURLs cascade MPD → Period → AdaptationSet → Representation; the
        // nearest non-empty one wins (DASH spec §5.3.9.2 simplified).
        val mpdBase = baseUrlOf(root)?.let { absolutize(it, manifestUrl) } ?: manifestUrl

        val videos = mutableListOf<MpdRepresentation>()
        val audios = mutableListOf<MpdRepresentation>()

        for (period in childrenOf(root, "Period")) {
            val periodBase = baseUrlOf(period)?.let { absolutize(it, mpdBase) } ?: mpdBase
            for (adaptationSet in childrenOf(period, "AdaptationSet")) {
                val setBase = baseUrlOf(adaptationSet)?.let { absolutize(it, periodBase) } ?: periodBase
                val setKind = kindOf(adaptationSet)
                for (repElement in childrenOf(adaptationSet, "Representation")) {
                    val rep = repElement
                    val kind = setKind ?: kindOf(rep) ?: continue
                    val segmented = rep.getElementsByTagName("SegmentTemplate").length > 0 ||
                        rep.getElementsByTagName("SegmentList").length > 0
                    // No BaseURL of its own → the rep's media lives in (many)
                    // template/list segments or is address-only via the parent
                    // base — never a directly playable single file. Report it
                    // (url="", singleFile=false) so callers can COUNT multi-
                    // segment reps; dropping it would make a SegmentTemplate
                    // manifest look like an empty one (CI round-3 lesson).
                    val repBase = baseUrlOf(rep)?.let { absolutize(it, setBase) } ?: ""
                    val representation = MpdRepresentation(
                        id = rep.getAttribute("id").ifBlank { null },
                        kind = kind,
                        height = rep.getAttribute("height").toIntOrNull(),
                        bandwidth = rep.getAttribute("bandwidth").toLongOrNull(),
                        url = repBase,
                        // SegmentBase (a byte-range index over ONE file) still
                        // leaves the BaseURL a complete progressive file — MPV
                        // plays it fine. SegmentTemplate/SegmentList = many
                        // segment files → NOT progressive. No BaseURL at all
                        // → likewise not directly addressable.
                        singleFile = repBase.isNotBlank() && !segmented,
                    )
                    if (kind == "video") videos += representation else audios += representation
                }
            }
        }
        return MpdInfo(dynamic = dynamic, videoReps = videos, audioReps = audios)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun newHardenedFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            // XXE hardening — MPD bodies arrive from untrusted CDNs.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            isXIncludeAware = false
            isExpandEntityReferences = false
        }

    private fun childrenOf(element: Element, tag: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = element.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            (nodes.item(i) as? Element)?.let { result += it }
        }
        return result
    }

    private fun baseUrlOf(element: Element): String? =
        (element.getElementsByTagName("BaseURL").item(0) as? Element)?.textContent?.trim()?.ifBlank { null }

    private fun kindOf(element: Element): String? {
        val contentType = element.getAttribute("contentType").lowercase()
        if (contentType.isNotEmpty()) {
            return when {
                contentType.contains("video") -> "video"
                contentType.contains("audio") -> "audio"
                else -> null
            }
        }
        val mime = element.getAttribute("mimeType").lowercase()
        return when {
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            else -> null
        }
    }

    private fun absolutize(uri: String, baseUrl: String): String = runCatching {
        java.net.URI(baseUrl).resolve(uri).toString()
    }.getOrDefault(uri)
}
