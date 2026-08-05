package com.confused.anikuta.core.smartmatcher

import com.confused.anikuta.core.preferences.AutoLinkPreferences
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin DI module for :core:smart-matcher.
 *
 * Provides:
 * - [SmartMatcher] — pure title matcher.
 * - [AutoLinkService] — orchestrator (depends on AniListApi + AutoLinkPreferences).
 *
 * CORE_RULES §20: All operations logged via the service's own tag.
 */
val smartMatcherModule = module {
    singleOf(::SmartMatcher)
    singleOf(::AutoLinkService)
}
