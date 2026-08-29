package com.confused.anikuta.data.cloudstream.model

import com.lagradost.cloudstream3.plugins.SitePlugin

/**
 * The UI-facing state model for a CloudStream extension (mirrors the aniyomi
 * AnimeExtension sealed shape so the unified Extensions screen renders both
 * with the same section patterns — doc 23 §5.4).
 *
 * - [Installed] — a TRUSTED .cs3 on disk with a persisted record; its providers
 *   are live in the registry. Carries the catalog display metadata (language /
 *   iconUrl / isNsfw / authors / description / tvTypes / fileSizeBytes)
 *   captured at install time so rows AND the plugin detail page render
 *   aniyomi-parity even after the repository is deleted, plus the live
 *   [CsProviderInfo] list for the detail page and the search source picker.
 * - [Untrusted] — installed on disk but NOT trusted (session 3 trust flow):
 *   its classes are never loaded, so no providers are registered. The row's
 *   Trust action loads it and moves it to Trusted Sources.
 * - [Available] — a plugins.json entry from a saved repo, not installed.
 * - [Errored] — trusted + installed but failed to load; carries the real reason
 *   (D-295/D-296 pattern — never silent).
 *
 * Session 2 removed the enable/disable toggle (plugins always load); session 3's
 * trust flow REPLACES that axis with a stronger semantic: trust gates code
 * execution itself (aniyomi TrustService parity). Per-plugin enable/disable
 * remains a future detail-page feature.
 */
sealed class CloudstreamExtension {

    data class Installed(
        val internalName: String,
        val name: String,
        val version: Int,
        val filePath: String,
        val repoUrl: String?,
        val language: String?,
        val iconUrl: String?,
        val isNsfw: Boolean,
        val providerCount: Int,
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val tvTypes: List<String> = emptyList(),
        val fileSizeBytes: Long? = null,
        /** The live provider details for the detail page + source picker (session 3). */
        val providers: List<CsProviderInfo> = emptyList(),
        val isDisabledByRepo: Boolean = false,
        val availableUpdateVersion: Int? = null,
    ) : CloudstreamExtension()

    data class Untrusted(
        val internalName: String,
        val name: String,
        val version: Int,
        val filePath: String,
        val repoUrl: String?,
        val language: String?,
        val iconUrl: String?,
        val isNsfw: Boolean,
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val tvTypes: List<String> = emptyList(),
        val fileSizeBytes: Long? = null,
    ) : CloudstreamExtension()

    data class Available(
        val plugin: SitePlugin,
        val repoUrl: String,
        val repoName: String,
    ) : CloudstreamExtension() {
        val isNsfw: Boolean get() = plugin.tvTypes?.any { it.equals("NSFW", ignoreCase = true) } == true
    }

    data class Errored(
        val internalName: String,
        val name: String,
        val version: Int,
        val filePath: String,
        val language: String?,
        val iconUrl: String?,
        val isNsfw: Boolean,
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val tvTypes: List<String> = emptyList(),
        val fileSizeBytes: Long? = null,
        val message: String,
    ) : CloudstreamExtension()
}

/**
 * The immutable UI view of one MainAPI provider registered by a plugin
 * (session 3 — feeds the plugin detail page's provider list AND the search
 * page's CloudStream source section). Mirrors the MainAPI members the UI
 * cares about, without exposing the live plugin object across modules.
 */
data class CsProviderInfo(
    /** MainAPI.name — the provider's display + identity name (the sourceKey component). */
    val name: String,
    /** MainAPI.mainUrl — the site root (WebView solver target + bridge baseUrl). */
    val mainUrl: String,
    /** MainAPI.lang — IETF tag. */
    val lang: String,
    /** TvType names the provider declares (supported content modes). */
    val supportedTypes: List<String>,
    /** ProviderType simple name (DirectProvider / MetaProvider / …). */
    val providerTypeName: String,
    /** Whether getMainPage is implemented (browse without a query). */
    val hasMainPage: Boolean,
    /** The provider's main-page shelf names (empty when !hasMainPage). */
    val mainPageNames: List<String>,
    /** Provider needs a WebView for some flows (Cloudflare-ish sites). */
    val usesWebView: Boolean,
)

/** Maps a live [MainAPI] into the UI-facing [CsProviderInfo] (session 3). */
object CsProviderInfoFactory {
    fun from(provider: com.lagradost.cloudstream3.MainAPI): CsProviderInfo = CsProviderInfo(
        name = provider.name,
        mainUrl = provider.mainUrl,
        lang = provider.lang,
        supportedTypes = provider.supportedTypes.map { it.name },
        providerTypeName = provider.providerType.name,
        hasMainPage = provider.hasMainPage,
        mainPageNames = if (provider.hasMainPage) provider.mainPage.map { it.name } else emptyList(),
        usesWebView = provider.usesWebView,
    )
}
