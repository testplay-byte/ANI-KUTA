package com.confused.anikuta.feature.extensionssettings

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * Nav3 key for the Extensions Settings screen.
 */
@Serializable
object ExtensionsSettingsKey : NavKey

/**
 * Nav3 key for the Extension Repo Settings screen.
 */
@Serializable
object ExtensionRepoSettingsKey : NavKey

/**
 * Nav3 key for the Auto-Link Settings screen (Phase B).
 *
 * Lets the user configure:
 * - Global auto-link toggle (master switch).
 * - Match strategy (Fuzzy / Strict / Manual).
 * - Fuzzy threshold (0.50 - 1.00).
 * - Per-extension overrides (Default / Always link / Never link).
 */
@Serializable
object AutoLinkSettingsKey : NavKey
