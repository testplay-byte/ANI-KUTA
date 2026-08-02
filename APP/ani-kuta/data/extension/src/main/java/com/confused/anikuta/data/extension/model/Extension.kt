package com.confused.anikuta.data.extension.model

import eu.kanade.tachiyomi.animesource.AnimeSource

/**
 * Represents an installed extension.
 *
 * @param packageName The Android package name of the extension APK.
 * @param name Display name.
 * @param versionName Version string.
 * @param versionCode Numeric version code.
 * @param sources The sources provided by this extension (empty until loaded).
 * @param isNsfw Whether this extension provides NSFW content.
 * @param signatureFingerprint SHA-256 fingerprint of the extension's signing certificate.
 * @param isEnabled Whether the extension is enabled.
 */
data class Extension(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val sources: List<AnimeSource> = emptyList(),
    val isNsfw: Boolean = false,
    val signatureFingerprint: String? = null,
    val isEnabled: Boolean = true,
) {
    val hasSources: Boolean get() = sources.isNotEmpty()
}
