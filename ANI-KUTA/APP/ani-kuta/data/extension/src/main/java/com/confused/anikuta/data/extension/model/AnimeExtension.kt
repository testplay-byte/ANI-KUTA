package com.confused.anikuta.data.extension.model

import android.graphics.drawable.Drawable
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

    /** A trusted, installed extension with live [AnimeSource] instances.
     *
     * Phase DB-OPT (extension trust fix): `isEnabled` is a per-package flag,
     * independent of signer-level trust. Trust is by-signing-certificate-fingerprint
     * (security gate — the signer must be trusted for the extension to LOAD at all).
     * `isEnabled` is the user's per-package control — only enabled extensions' sources
     * appear in pickers (Search source picker, Details manual search). This prevents
     * "I trusted 2 extensions but all same-signer extensions show up" (the signer-trust
     * auto-propagation issue). Default = true (backward compat — existing trusted
     * extensions stay enabled unless the user explicitly disables them).
     */
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
        val icon: Drawable? = null,
        val signatureHash: String = "",
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val repoUrl: String? = null,
        val isEnabled: Boolean = true,
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
        val icon: Drawable? = null,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
        override val isTorrent: Boolean = false,
    ) : AnimeExtension()

    /**
     * D-296: An installed, TRUSTED extension that failed to LOAD (classloader /
     * instantiation error). Previously [com.confused.anikuta.data.extension.model.LoadResult.Error]
     * results were silently dropped — the extension vanished from every list
     * ("trusted it and it disappeared"). Now it gets a visible row in the
     * extensions screen with the failure reason + Retry / Uninstall actions.
     */
    data class Errored(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double,
        override val lang: String? = null,
        override val isNsfw: Boolean = false,
        override val isTorrent: Boolean = false,
        val message: String,
        val icon: Drawable? = null,
    ) : AnimeExtension()

    companion object {
        /** Parse the lib version from a versionName like "1.4.3" → 1.4. */
        fun parseLibVersion(versionName: String): Double =
            versionName.substringBeforeLast('.').toDoubleOrNull() ?: -1.0
    }
}
