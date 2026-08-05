package com.confused.anikuta.core.common

/**
 * Content types supported by ANI-KUTA.
 *
 * Phase 2: VIDEO only (anime).
 * Future: IMAGE (manga), TEXT (novels).
 *
 * Architecture plan §9: per-content-type feature modules.
 */
enum class ContentType {
    VIDEO,  // anime, movies, series
    IMAGE,  // manga (future)
    TEXT,   // novels (future)
}
