package com.confused.anikuta.core.share

/**
 * ROUND 101 (WS-C): the share-system models.
 *
 * The user's spec — THREE sharing targets, built as their own module so the
 * sharing system stays decoupled from every other app layer:
 *
 *  1. **EXTENSION_URL** — the content's page on its extension's site. Sharing
 *     it hands the recipient a plain web link that opens the site in any
 *     browser, exactly as-is.
 *  2. **DATA_SOURCE** — the content's page on a data-source provider. Today
 *     that is AniList only (`https://anilist.co/anime/{id}`); the list-shaped
 *     [DataSourceLink] model is deliberately provider-generic so MAL / TMDb /
 *     future providers append without touching this module's consumers.
 *  3. **APP_DEEP_LINK** — the app's OWN link (`anikuta://content/{mainId}`).
 *     Opening it on a device with ANI-KUTA installed launches the app and
 *     navigates straight to the content (MainActivity resolves mainId → the
 *     existing content record → the Details page — the same resolver the
 *     notification tap uses). V1 is deliberately limited to this exact
 *     behavior; a smarter future version (web fallback page, provider
 *     negotiation) extends [ContentShareLinkFactory] without changing the
 *     share sheet's contract.
 */
enum class ShareTargetKind {
    /** The content's page on the extension's website (opens in a browser). */
    EXTENSION_URL,

    /** The content's page on a data-source provider (AniList today). */
    DATA_SOURCE,

    /** ANI-KUTA's own deep link (opens the app on the content). */
    APP_DEEP_LINK,
}

/**
 * A data-source provider's page for the shared content (provider-generic so
 * MAL / TMDb / future providers are additive).
 *
 * @param providerId Stable provider id ("anilist", "mal", "tmdb", …).
 * @param providerName Human-readable provider name for the share sheet row.
 * @param url The ABSOLUTE web URL of the content on that provider.
 */
data class DataSourceLink(
    val providerId: String,
    val providerName: String,
    val url: String,
)

/**
 * The share system's INPUT — everything the details page knows about the
 * content, projected into link-building terms. Field availability decides
 * which targets exist (a null extensionUrl hides the extension row, etc.).
 *
 * @param title Display title — rides along in every share payload.
 * @param mainId The content's stable main_id ("" when unknown — hides the
 *   deep-link target).
 * @param extensionUrl The content's ABSOLUTE page URL on the extension's
 *   site (the caller joins the source's baseUrl with the content's url —
 *   see DetailsViewModel.buildShareContent). Null when no extension source
 *   is linked.
 * @param extensionSourceName The extension source's display name (subtitle
 *   of the extension row).
 * @param dataSourceLinks The provider pages (empty when nothing is linked).
 */
data class ShareableContent(
    val title: String,
    val mainId: String = "",
    val extensionUrl: String? = null,
    val extensionSourceName: String? = null,
    val dataSourceLinks: List<DataSourceLink> = emptyList(),
)

/**
 * The share system's OUTPUT — one row of the share sheet.
 *
 * @param kind Which of the three systems produced this link.
 * @param url The link itself (what lands in the OS share sheet).
 * @param label The primary row label ("Extension link", "AniList", "ANI-KUTA link").
 * @param description The secondary row text (the source name / what happens).
 */
data class ShareLink(
    val kind: ShareTargetKind,
    val url: String,
    val label: String,
    val description: String,
)
