package com.confused.anikuta.core.navigation

/**
 * Marker interface for all navigation keys.
 * Nav3 uses @Serializable objects as back-stack entries.
 * Type-safe: each screen defines its own NavKey with its parameters.
 *
 * Note: NOT sealed — implementations live in different Gradle modules.
 * Each implementation is @Serializable individually.
 */
interface NavKey
