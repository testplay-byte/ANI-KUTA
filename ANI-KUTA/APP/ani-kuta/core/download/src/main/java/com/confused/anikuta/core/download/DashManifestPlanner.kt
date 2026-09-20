// CLEAN-ROOM: original ANI-KUTA code.
//
// D-539: the DASH (MPD) segment planner — turns a manifest into the exact
// list of byte resources a download must cache. Companion to the summary-only
// MpdParser in :core:cloudstream-api (that one answers "what reps exist";
// this one answers "what bytes do I fetch, in what order, from where").
//
// Supported addressing (the shapes real anime CDNs emit):
//  - SegmentTemplate @media/@initialization with SegmentTimeline ($Time$ /
//    $Number$/$RepresentationID$/$Bandwidth$ substitution, S@t/@d/@r incl.
//    negative repeat-to-period-end) — the modern fMP4 default;
//  - SegmentTemplate @duration (timeline-less fixed-duration numbering);
//  - SegmentList (SegmentURL @media/@range + Initialization @sourceURL/@range);
//  - SegmentBase / plain BaseURL — a complete single file (cached whole);
//  - attribute inheritance rep → AdaptationSet → Period (DASH §5.3.9.2).
//
// Honesty rules (the pipeline never downloads garbage):
//  - ANY ContentProtection element → drmProtected=true → the downloader
//    refuses with a clear message (no keys, no Widevine license server);
//  - type="dynamic" (live) → unsupported reason;
//  - a representation with no resolvable addressing → unsupported reason;
//  - parse failures return an EMPTY plan, never throw (the queue shows the
//    honest error and the other sources stay usable).
package com.confused.anikuta.core.download

import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One byte resource to cache: a URL + optional byte range. */
data class DashPart(
    val url: String,
    /** Range start; 0 when the whole file is wanted. */
    val position: Long = 0L,
    /** Range length; -1 = to EOF (C.LENGTH_UNSET semantics). */
    val length: Long = -1L,
    val isInit: Boolean = false,
    /** Which track this part belongs to ("video"/"audio") — diagnostics. */
    val kind: String = "video",
)

/** The chosen video representation. */
data class DashVideoRep(
    val id: String?,
    val height: Int?,
    val bandwidth: Long,
)

/** The plan DashDownloader executes. */
data class DashSegmentPlan(
    val videoRep: DashVideoRep?,
    val audioRepIds: List<String>,
    val parts: List<DashPart>,
    /** bandwidth-derived byte estimate (0 = unknowable → progress runs indeterminate). */
    val estimatedBytes: Long,
    val drmProtected: Boolean,
    /** Non-null when the manifest is genuinely undownloadable (live, unaddressable). */
    val unsupportedReason: String?,
)

object DashManifestPlanner {

    fun parse(manifestXml: String, manifestUrl: String, preferredHeight: Int?): DashSegmentPlan {
        val document = runCatching {
            newHardenedFactory().newDocumentBuilder().parse(manifestXml.byteInputStream())
        }.getOrElse {
            return empty(manifestUrl, "Manifest could not be parsed")
        }
        return parseDocument(document, manifestUrl, preferredHeight)
    }

    // ── parsing ──────────────────────────────────────────────────────────────

