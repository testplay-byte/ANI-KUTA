package com.confused.anikuta.core.share

import org.koin.dsl.module

/**
 * Koin DI module for :core:share.
 *
 * The factory is a stateless object (no dependencies to inject today); the
 * module exists so the share system is a first-class registrable module —
 * future stateful share services (a share-history recorder, a short-link
 * resolver) land here without touching the app's wiring shape.
 */
val shareModule = module {
    // ContentShareLinkFactory is an object — nothing to construct yet.
}
