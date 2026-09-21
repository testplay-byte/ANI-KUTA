// CLEAN-ROOM: original ANI-KUTA code.
//
// D-550: the shared DASH manifest pruner — the ONE answer to the offline
// playback failure the v1.1.23 device round exposed: the published `.dashmeta`
// sidecar carried the ORIGINAL manifest, whose video AdaptationSet lists every
// representation the CDN offers (1080p + 720p + 480p on the aoneroom shape)
// while the downloader saved EXACTLY ONE of them. ExoPlayer's adaptive track
// selection boots on DefaultBandwidthMeter's INITIAL bandwidth estimate
// (1 Mbps) — below the 1080p rep's 1.6 Mbps — so its very first pick was the
// 720p rep, whose segments were never downloaded; LocalDashDataSource's index
// miss threw the honest IOException and the episode never played. The
// `maxVideoHeight` pin cannot help here: it CAPS the height but still allows
// every SMALLER (undownloaded) rep.
//
// The fix, both ends of the pipeline:
//  - READ side (CsPlayerEngine.startOfflineDashLocal, for the v1.1.23 episodes
//    ALREADY on disk): [coveredRepIds] derives each representation's first
//    planned URL exactly the way DashManifestPlanner derived it (the same
//    inheritance chain, the same tag substitution, the same URI resolution)
//    and checks it against the sidecar's recorded range URLs; a rep whose
//    init/first-segment URL is absent from the index was never downloaded.
//    [pruneRepresentations] then removes every non-covered representation, so
//    the sideloaded manifest offers ONLY what the local files can serve —
//    ABR has nothing to switch to, and the audio sets that ARE local stay.
//  - WRITE side (DashDownloader's sidecar composition): the same prune with
//    the downloader's own exact knowledge of what it downloaded — the sidecar
//    is born honest (the read-side prune then becomes a no-op safety net).
//
// WHY in :core:common: both :core:download and :core:cs-player need it and
// the modules must not depend on each other (the DashCacheKeys precedent).
// Pure org.w3c.dom + javax.xml work — no media3, no Android framework classes.
//
// D-546 lesson honored: the DOM factory NEVER touches isXIncludeAware — that
// assignment throws UnsupportedOperationException on every Android device
// (libcore's parser class reports spec "Unknown"/"0.0" and refuses the whole
// family unconditionally) and once masqueraded as "Manifest could not be
// parsed" across two device rounds. Only the supported, wrapped feature
// refusals are set.
package com.confused.anikuta.core.common

import java.io.ByteArrayOutputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

object DashManifestPruner {

    /**
     * Derives, for every representation in [manifestBytes], the URL of its
     * FIRST planned part exactly the way `:core:download`'s
     * DashManifestPlanner derives parts (rep → AdaptationSet → Period → MPD
     * BaseURL inheritance; the same `$RepresentationID$`/`$Bandwidth$`/
     * `$Number%0Nd$`/`$Time$` substitution; the same `URI.resolve`
     * absolutization), and returns the ids of the representations whose
     * derived URL appears in [recordedUrls] — i.e. the reps the download
     * actually placed into the local files.
     *
     * A representation with no resolvable addressing is NOT returned (the
     * planner planned no parts for it, so nothing of it is on disk). Blank-id
     * representations cannot be identified by the pruner and are reported as
     * `""` when their derived URL is covered (the prune step keeps them
     * unconditionally — conservative).
     */
    fun coveredRepIds(
        manifestBytes: ByteArray,
        manifestUrl: String,
        recordedUrls: Set<String>,
    ): Set<String> {
        val document = parse(manifestBytes) ?: return emptySet()
        val root = document.documentElement ?: return emptySet()
        if (root.tagName != "MPD") return emptySet()

        val mpdBase = baseUrlOf(root)?.let { absolutize(it, manifestUrl) } ?: manifestUrl
        val covered = mutableSetOf<String>()

        for (period in directChildren(root, "Period")) {
            val periodBase = baseUrlOf(period)?.let { absolutize(it, mpdBase) } ?: mpdBase
            for (set in directChildren(period, "AdaptationSet")) {
                val setBase = baseUrlOf(set)?.let { absolutize(it, periodBase) } ?: periodBase
                val setSegment = directChildren(set, "SegmentTemplate").firstOrNull()
                for (rep in directChildren(set, "Representation")) {
                    val id = rep.getAttribute("id")
                    val repBaseRaw = baseUrlOf(rep)
                    val repBase = repBaseRaw?.let { absolutize(it, setBase) } ?: setBase
                    val ownSegment = directChildren(rep, "SegmentTemplate").firstOrNull()
                    // The planner's addressing resolution order: SegmentTemplate
                    // → SegmentList → BaseURL whole-file. A BaseURL-only rep is
                    // planned as ONE part at repBase (cached whole), so its
                    // first planned URL IS repBase; a rep with no addressing
                    // gets its set base here — which never matches a recorded
                    // segment URL, so it (correctly) prunes.
                    val firstUrl = firstPlannedUrlOf(rep, ownSegment ?: setSegment, repBase)
                        ?: directChildren(rep, "SegmentList").firstOrNull()
                            ?.let { firstPlannedUrlOfList(it, repBase) }
                        ?: repBase
                    if (firstUrl in recordedUrls) {
                        covered.add(id)
                    }
                }
            }
        }
        return covered
    }

