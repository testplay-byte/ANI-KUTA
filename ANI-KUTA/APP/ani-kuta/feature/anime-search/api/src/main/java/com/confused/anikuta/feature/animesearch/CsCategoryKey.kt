package com.confused.anikuta.feature.animesearch

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * Task 61 (round 21 — the category subpages): the NavKey for one CloudStream
 * provider shelf's OWN page. Tapping a section title on the search page
 * (ExtensionBrowseSuccess) navigates here; the page shows the category's
 * heading + a full grid of its results with infinite scroll (paged
 * MainAPI.getMainPage).
 *
 * @param providerName the MainAPI provider name (the shelf's owner).
 * @param sectionTitle the shelf's display name — the page heading.
 * @param shelfIndex the shelf's ORIGINAL index in the provider's mainPage
 *   list (captured BEFORE the search page's random section shuffle — the
 *   subpage resolves its shelf by this index to page it).
 */
@Serializable
data class CsCategoryKey(
    val providerName: String,
    val sectionTitle: String,
    val shelfIndex: Int,
) : NavKey
