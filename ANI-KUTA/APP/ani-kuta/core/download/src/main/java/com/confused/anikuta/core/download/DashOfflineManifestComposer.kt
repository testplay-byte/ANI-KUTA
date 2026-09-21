// CLEAN-ROOM: original ANI-KUTA code.
//
// D-550: composes the manifest bytes a D-548 sidecar stores — no longer the
// ORIGINAL manifest, but one that describes EXACTLY what the download folder
// holds. The v1.1.23 device round proved why the original manifest cannot be
// the sidecar payload: its video AdaptationSet lists every representation the
// CDN offers while only the picked one was saved, so ExoPlayer's ABR selected
// an undownloaded rep (its segments are not in the local index) and the
// episode failed to play at all.
//
// The composed manifest:
//  1. PRUNES the primary manifest to the representations the downloader
//     actually downloaded (the chosen video rep + the best rep of every
//     primary audio set — DashManifestPruner.pruneRepresentations);
//  2. IMPORTS each sibling audio variant's covered audio AdaptationSets
//     (the D-550 offline audio-switching: the picked variant's manifest alone
//     never carried the other variants' audio) — each imported set's segment
//     addressing is ABSOLUTIZED against the sibling manifest's own base chain
//     so the generated segment URLs remain byte-identical to the placements
//     the downloader recorded, no matter which manifest URL media3 resolves
//     the composed document against;
//  3. LABELS every audio AdaptationSet with its audio-version label
//     (`<Label>` → media3 Format.label → the audio track selector shows
//     "MovieBox (Original Audio)" / "MovieBox (English sub)" offline, the
//     same switching UX the streaming player already offers).
//
// The composed bytes are consumed by CsPlayerEngine.startOfflineDashLocal
// (parsed as a SIDeloaded DashMediaSource) and served by LocalDashDataSource
// from the recorded ranges. Kept representations' XML is untouched — every
// recorded segment URL still matches byte-for-byte.
package com.confused.anikuta.core.download

import com.confused.anikuta.core.common.DashManifestPruner
import java.io.ByteArrayOutputStream
import java.net.URI
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Element

/** One sibling audio variant's side of the composition. */
data class DashSiblingAudioSet(
    /** The sibling variant's manifest URL (its addressing resolves against this). */
    val manifestUrl: String,
    /** The variant's audio-version label (e.g. "MovieBox (English sub)"). */
    val label: String,
    /** The sibling manifest bytes as fetched. */
    val manifestBytes: ByteArray,
    /** The segment URLs the downloader actually recorded for this variant (its audio groups' parts). */
    val recordedUrls: Set<String>,
)

object DashOfflineManifestComposer {

