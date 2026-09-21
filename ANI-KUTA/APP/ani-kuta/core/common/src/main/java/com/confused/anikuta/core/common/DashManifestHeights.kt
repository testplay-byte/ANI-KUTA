// CLEAN-ROOM: original ANI-KUTA code.
//
// D-551: the DASH video-resolution probe's PARSER — the pure half of the
// "show every available resolution" feature the v1.1.24 device round asked
// for ("If it is possible to show all the available video resolutions, then I
// would be quite happy with it, like 1080p, 720p, and 480p"). The user sees
// those resolutions in the player's per-stream quality section (media3 reads
// the manifest at playback); the resolve sheet only knew the link's DECLARED
// quality (the provider's single int) — so "MovieBox (Hindi Audio) 1080p"
// hid the 720p/480p reps the same manifest serves. This parser reads the
// manifest's video representations so the sheet can list them.
//
// The network half lives where the links live (the resolve sheet's probe —
// it must ride the LINK'S OWN headers: the MovieBox manifest needs the
// extension's CloudFront-Policy cookie + User-Agent, and a generic fetch
// without them gets a 403). The resolver flow itself stays byte-identical —
// the probe is presentation-layer enrichment, silent by contract: ANY failure
// (transport, status, parse, zero heights) returns null and the sheet simply
// keeps the declared-quality-only row.
//
// WHY in :core:common: the same home as DashManifestPruner (D-550) — pure
// org.w3c.dom + javax.xml work, no media3, no Android framework classes, so
// it is JVM-unit-testable and reusable by any module that needs to look
// INSIDE a manifest without playing it.
//
// D-546 lesson honored: the DOM factory NEVER touches isXIncludeAware — that
// assignment throws UnsupportedOperationException on every Android device
// (libcore's parser class reports spec "Unknown"/"0.0") and once masqueraded
// as "Manifest could not be parsed" across two device rounds. Only the
// supported, wrapped feature refusals are set — the exact factory shape the
// pruner proved on-device.
package com.confused.anikuta.core.common

import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object DashManifestHeights {

    /**
     * The DISTINCT video-representation heights [manifestBytes] lists,
     * sorted DESCENDING (1080, 720, 480 …) — the sheet's "Available:" line
     * renders them top-down like the player's own quality section. Null when
     * the document cannot be parsed or no video height can be read (the
     * caller keeps its declared-quality-only presentation).
     *
     * A representation counts as VIDEO when its height attribute parses AND
     * neither its own mimeType nor its AdaptationSet's contentType/mimeType
     * declares audio (an audio rep never carries a height, but the guard
     * costs nothing and keeps exotic manifests honest).
     */
    fun parse(manifestBytes: ByteArray): List<Int>? {
        val root = runCatching {
            newHardenedFactory().newDocumentBuilder().parse(manifestBytes.inputStream()).documentElement
        }.getOrNull() ?: return null
        if (root.tagName != "MPD") return null

        val heights = mutableSetOf<Int>()
        for (period in directChildren(root, "Period")) {
            for (set in directChildren(period, "AdaptationSet")) {
                if (setDeclaresAudio(set)) continue
                for (rep in directChildren(set, "Representation")) {
                    if (rep.getAttribute("mimeType").lowercase().startsWith("audio")) continue
                    val height = rep.getAttribute("height").toIntOrNull() ?: continue
                    if (height > 0) heights += height
                }
            }
        }
        return heights.sortedDescending().ifEmpty { null }
    }

    /** The audio guards on an AdaptationSet: contentType attr or a ContentComponent child. */
    private fun setDeclaresAudio(set: Element): Boolean {
        if (set.getAttribute("contentType").lowercase() == "audio") return true
        val components = set.getElementsByTagName("ContentComponent")
        for (i in 0 until components.length) {
            val component = components.item(i) as? Element ?: continue
            if (component.getAttribute("contentType").lowercase() == "audio") return true
        }
        return false
    }

    /**
     * The D-546 hardened factory (DashManifestPruner's exact shape): NEVER
     * touches `isXIncludeAware`/`setSchema` — Android's base parser class
     * throws UnsupportedOperationException for the whole family,
     * unconditionally. The DOCTYPE/external-entity refusals are supported
     * (and wrapped for unknown-feature safety).
     */
    private fun newHardenedFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            isExpandEntityReferences = false
        }

    /** Direct-child lookup (the pruner's rule: a child element must not leak across levels). */
    private fun directChildren(element: Element, tag: String): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = element.getElementsByTagName(tag)
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            if (node.parentNode === element) result += node
        }
        return result
    }
}
