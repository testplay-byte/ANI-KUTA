package com.confused.anikuta.data.extension.repo

import kotlinx.serialization.Serializable

/**
 * A remote extension repository.
 *
 * Ported from the old project. The repo URL contract:
 * - `<baseUrl>/index.min.json` — the MINIFIED index (the Aniyomi-repo
 *   standard; most repos serve it FIRST, and some serve ONLY it — round 135).
 * - `<baseUrl>/index.json` — the pretty-printed index (the legacy fetch).
 * - `<baseUrl>/icon/<pkg>.png` — extension icon.
 * - `<baseUrl>/apk/<apkName>` — APK file.
 * - `<baseUrl>/repo.json` — optional metadata (name, website).
 *
 * D-043: NO default repos. The user adds their own.
 *
 * ROUND 97 (D-667): [hidden] — a VISIBILITY filter for the Extensions page's
 * Available section, nothing more (see CloudstreamRepo.hidden for the full
 * contract). Defaults false; old persisted JSON without the key loads fine.
 *
 * @param baseUrl The repo base URL (no trailing slash).
 * @param name Human-readable name (from repo.json or fallback).
 * @param website Optional website URL.
 * @param hidden True while the repo's not-installed entries are filtered out.
 */
@Serializable
data class ExtensionRepo(
    val baseUrl: String,
    val name: String = "",
    val website: String = "",
    val hidden: Boolean = false,
) {
    val indexUrl: String
        get() = "${baseUrl.trimEnd('/')}/index.json"

    /** The minified index — tried FIRST by every fetch (see the contract above). */
    val indexMinUrl: String
        get() = "${baseUrl.trimEnd('/')}/index.min.json"

    fun apkUrl(apkName: String): String =
        "${baseUrl.trimEnd('/')}/apk/$apkName"

    fun iconUrlFor(pkgName: String): String =
        "${baseUrl.trimEnd('/')}/icon/$pkgName.png"
}
