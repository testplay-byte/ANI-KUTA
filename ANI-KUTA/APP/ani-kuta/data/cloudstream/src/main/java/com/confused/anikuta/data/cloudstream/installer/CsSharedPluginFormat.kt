package com.confused.anikuta.data.cloudstream.installer

import com.lagradost.cloudstream3.plugins.BasePlugin
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

/**
 * Task 58 (round 18) — the SHARED plugin file format (the user's spec):
 * a .cs3 plugin exported under OUR custom extension
 * `<internalName>.moviebox.WHITECAT`.
 *
 * The format is "temporarily our own" (testing/distribution between users):
 * the BYTES are the untouched .cs3 zip (DexClassLoader-compatible as-is);
 * only the FILENAME carries the custom extension, which (a) makes our app
 * the offered handler when the file is opened from a file manager and
 * (b) encodes the plugin's internalName in the stem (repo .cs3 files are
 * named `<internalName>.cs3`, so the stem round-trips the identity).
 *
 * Receiving side: opening such a file (ACTION_VIEW / ACTION_SEND with
 * content:// + application/octet-stream) lands in the app's
 * PluginImportActivity → validate → confirm dialog →
 * [com.confused.anikuta.data.cloudstream.CloudstreamPluginManager.importSharedPlugin].
 *
 * Pure JVM (java.util.zip + kotlinx.serialization) — unit-testable.
 */
object CsSharedPluginFormat {

    /** The custom extension (the user's example: "moviebox.WHITECAT"). */
    const val SHARED_EXTENSION = "moviebox.WHITECAT"

    /** The synthetic repo "URL" used to salt the shared-install path (repo-less installs). */
    const val SHARED_PATH_SALT = "shared-file"

    /** Same lenient reader the module's stores use (unknown keys ignored). */
    private val json = Json { ignoreUnknownKeys = true }

    /** The exported file name: `<internalName>.moviebox.WHITECAT`. */
    fun sharedFileName(internalName: String): String =
        "$internalName.$SHARED_EXTENSION"

    /** True when a display name carries our custom extension (case-insensitive). */
    fun isSharedPluginFile(displayName: String): Boolean =
        displayName.endsWith(".$SHARED_EXTENSION", ignoreCase = true)

    /**
     * The internalName from a shared file's display name — the stem before the
     * custom extension ("MovieBoxProvider.moviebox.WHITECAT" →
     * "MovieBoxProvider"). Null when the name doesn't carry the extension.
     * Case-insensitive on the extension (file managers may lowercase it).
     */
    fun internalNameFrom(displayName: String): String? {
        if (!isSharedPluginFile(displayName)) return null
        // isSharedPluginFile verified the tail is ".<ext>" (case-insensitive) —
        // cut exactly there (removeSuffix is case-SENSITIVE, so it can't be used).
        val cut = displayName.length - SHARED_EXTENSION.length - 1
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
     * Derives the best internalName for an import: the file-name stem (the
     * canonical identity, mirrors the repo .cs3 naming), falling back to the
     * manifest's `name` when the display name is opaque.
     */
    fun internalNameFor(displayName: String, manifest: BasePlugin.Manifest?): String? {
        internalNameFrom(displayName)?.let { return it }
        manifest?.name?.trim()?.takeIf { it.isNotBlank() }?.let {
            return CloudstreamPluginInstaller.sanitizeFilename(it)
        }
        return null
    }
}