    /**
     * Composes the sidecar manifest: the pruned primary document + the
     * siblings' covered audio sets, every audio set labeled with its variant.
     *
     * Never throws for malformed input: a sibling whose manifest cannot be
     * parsed contributes nothing (its audio is simply absent — the download
     * itself already succeeded), and a primary that cannot be re-serialized
     * falls back to the pruned bytes' document as-is.
     */
    fun compose(
        primaryManifestBytes: ByteArray,
        primaryManifestUrl: String,
        keepRepIds: Set<String>,
        primaryAudioLabel: String?,
        siblings: List<DashSiblingAudioSet>,
    ): ByteArray {
        val prunedPrimary = DashManifestPruner.pruneRepresentations(primaryManifestBytes, keepRepIds)
        if (siblings.isEmpty() && primaryAudioLabel.isNullOrBlank()) {
            // Nothing to import, nothing to label — the prune is the whole job.
            return prunedPrimary
        }

        val document = parse(prunedPrimary) ?: return prunedPrimary
        val root = document.documentElement ?: return prunedPrimary
        if (root.tagName != "MPD") return prunedPrimary

        val primaryPeriods = children(root, "Period")
        if (primaryPeriods.isEmpty()) return prunedPrimary

        // ── 2. The sibling imports ───────────────────────────────────────────
        for (sibling in siblings) {
            val siblingDoc = parse(sibling.manifestBytes) ?: continue
            val siblingRoot = siblingDoc.documentElement ?: continue
            if (siblingRoot.tagName != "MPD") continue
            val covered = DashManifestPruner.coveredRepIds(
                sibling.manifestBytes,
                sibling.manifestUrl,
                sibling.recordedUrls,
            )
            if (covered.isEmpty()) continue

            val siblingBase = baseUrlOf(siblingRoot)
                ?.let { absolutize(it, sibling.manifestUrl) } ?: sibling.manifestUrl
            val siblingPeriods = children(siblingRoot, "Period")

            siblingPeriods.forEachIndexed { periodIndex, siblingPeriod ->
                // Episode manifests are single-period; a multi-period sibling's
                // extra periods land on the LAST primary period (there is no
                // meaningful per-period alignment across variants).
                val targetPeriod = primaryPeriods.getOrElse(periodIndex) { primaryPeriods.last() }
                val periodBase = baseUrlOf(siblingPeriod)
                    ?.let { absolutize(it, siblingBase) } ?: siblingBase

                var setOrdinal = 0
                for (set in children(siblingPeriod, "AdaptationSet")) {
                    if (kindOf(set) != "audio") continue
                    // Only sets with at least one covered (downloaded) rep are
                    // imported — an unplanned set would offer an audio track
                    // whose first request dies on the index miss.
                    val coveredHere = children(set, "Representation")
                        .mapNotNull { rep -> rep.getAttribute("id").takeIf { it in covered } }
                    if (coveredHere.isEmpty()) continue

                    val setBase = baseUrlOf(set)
                        ?.let { absolutize(it, periodBase) } ?: periodBase
                    val imported = document.importNode(set, true) as Element

                    pruneImportedReps(imported, covered)
                    absolutizeImportedAddressing(imported, setBase)
                    labelAudioSet(
                        imported,
                        if (setOrdinal == 0) sibling.label else "${sibling.label} ${setOrdinal + 1}",
                    )
                    targetPeriod.appendChild(imported)
                    setOrdinal++
                }
            }
        }

        // ── 3. The primary audio sets' labels ────────────────────────────────
        if (!primaryAudioLabel.isNullOrBlank()) {
            var ordinal = 0
            for (period in primaryPeriods) {
                for (set in children(period, "AdaptationSet")) {
                    if (kindOf(set) != "audio") continue
                    labelAudioSet(
                        set,
                        if (ordinal == 0) primaryAudioLabel else "$primaryAudioLabel ${ordinal + 1}",
                    )
                    ordinal++
                }
            }
        }

        return runCatching { serialize(document) }.getOrDefault(prunedPrimary)
    }

    // ── import helpers ───────────────────────────────────────────────────────

    /**
     * Drops the imported set's representations that were NOT downloaded
     * (a multi-rep audio set: only its best rep is on disk). Blank-id
     * representations are kept — unidentifiable, and dropping something that
     * is actually playing is far worse than keeping a harmless extra entry.
     */
    private fun pruneImportedReps(set: Element, covered: Set<String>) {
        for (rep in children(set, "Representation")) {
            val id = rep.getAttribute("id")
            if (id.isBlank() || id in covered) continue
            set.removeChild(rep)
        }
    }