    internal fun parseDocument(
        document: org.w3c.dom.Document,
        manifestUrl: String,
        preferredHeight: Int?,
    ): DashSegmentPlan {
        val root = document.documentElement
            ?: return empty(manifestUrl, "Manifest has no root element")
        if (root.tagName != "MPD") return empty(manifestUrl, "Not a DASH manifest (no <MPD> root)")

        if (root.getAttribute("type").equals("dynamic", true) ||
            root.getAttribute("minimumUpdatePeriod").isNotBlank()
        ) {
            return empty(manifestUrl, "This is a live (dynamic) manifest — it cannot be downloaded")
        }
        if (countElements(root, "ContentProtection") > 0) {
            return empty(
                manifestUrl,
                "This stream is DRM-protected — it cannot be downloaded. Stream it instead.",
            ).copy(drmProtected = true)
        }

        val totalDurationSec = parseIsoDuration(root.getAttribute("mediaPresentationDuration")) ?: 0.0
        val mpdBase = baseUrlOf(root)?.let { absolutize(it, manifestUrl) } ?: manifestUrl

        val videoReps = mutableListOf<RepNode>()
        val audioSets = mutableListOf<Pair<RepNode, List<DashPart>>>() // chosen rep + its parts
        val parts = mutableListOf<DashPart>()
        var unsupported: String? = null

        for (period in elementsOf(root, "Period")) {
            val periodBase = baseUrlOf(period)?.let { absolutize(it, mpdBase) } ?: mpdBase
            val periodDurationSec = parseIsoDuration(period.getAttribute("duration")) ?: totalDurationSec
            for (set in elementsOf(period, "AdaptationSet")) {
                val setBase = baseUrlOf(set)?.let { absolutize(it, periodBase) } ?: periodBase
                val kind = kindOf(set)
                val setSegment = segmentSourceOf(set)

                val reps = elementsOf(set, "Representation").mapNotNull { repElement ->
                    repNodeOf(repElement, kind, setBase, setSegment, periodDurationSec)
                }
                if (reps.isEmpty()) continue

                when (kind) {
                    "video" -> {
                        val best = reps.maxByOrNull {
                            if (preferredHeight != null && preferredHeight > 0) {
                                // Prefer the rep closest to the picked quality…
                                -kotlin.math.abs((it.height ?: 0) - preferredHeight) * 1_000_000L + it.bandwidth
                            } else {
                                // …else the highest bandwidth.
                                it.bandwidth
                            }
                        } ?: continue
                        if (best.unsupportedReason != null) {
                            if (unsupported == null) unsupported = best.unsupportedReason
                            continue
                        }
                        videoReps += best
                        parts += best.parts
                    }
                    "audio" -> {
                        // One (best) rep per audio AdaptationSet — every set is
                        // usually one language (SUB/DUB manifests carry two
                        // sets); caching the best rep of EACH keeps every
                        // audio selection playable offline.
                        val best = reps.maxByOrNull { it.bandwidth } ?: continue
                        if (best.unsupportedReason != null) {
                            if (unsupported == null) unsupported = best.unsupportedReason
                            continue
                        }
                        audioSets += best to best.parts
                        parts += best.parts
                    }
                }
            }
        }

        val chosenVideo = videoReps.maxByOrNull { it.bandwidth }
        if (chosenVideo == null) {
            return empty(
                manifestUrl,
                unsupported ?: "No downloadable video track found in this manifest",
            )
        }

        // The manifest ITSELF is a resource (offline playback's first read).
        val allParts = listOf(DashPart(url = manifestUrl, kind = "manifest")) + parts

        val durationSec = totalDurationSec.takeIf { it > 0.0 }
        val estimated = if (durationSec != null) {
            val videoBytes = (chosenVideo.bandwidth / 8.0) * durationSec
            val audioBytes = audioSets.sumOf { (rep, _) -> (rep.bandwidth / 8.0) * durationSec }
            (videoBytes + audioBytes).toLong()
        } else 0L

        return DashSegmentPlan(
            videoRep = DashVideoRep(chosenVideo.id, chosenVideo.height, chosenVideo.bandwidth),
            audioRepIds = audioSets.map { it.first.id ?: "" },
            parts = allParts,
            estimatedBytes = estimated,
            drmProtected = false,
            unsupportedReason = null,
        )
    }

    // ── representation model ─────────────────────────────────────────────────

    /** A representation with its addressing resolved into concrete parts. */
    private class RepNode(
        val id: String?,
        val height: Int?,
        val bandwidth: Long,
        val parts: List<DashPart>,
        val unsupportedReason: String?,
    )

    /** The segment-addressing source found at ONE element level (rep/set/period). */
    private class SegmentSource(
        val template: Element?,
        val list: Element?,
        val hasSegmentBase: Boolean,
    )

