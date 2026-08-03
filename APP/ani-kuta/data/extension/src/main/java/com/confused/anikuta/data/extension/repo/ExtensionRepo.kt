package com.confused.anikuta.data.extension.repo

import kotlinx.serialization.Serializable

/**
 * A remote extension repository.
 *
 * Ported from the old project. The repo URL contract:
 * - `<baseUrl>/index.json` — list of available extensions.
 * - `<baseUrl>/icon/<pkg>.png` — extension icon.
 * - `<baseUrl>/apk/<apkName>` — APK file.
 * - `<baseUrl>/repo.json` — optional metadata (name, website).
 *
 * D-043: NO default repos. The user adds their own.
 *
 * @param baseUrl The repo base URL (no trailing slash).
 * @param name Human-readable name (from repo.json or fallback).
 * @param website Optional website URL.
 */
@Serializable
data class ExtensionRepo(
    val baseUrl: String,
    val name: String,
    val website: String = "",
) {
    val indexUrl: String
        get() = "${baseUrl.trimEnd('/')}/index.json"

    fun apkUrl(apkName: String): String =
        "${baseUrl.trimEnd('/')}/apk/$apkName"

    fun iconUrlFor(pkgName: String): String =
        "${baseUrl.trimEnd('/')}/icon/$pkgName.png"
}
