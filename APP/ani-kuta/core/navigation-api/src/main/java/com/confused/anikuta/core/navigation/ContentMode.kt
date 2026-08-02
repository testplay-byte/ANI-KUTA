package com.confused.anikuta.core.navigation

/**
 * Content mode for the app.
 * Phase 2: AnimeMode only (VIDEO content type).
 * Future: MangaMode (IMAGE), NovelMode (TEXT).
 *
 * Architecture plan §9: mode switch replaces root of List<NavKey>.
 */
sealed interface ContentMode {
    data object Anime : ContentMode
    data object Manga : ContentMode   // future
    data object Novel : ContentMode   // future
}
