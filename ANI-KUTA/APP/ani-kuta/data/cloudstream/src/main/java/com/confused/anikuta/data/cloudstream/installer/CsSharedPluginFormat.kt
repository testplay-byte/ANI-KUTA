package com.confused.anikuta.data.cloudstream.installer

import com.lagradost.cloudstream3.plugins.BasePlugin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Task 58 (round 18) / **Task 59 (round 19 — the format v2)** — the SHARED
 * plugin file format (the user's spec): a .cs3 plugin exported under OUR
 * custom extension.
 *
 * Round 19 changes (the v0.4.6 device round):
 *  - the extension is now just **`.WHITECAT`** (the user: "what I wanted the
 *    extension of the shared plugin to be only (.WHITECAT)") — the round-18
 *    `.moviebox.WHITECAT` name is still ACCEPTED on import (backward
 *    compatibility with files already shared);
 *  - the export is no longer a byte-for-byte .cs3 copy — it CARRIES METADATA
 *    ([CsExportInfo] at [EXPORT_INFO_ENTRY]: the source repository URL, the
 *    icon URL, the catalog display fields) and optionally the actual ICON
 *    BYTES ([EXPORT_ICON_ENTRY]), so the receiving device preserves the
 *    plugin's icon/cover + source repository instead of landing a
 *    repo-less, icon-less record (the v0.4.6 findings). Extra zip entries
 *    are ignored by `PathClassLoader`/the loader (no signature/CRC gate —
 *    verified), so the file stays DexClassLoader-compatible as-is.
 *
 * The format is "temporarily our own" (testing/distribution between users):
 * the BYTES are the untouched .cs3 zip (plus our two `anikuta/` entries);
 * the FILENAME carries the custom extension, which (a) makes our app the
 * offered handler when the file is opened from a file manager and (b)
 * encodes the plugin's internalName in the stem (repo .cs3 files are named
 * `<internalName>.cs3`, so the stem round-trips the identity).
 *
 * Receiving side: opening such a file (ACTION_VIEW / ACTION_SEND with
 * content:// + application/octet-stream) lands in the app's
 * PluginImportActivity → content-first validation (Task 59: the display
 * name's extension is a HINT, not the gate — `.bin`/renamed files are
 * analyzed by content) → confirm dialog →
 * [com.confused.anikuta.data.cloudstream.CloudstreamPluginManager.importSharedPlugin].
 *
 * Pure JVM (java.util.zip + kotlinx.serialization) — unit-testable.
 */

/**
 * The metadata a shared plugin file carries (Task 59): everything the
 * receiving side needs to render the plugin's row + detail page with the
 * SAME icon/cover, repository link and catalog fields the sender saw — even
 * when the receiver has NO repository added that catalogs the plugin.
 */
@Serializable
data class CsExportInfo(
    /** The sender's source repository URL (the record's repoUrl fallback). */
    val repoUrl: String? = null,
    /** The catalog icon URL (the fallback when no icon bytes are embedded). */
    val iconUrl: String? = null,
    /** The plugin's display name. */
    val name: String? = null,
    /** The plugin's catalog version. */
    val version: Int? = null,
    /** The catalog language code. */
    val language: String? = null,
    /** The catalog authors. */
    val authors: List<String> = emptyList(),
    /** The catalog description. */
    val description: String? = null,
    /** The catalog TvType names. */
    val tvTypes: List<String> = emptyList(),
    /** Export time (epoch ms) — diagnostics only. */
    val exportedAtMs: Long = 0L,
)

object CsSharedPluginFormat {

    /** The custom extension (the user's round-19 spec: just "WHITECAT"). */
    const val SHARED_EXTENSION = "WHITECAT"

    /**
     * The round-18 extension — still ACCEPTED on import so files shared by
     * v0.4.6 devices keep working ("moviebox.WHITECAT").
     */
    private const val LEGACY_SHARED_EXTENSION = "moviebox.WHITECAT"

    /** The synthetic repo "URL" used to salt the shared-install path (repo-less installs). */
    const val SHARED_PATH_SALT = "shared-file"

    /** The zip entry carrying [CsExportInfo] (Task 59). */
    const val EXPORT_INFO_ENTRY = "anikuta/export.json"

    /** The zip entry carrying the embedded icon bytes, when available (Task 59). */
    const val EXPORT_ICON_ENTRY = "anikuta/icon.png"

    /** Same lenient reader the module's stores use (unknown keys ignored). */
    private val json = Json { ignoreUnknownKeys = true }

    /** The exported file name: `<internalName>.WHITECAT`. */
    fun sharedFileName(internalName: String): String =
        "$internalName.$SHARED_EXTENSION"

    /**
     * True when a display name carries our custom extension — the CURRENT
     * `.WHITECAT` or the legacy `.moviebox.WHITECAT` (case-insensitive).
     */
    fun isSharedPluginFile(displayName: String): Boolean =
        sharedExtensionOf(displayName) != null

    /**
     * Which custom extension the display name carries (the current one or
     * the legacy round-18 name), or null. The LEGACY tail is checked FIRST —
     * it is the LONGER one and ends with the current extension too
     * ("X.moviebox.WHITECAT" also matches ".WHITECAT"), so the current-only
     * check would otherwise leave the ".moviebox" infix in the stem.
     */
    private fun sharedExtensionOf(displayName: String): String? = when {
        displayName.endsWith(".$LEGACY_SHARED_EXTENSION", ignoreCase = true) -> LEGACY_SHARED_EXTENSION
        displayName.endsWith(".$SHARED_EXTENSION", ignoreCase = true) -> SHARED_EXTENSION
        else -> null
    }

    /**
     * The internalName from a shared file's display name — the stem before
     * the custom extension ("MovieBoxProvider.WHITECAT" → "MovieBoxProvider";
     * the legacy "MovieBoxProvider.moviebox.WHITECAT" → "MovieBoxProvider").
     * Null when the name doesn't carry an extension. Case-insensitive on the
     * extension (file managers may lowercase it).
     */
    fun internalNameFrom(displayName: String): String? {
        val ext = sharedExtensionOf(displayName) ?: return null
        // sharedExtensionOf verified the tail is ".<ext>" (case-insensitive) —
        // cut exactly there (removeSuffix is case-SENSITIVE, so it can't be used).
        val cut = displayName.length - ext.length - 1
        if (cut <= 0) return null
        return displayName.substring(0, cut).trim().takeIf { it.isNotBlank() }
    }

    /**
     * Reads the .cs3 zip's `manifest.json` (name / pluginClassName /
     * requiresResources / version) WITHOUT a classloader — the import flow's
     * pre-install validation + the confirm dialog's details. Null when the
     * file isn't a zip or has no readable manifest (the activity shows the
     * "not a plugin file" state).
     *
     * Mirrors [CloudstreamPluginLoader]'s resource-based read (same entry,
     * same model, same kotlinx-first parsing) but through [ZipFile] — no dex
     * involved.
     */
    fun readManifest(file: File): BasePlugin.Manifest? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("manifest.json") ?: return@runCatching null
            val text = zip.getInputStream(entry).use { it.readBytes().decodeToString() }
            json.decodeFromString(BasePlugin.Manifest.serializer(), text)
        }
    }.getOrNull()

    /**
     * Task 59 — reads the export metadata entry ([EXPORT_INFO_ENTRY]) a
     * SENDER wrote. Null when the file is a plain .cs3 (no metadata — the
     * round-18 behavior) or the entry is unreadable; never throws.
     */
    fun readExportInfo(file: File): CsExportInfo? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(EXPORT_INFO_ENTRY) ?: return@runCatching null
            val text = zip.getInputStream(entry).use { it.readBytes().decodeToString() }
            json.decodeFromString(CsExportInfo.serializer(), text)
        }
    }.getOrNull()

    /**
     * Task 59 — reads the embedded icon bytes ([EXPORT_ICON_ENTRY]) when the
     * sender could fetch them. Null when absent/unreadable; never throws.
     */
    fun readExportIcon(file: File): ByteArray? = runCatching {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry(EXPORT_ICON_ENTRY) ?: return@runCatching null
            zip.getInputStream(entry).use { it.readBytes() }.takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    /**
     * Task 59 — writes a SHARED plugin file: every original .cs3 entry is
     * copied BYTE-FOR-BYTE (DexClassLoader loads the result as-is), plus:
     *  - [EXPORT_INFO_ENTRY] — the serialized [info] (source repo URL, icon
     *    URL, catalog display fields);
     *  - [EXPORT_ICON_ENTRY] — the [iconBytes] when non-null (the icon
     *    travels INSIDE the file, no network needed on the receiving side).
     *
     * The round-18 `anikuta/` entries (if the source itself was a shared
     * file) are never re-copied — the fresh info replaces them (a re-share
     * carries the CURRENT sender's metadata).
     */
    fun writeSharedFile(source: File, target: File, info: CsExportInfo, iconBytes: ByteArray?) {
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            ZipFile(source).use { zip ->
                for (entry in zip.entries().asSequence()) {
                    if (entry.name == EXPORT_INFO_ENTRY || entry.name == EXPORT_ICON_ENTRY) continue
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            out.putNextEntry(ZipEntry(EXPORT_INFO_ENTRY))
            out.write(json.encodeToString(CsExportInfo.serializer(), info).encodeToByteArray())
            out.closeEntry()
            if (iconBytes != null && iconBytes.isNotEmpty()) {
                out.putNextEntry(ZipEntry(EXPORT_ICON_ENTRY))
                out.write(iconBytes)
                out.closeEntry()
            }
        }
    }

    /**
     * Derives the best internalName for an import: the file-name stem (the
     * canonical identity, mirrors the repo .cs3 naming), falling back to the
     * manifest's `name` when the display name is opaque (Task 59: .bin and
     * renamed files take this path).
     */
    fun internalNameFor(displayName: String, manifest: BasePlugin.Manifest?): String? {
        internalNameFrom(displayName)?.let { return it }
        manifest?.name?.trim()?.takeIf { it.isNotBlank() }?.let {
            return CloudstreamPluginInstaller.sanitizeFilename(it)
        }
        return null
    }
}
