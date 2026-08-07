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
    single { ContentResolver(get()) }
    single { ContentSeeder(get()) }
}

/**
 * Helper class that seeds the lookup tables + Default library category.
 * Called from [com.confused.anikuta.AnikutaApp.onCreate] after Koin starts.
 */
class ContentSeeder(private val repo: ContentRepository) {
    fun seed() {
        repo.seedDefaults()
    }
}