    /**
     * Rewrites every relative addressable attribute of the imported subtree
     * to an ABSOLUTE URL (the sibling's own base chain — rep → set → period →
     * MPD — exactly the resolution the planner recorded the parts from).
     * Absolute references resolve to themselves regardless of the base the
     * composed document is parsed against, so media3's generated segment URLs
     * stay byte-identical to the recorded placements. The `$Number%05d$`
     * tokens survive: `URI.resolve` keeps percent-escapes literal, and media3
     * substitutes after (or before — both orders agree) its own resolution.
     *
     * Known limit (documented, not served by real CDNs here): a set-level
     * SegmentTemplate combined with per-representation BaseURLs absolutizes
     * against the SET base — the primary download path (no sibling import)
     * handles every shape; the import handles the standard ones.
     */
    private fun absolutizeImportedAddressing(set: Element, setBase: String) {
        // The set's own BaseURL becomes absolute (relative BaseURLs under the
        // composed document would resolve against the PRIMARY manifest URL).
        val setBaseUrlNode = childBaseUrl(set)
        if (setBaseUrlNode != null) {
            setBaseUrlNode.textContent = absolutize(setBaseUrlNode.textContent?.trim().orEmpty(), setBase)
        }
        val effectiveSetBase = setBaseUrlNode?.textContent?.trim() ?: setBase

        for (template in directChildren(set, "SegmentTemplate")) {
            absolutizeTemplateAttrs(template, effectiveSetBase)
        }
        for (list in directChildren(set, "SegmentList")) {
            absolutizeListChildren(list, effectiveSetBase)
        }
        for (rep in directChildren(set, "Representation")) {
            val repBase = childBaseUrl(rep)?.textContent?.trim()?.let { absolutize(it, effectiveSetBase) }
                ?: effectiveSetBase
            childBaseUrl(rep)?.let { it.textContent = repBase }
            for (template in directChildren(rep, "SegmentTemplate")) {
                absolutizeTemplateAttrs(template, repBase)
            }
            for (list in directChildren(rep, "SegmentList")) {
                absolutizeListChildren(list, repBase)
            }
        }
    }

    private fun absolutizeTemplateAttrs(template: Element, base: String) {
        for (attr in listOf("initialization", "media")) {
            val value = template.getAttribute(attr)
            if (value.isNotBlank()) template.setAttribute(attr, absolutize(value, base))
        }
    }

    private fun absolutizeListChildren(list: Element, base: String) {
        for (init in directChildren(list, "Initialization")) {
            val sourceUrl = init.getAttribute("sourceURL")
            if (sourceUrl.isNotBlank()) init.setAttribute("sourceURL", absolutize(sourceUrl, base))
        }
        for (seg in directChildren(list, "SegmentURL")) {
            val media = seg.getAttribute("media")
            if (media.isNotBlank()) seg.setAttribute("media", absolutize(media, base))
        }
    }

    // ── labels ───────────────────────────────────────────────────────────────

    /**
     * Replaces the set's `<Label>` children with one carrying [label] (media3
     * reads it into Format.label — the audio track selector's display name).
     * The label node goes FIRST (before SegmentTemplate/Representations);
     * media3's parser is order-tolerant, and the DASH schema puts Label early.
     */
    private fun labelAudioSet(set: Element, label: String) {
        if (label.isBlank()) return
        for (existing in directChildren(set, "Label")) set.removeChild(existing)
        val node = set.ownerDocument.createElement("Label")
        node.appendChild(set.ownerDocument.createTextNode(label))
        set.insertBefore(node, set.firstChild)
    }

    // ── plumbing (the hardened-factory lessons live in DashManifestPruner) ───

    private fun serialize(document: Document): ByteArray {
        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        val out = ByteArrayOutputStream()
        transformer.transform(DOMSource(document), StreamResult(out))
        return out.toByteArray()
    }

    private fun children(element: Element, tag: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = element.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.parentNode === element) result += node
        }
        return result
    }

    private fun directChildren(element: Element, tag: String): List<Element> = children(element, tag)

    private fun childBaseUrl(element: Element): Element? {
        val nodes = element.getElementsByTagName("BaseURL")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.parentNode === element) return node
        }
        return null
    }

    /** The direct-child BaseURL's text (the planner's baseUrlOf shape). */
    private fun baseUrlOf(element: Element): String? =
        childBaseUrl(element)?.textContent?.trim()?.ifBlank { null }

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

    // The D-546 hardened factory (see DashManifestPruner for the full story):
    // NEVER touches isXIncludeAware — Android's base parser class throws
    // UnsupportedOperationException for that family unconditionally.
    private fun newHardenedFactory(): javax.xml.parsers.DocumentBuilderFactory =
        javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            isExpandEntityReferences = false
        }

    private fun parse(bytes: ByteArray): Document? = runCatching {
        newHardenedFactory().newDocumentBuilder().parse(bytes.inputStream())
    }.getOrNull()
}
