package com.confused.anikuta.data.extension.model

import eu.kanade.tachiyomi.animesource.AnimeSource

/**
 * Sealed class modeling the three extension states.
 *
 * Ported from the old project's `AnimeExtension` (D-038 — modular, documented).
 * - [Installed]: trusted, sources loaded, available for use.
 * - [Available]: listed in a repo but not yet installed.
 * - [Untrusted]: installed but signature not yet trusted by the user.
 *
 * @param name Display name.
 * @param pkgName Android package name.
 * @param versionName Version string (e.g. "1.4.3").
 * @param versionCode Numeric version code.
 * @param libVersion The extension library version (parsed from versionName's major.minor).
 * @param lang Language code (nullable — some extensions don't specify).
 * @param isNsfw Whether this extension provides NSFW content.
 * @param isTorrent Whether this extension supports torrent sources.
 */
sealed class AnimeExtension {
    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double
    abstract val lang: String?
    abstract val isNsfw: Boolean
    abstract val isTorrent: Boolean

    /** A trusted, installed extension with live [AnimeSource] instances. */
    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String?,
        override val isNsfw: Boolean,
        override val isTorrent: Boolean,
        val sources: List<AnimeSource>,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val repoUrl: String? = null,
    ) : AnimeExtension()

    /** An extension listed in a repo but not yet installed. Sources are metadata only. */
    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String?,
        override val isNsfw: Boolean,
        override val isTorrent: Boolean,
        val sources: List<AnimeSourceMetadata>,
        val apkName: String,
        val iconUrl: String,
        val repoUrl: String,
    ) : AnimeExtension() {
        /** Lightweight source metadata (no live [AnimeSource] — can't load until installed). */
        data class AnimeSourceMetadata(
            val id: Long,
            val lang: String,
            val name: String,
            val baseUrl: String,
        )
    }

    /** An installed extension whose signing certificate is not yet trusted. */
    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
        override val isTorrent: Boolean = false,
    ) : AnimeExtension()

    companion object {
        /** Parse the lib version from a versionName like "1.4.3" → 1.4. */
        fun parseLibVersion(versionName: String): Double =
            versionName.substringBeforeLast('.').toDoubleOrNull() ?: -1.0
    }
}