    /**
     * D-539 (review risk 5): the addressing lookup scans DIRECT children only —
     * getElementsByTagName would let a Representation-level SegmentTemplate/
     * BaseURL leak into the parent AdaptationSet/Period and be inherited by
     * sibling reps with different addressing (rep1's segments for rep2).
     */
    private fun directChildren(element: Element, tag: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = element.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.parentNode === element) result += node
        }
        return result
    }

    private fun segmentSourceOf(element: Element): SegmentSource {
        val templates = directChildren(element, "SegmentTemplate")
        val lists = directChildren(element, "SegmentList")
        val bases = directChildren(element, "SegmentBase")
        return SegmentSource(templates.firstOrNull(), lists.firstOrNull(), bases.isNotEmpty())
    }

    private fun repNodeOf(
        repElement: Element,
        kind: String?,
        setBase: String,
        setSegment: SegmentSource,
        periodDurationSec: Double,
    ): RepNode? {
        if (kind == null) return null
        val repBaseRaw = baseUrlOf(repElement)
        val repBase = repBaseRaw?.let { absolutize(it, setBase) } ?: setBase
        val id = repElement.getAttribute("id").ifBlank { null }
        val bandwidth = repElement.getAttribute("bandwidth").toLongOrNull() ?: 0L
        val height = repElement.getAttribute("height").toIntOrNull()

        // DASH addressing inheritance: the rep's own Segment*/BaseURL wins,
        // then the AdaptationSet's, then (via the caller's chain) the Period's.
        val own = segmentSourceOf(repElement)
        val template = own.template ?: setSegment.template
        val list = own.list ?: setSegment.list
        val hasSegmentBase = own.hasSegmentBase || setSegment.hasSegmentBase

        val parts: MutableList<DashPart> = mutableListOf()
        val unsupported: String?

        when {
            template != null -> {
                val err = planFromTemplate(template, id, bandwidth, repBase, periodDurationSec, kind, parts)
                unsupported = err
            }
            list != null -> {
                val err = planFromList(list, repBase, kind, parts)
                unsupported = err
            }
            repBaseRaw != null -> {
                // A BaseURL (with or without SegmentBase index ranges) is a
                // complete file — cache it whole; ranges are spans inside it.
                parts += DashPart(url = repBase, kind = kind)
                unsupported = null
            }
            hasSegmentBase && repBaseRaw == null -> {
                unsupported = "SegmentBase without a BaseURL is not addressable"
            }
            else -> unsupported = "Representation has no resolvable media addressing"
        }

        return RepNode(id, height, bandwidth, parts, unsupported)
    }

    // ── SegmentTemplate planning ─────────────────────────────────────────────

    /** Returns null on success, else the unsupported reason. */
    private fun planFromTemplate(
        template: Element,
        id: String?,
        bandwidth: Long,
        repBase: String,
        periodDurationSec: Double,
        kind: String,
        out: MutableList<DashPart>,
    ): String? {
        val mediaTemplate = template.getAttribute("media")
        val initTemplate = template.getAttribute("initialization")
        val timescale = template.getAttribute("timescale").toLongOrNull() ?: 1L
        val startNumber = template.getAttribute("startNumber").toLongOrNull() ?: 1L

        if (mediaTemplate.isBlank()) return "SegmentTemplate has no media template"
        if (mediaTemplate.contains("\$SubNumber\$") || mediaTemplate.contains("\$PartIndex\$")) {
            return "Low-latency (CTS) segment templates are not supported"
        }

        val timeline = elementsOf(template, "SegmentTimeline").firstOrNull()
        val sEntries = timeline?.let { elementsOf(it, "S") }.orEmpty()

        if (sEntries.isNotEmpty()) {
            // Timeline mode: enumerate S@t/@d/@r (r<0 = repeat to period end).
            val periodTicks = if (periodDurationSec > 0) (periodDurationSec * timescale).toLong() else -1L
            var runningT = 0L
            var number = startNumber
            var emitted = 0L
            for (s in sEntries) {
                val t = s.getAttribute("t").toLongOrNull() ?: runningT
                val d = s.getAttribute("d").toLongOrNull()
                    ?: return "SegmentTimeline entry without a duration"
                var r = s.getAttribute("r").toLongOrNull() ?: 0L
                if (r < 0) {
                    r = if (periodTicks > 0) (periodTicks - t) / d - 1 else 0L
                    if (r < 0) r = 0L
                }
                for (i in 0..r) {
                    val segT = t + i * d
                    val url = absolutize(substitute(mediaTemplate, id, bandwidth, number, segT), repBase)
                    out += DashPart(url = url, kind = kind)
                    number++
                    emitted++
                    if (emitted > MAX_SEGMENTS) return "Manifest declares more than $MAX_SEGMENTS segments"
                }
                runningT = t + (r + 1) * d
            }
        } else {
            val duration = template.getAttribute("duration").toLongOrNull()
                ?: return "SegmentTemplate has neither a SegmentTimeline nor a duration"
            val segmentTicks = duration * timescale
            if (segmentTicks <= 0) return "SegmentTemplate duration/timescale is invalid"
            val periodTicks = if (periodDurationSec > 0) (periodDurationSec * timescale).toLong() else -1L
            val count = if (periodTicks > 0) (periodTicks + segmentTicks - 1) / segmentTicks else 0L
            if (count <= 0L) return "Manifest declares no segments and no period duration"
            if (count > MAX_SEGMENTS) return "Manifest declares more than $MAX_SEGMENTS segments"
            for (i in 0 until count) {
                val number = startNumber + i
                val url = absolutize(substitute(mediaTemplate, id, bandwidth, number, null), repBase)
                out += DashPart(url = url, kind = kind)
            }
        }

        if (initTemplate.isNotBlank()) {
            val initUrl = absolutize(substitute(initTemplate, id, bandwidth, null, null), repBase)
            out.add(0, DashPart(url = initUrl, isInit = true, kind = kind))
        }
        return null
    }

    // ── SegmentList planning ─────────────────────────────────────────────────

    private fun planFromList(
        list: Element,
        repBase: String,
        kind: String,
        out: MutableList<DashPart>,
    ): String? {
        val init = elementsOf(list, "Initialization").firstOrNull()
        init?.let {
            val sourceUrl = it.getAttribute("sourceURL")
            val range = it.getAttribute("range")
            when {
                sourceUrl.isNotBlank() ->
                    out += DashPart(url = absolutize(sourceUrl, repBase), isInit = true, kind = kind)
                range.isNotBlank() -> {
                    val (pos, len) = parseRange(range)
                        ?: return "Initialization range is malformed"
                    out += DashPart(url = repBase, position = pos, length = len, isInit = true, kind = kind)
                }
            }
        }
        val urls = elementsOf(list, "SegmentURL")
        if (urls.isEmpty()) return "SegmentList declares no segments"
        if (urls.size > MAX_SEGMENTS) return "Manifest declares more than $MAX_SEGMENTS segments"
        for (seg in urls) {
            val media = seg.getAttribute("media")
            val range = seg.getAttribute("range")
            when {
                media.isNotBlank() && range.isNotBlank() -> {
                    val (pos, len) = parseRange(range) ?: return "Segment range is malformed"
                    out += DashPart(url = absolutize(media, repBase), position = pos, length = len, kind = kind)
                }
                media.isNotBlank() ->
                    out += DashPart(url = absolutize(media, repBase), kind = kind)
                range.isNotBlank() -> {
                    val (pos, len) = parseRange(range) ?: return "Segment range is malformed"
                    out += DashPart(url = repBase, position = pos, length = len, kind = kind)
                }
                else -> return "SegmentURL has neither media nor range"
            }
        }
        return null
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private const val MAX_SEGMENTS = 20_000L

    /** DASH identifier tags incl. the zero-padded Number form ($Number%05d$). */
    private val TAG = Regex("\\$(RepresentationID|Bandwidth|Number(?:%0(\\d+)d)?|Time)\\$|\\$\\$")

    private fun substitute(
        template: String,
        repId: String?,
        bandwidth: Long,
        number: Long?,
        time: Long?,
    ): String = TAG.replace(template) { m ->
        when {
            m.value == "$$" -> "$"
            m.groupValues[1] == "RepresentationID" -> repId ?: ""
            m.groupValues[1] == "Bandwidth" -> bandwidth.toString()
            m.groupValues[1] == "Time" -> (time ?: 0L).toString()
            else -> { // Number (optionally %0Nd)
                val width = m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                val n = number ?: 0L
                // Locale.ROOT (review risk 6): the default locale renders
                // Eastern-Arabic digits on ar/fa/bn devices — CDN URLs 404.
                if (width != null) String.format(java.util.Locale.ROOT, "%0${width}d", n) else n.toString()
            }
        }
    }

    /** "1000-2000" → (1000, 1001); open ends → -1 (to EOF). */
    private fun parseRange(range: String): Pair<Long, Long>? {
        val parts = range.split("-")
        val start = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: return null
        val end = parts.getOrNull(1)?.trim()?.toLongOrNull()
        return start to ((end?.minus(start)?.plus(1)) ?: -1L)
    }

    private fun elementsOf(element: Element, tag: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = element.getElementsByTagName(tag)
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let { result += it }
        return result
    }

    private fun countElements(element: Element, tag: String): Int = element.getElementsByTagName(tag).length

    private fun baseUrlOf(element: Element): String? {
        // Direct-child lookup (review risk 5 — a rep-level BaseURL must not
        // be inherited by the parent level's own resolution).
        val nodes = element.getElementsByTagName("BaseURL")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.parentNode === element) {
                return node.textContent?.trim()?.ifBlank { null }
            }
        }
        return null
    }

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
        URI(baseUrl).resolve(uri).toString()
    }.getOrDefault(uri)

    /** Minimal ISO-8601 duration parser (PT#H#M#S + Y/M/D) — no java.time (desugaring-free). */
    internal fun parseIsoDuration(value: String): Double? {
        if (value.isBlank()) return null
        val m = Regex(
            "^P(?:(\\d+(?:\\.\\d+)?)Y)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)W)?(?:(\\d+(?:\\.\\d+)?)D)?" +
                "(?:T(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$",
        ).find(value.trim()) ?: return null
        val (y, mo, w, d, h, mi, s) = m.destructured
        fun g(v: String, mult: Double) = v.toDoubleOrNull()?.times(mult) ?: 0.0
        return g(y, 31_557_600.0) + g(mo, 2_629_800.0) + g(w, 604_800.0) + g(d, 86_400.0) +
            g(h, 3_600.0) + g(mi, 60.0) + g(s, 1.0)
    }

    private fun empty(manifestUrl: String, reason: String): DashSegmentPlan = DashSegmentPlan(
        videoRep = null,
        audioRepIds = emptyList(),
        parts = listOf(DashPart(url = manifestUrl, kind = "manifest")),
        estimatedBytes = 0L,
        drmProtected = false,
        unsupportedReason = reason,
    )

    private fun newHardenedFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            // XXE hardening — MPD bodies arrive from untrusted CDNs (the
            // MpdParser hardening, replicated).
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
}