    /**
     * Returns [manifestBytes] re-serialized WITHOUT the representations whose
     * `id` is not in [keepIds] (and WITHOUT AdaptationSets left empty by that
     * removal). Blank-id representations are kept — they cannot be identified
     * reliably and dropping a rep that is actually on disk would break
     * playback far worse than keeping a harmless extra entry. Kept
     * representations' XML is untouched, so every recorded segment URL still
     * matches the local index byte-for-byte.
     *
     * Returns the input bytes unchanged when the document cannot be parsed —
     * a pruner failure must never take playback down with it (the caller
     * falls back to the unpruned manifest; that is today's behavior, not a
     * worse one).
     */
    fun pruneRepresentations(manifestBytes: ByteArray, keepIds: Set<String>): ByteArray {
        val document = parse(manifestBytes) ?: return manifestBytes
        val root = document.documentElement ?: return manifestBytes
        if (root.tagName != "MPD") return manifestBytes

        var removed = 0
        for (period in directChildren(root, "Period")) {
            for (set in directChildren(period, "AdaptationSet").toList()) {
                val reps = directChildren(set, "Representation")
                for (rep in reps) {
                    val id = rep.getAttribute("id")
                    if (id.isBlank() || id in keepIds) continue
                    set.removeChild(rep)
                    removed++
                }
                if (directChildren(set, "Representation").isEmpty()) {
                    period.removeChild(set)
                }
            }
        }
        if (removed == 0) return manifestBytes

        return runCatching { serialize(document) }.getOrDefault(manifestBytes)
    }

    // ── the first planned URL per representation (the planner's mirror) ──────

    /**
     * Template addressing: the INIT segment URL when the effective template
     * declares one (the planner always appends it first), else the FIRST media
     * segment URL (timeline mode: the first `S@t` + `startNumber`; duration
     * mode: `startNumber`).
     */
    private fun firstPlannedUrlOf(rep: Element, template: Element?, repBase: String): String? {
        if (template == null) return null
        val mediaTemplate = template.getAttribute("media")
        if (mediaTemplate.isBlank()) return null
        val id = rep.getAttribute("id")
        val bandwidth = rep.getAttribute("bandwidth").toLongOrNull() ?: 0L
        val startNumber = template.getAttribute("startNumber").toLongOrNull() ?: 1L
        val initTemplate = template.getAttribute("initialization")
        if (initTemplate.isNotBlank()) {
            return absolutize(substitute(initTemplate, id, bandwidth, null, null), repBase)
        }
        // No init template — the planner's first part is the first media segment.
        val timeline = directChildren(template, "SegmentTimeline").firstOrNull()
        val firstS = timeline?.let { directChildren(it, "S").firstOrNull() }
        return if (firstS != null) {
            val t = firstS.getAttribute("t").toLongOrNull() ?: 0L
            absolutize(substitute(mediaTemplate, id, bandwidth, startNumber, t), repBase)
        } else {
            absolutize(substitute(mediaTemplate, id, bandwidth, startNumber, null), repBase)
        }
    }

    /**
     * SegmentList addressing (mirrors the planner's planFromList order):
     * the init `sourceURL` (absolutized), else the rep base when the init is a
     * byte-range inside it, else the first SegmentURL's media URL, else the
     * rep base (a range-only SegmentURL shares the rep base). A list with no
     * addressable entry returns the rep base too — nothing of it was planned,
     * so the URL will not be in the recorded set and the rep prunes.
     */
    private fun firstPlannedUrlOfList(list: Element, repBase: String): String? {
        val init = directChildren(list, "Initialization").firstOrNull()
        val sourceUrl = init?.getAttribute("sourceURL")
        if (!sourceUrl.isNullOrBlank()) return absolutize(sourceUrl, repBase)
        if (init != null && init.getAttribute("range").isNotBlank()) return repBase
        val firstMedia = directChildren(list, "SegmentURL")
            .firstOrNull()?.getAttribute("media")
        return if (!firstMedia.isNullOrBlank()) absolutize(firstMedia, repBase) else repBase
    }

    // ── template substitution (the planner's regex + semantics, mirrored) ───

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
            m.value == "\$\$" -> "$"
            m.groupValues[1] == "RepresentationID" -> repId ?: ""
            m.groupValues[1] == "Bandwidth" -> bandwidth.toString()
            m.groupValues[1] == "Time" -> (time ?: 0L).toString()
            else -> { // Number (optionally %0Nd)
                val width = m.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                val n = number ?: 0L
                // Locale.ROOT: the default locale renders Eastern-Arabic digits
                // on ar/fa/bn devices — CDN URLs would 404 (the planner's fix 6).
                if (width != null) String.format(java.util.Locale.ROOT, "%0${width}d", n) else n.toString()
            }
        }
    }

    // ── XML plumbing ─────────────────────────────────────────────────────────

    private fun parse(bytes: ByteArray): Document? = runCatching {
        newHardenedFactory().newDocumentBuilder().parse(bytes.inputStream())
    }.getOrNull()

    /**
     * The D-546 hardened factory (the planner's exact shape): NEVER touches
     * `isXIncludeAware`/`setSchema` — Android's base parser class throws
     * UnsupportedOperationException for the whole family, unconditionally.
     * The DOCTYPE/external-entity refusals below are supported (and wrapped
     * for unknown-feature safety); setExpandEntityReferences is a plain
     * supported setter.
     */
    private fun newHardenedFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            isExpandEntityReferences = false
        }

    private fun serialize(document: Document): ByteArray {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(document), StreamResult(out))
        return out.toByteArray()
    }

    /** Direct-child lookup — a rep-level BaseURL must not leak into sibling resolutions (the planner's review risk 5). */
    private fun directChildren(element: Element, tag: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = element.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.parentNode === element) result += node
        }
        return result
    }

    private fun baseUrlOf(element: Element): String? {
        val nodes = element.getElementsByTagName("BaseURL")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.parentNode === element) {
                return node.textContent?.trim()?.ifBlank { null }
            }
        }
        return null
    }

    private fun absolutize(uri: String, baseUrl: String): String = runCatching {
        URI(baseUrl).resolve(uri).toString()
    }.getOrDefault(uri)
}
