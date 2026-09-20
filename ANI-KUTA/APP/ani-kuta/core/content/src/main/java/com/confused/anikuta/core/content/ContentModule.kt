package com.confused.anikuta.core.content

import org.koin.dsl.module

/**
 * Koin DI module for :core:content.
 *
 * Provides:
 * - [ContentRepository] — DB CRUD operations.
 * - [ContentResolver] — resolves external IDs to mainId.
 * - [ContentSeeder] — seeds lookup tables + Default library category.
 *
 * CORE_RULES §20: All operations logged via the repository's own tag.
 */
val contentModule = module {
    single { ContentRepository(get()) }
    // D-540: the ContentIdentitySync hook binds to the download manager (it
    // implements the interface) — getOrNull keeps test graphs (no download
    // module) working with the reconciliation disabled.
    single { ContentResolver(get(), get(), identitySync = getOrNull()) }
    single { ContentSeeder(get(), get()) }
    single { com.confused.anikuta.core.content.genre.GenreRepository(get()) }
}

/**
 * Helper class that seeds the lookup tables + Default library category.
 * Called from [com.confused.anikuta.AnikutaApp.onCreate] after Koin starts.
 */
class ContentSeeder(
    private val repo: ContentRepository,
    private val genreRepo: com.confused.anikuta.core.content.genre.GenreRepository,
) {
    fun seed() {
        repo.seedDefaults()
        genreRepo.seedIfEmpty()
    }
}
