package com.confused.anikuta.feature.extensionssettings

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * Nav3 key for the Extensions Settings screen.
 *
 * Task 60 (round 20): carries the tab the screen should open on. Default
 * "aniyomi" (the built-in behavior); the post-plugin-import hand-off pushes
 * "cloudstream" so landing after a CloudStream plugin Add shows the
 * CLOUDSTREAM section (the user's round-20 spec: "I added the cloud streaming
 * plugin so it should lead me to the cloud stream section by default").
 * A @Serializable data class with all-default params — payloads persisted by
 * the old `object` form (an empty object) still decode.
 */
@Serializable
data class ExtensionsSettingsKey(
    /** "aniyomi" (default) or "cloudstream". */
    val initialTab: String = "aniyomi",
) : NavKey

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

/**
 * Phase 4: NavKey for the Extension Detail screen.
 * Shows the extension's logo, version, package ID, enable/disable toggle,
 * uninstall button, app info button, + the list of sources (with per-source
 * settings buttons if the source is ConfigurableAnimeSource).
 */
@Serializable
data class ExtensionDetailKey(val pkgName: String) : NavKey

/**
 * Phase 4: NavKey for the Source Preferences screen.
 * Renders the extension's own settings via PreferenceFragmentCompat.
 * (Placeholder for now — the full PreferenceFragmentCompat integration is pending.)
 */
@Serializable
data class SourcePreferencesKey(val sourceId: Long) : NavKey

/**
 * Session 3 (device round 2): NavKey for the CloudStream PLUGIN Detail screen.
 * Identified by internalName — the screen resolves the plugin across the
 * manager's states (Trusted / Untrusted / Failed to load / Available) and
 * renders its metadata (description, authors, version, status, size, supported
 * modes, language, live providers) + the actions valid for that state.
 */
@Serializable
data class CloudstreamPluginDetailKey(val internalName: String) : NavKey

/**
 * Round 82 (D-576): NavKey for the EXTENSION TESTING screen.
 *
 * Round 84 (D-583): this key is the HOME of a FIVE-PAGE testing system —
 * Home (here) → per-system target list → the live Run page → a target's
 * result detail → Statistics. The other four pages carry their own keys
 * below (all follow the same all-default/String-payload serialization
 * pattern as [ExtensionsSettingsKey]).
 */
@Serializable
object ExtensionTestingKey : NavKey

/**
 * Round 84 (D-583): NavKey for the per-system TARGET LIST — the clean,
 * checkbox-free list of one ecosystem's testable targets (the round-84
 * report: "there were no options to select which extension system I wanted
 * to test out").
 *
 * @param ecosystem "aniyomi" or "cloudstream".
 */
@Serializable
data class ExtensionTestingListKey(val ecosystem: String = "aniyomi") : NavKey

/**
 * Round 84 (D-583): NavKey for the dedicated RUN PAGE — the full-screen live
 * run experience (progress + per-kind rows + Stop + Skip), replacing the
 * round-83 in-screen overlay. The payload is only the REQUEST — the page
 * renders whatever the run controller's live session says.
 *
 * @param targetIdsCsv comma-separated target ids; empty = every target.
 */
@Serializable
data class ExtensionTestingRunKey(val targetIdsCsv: String = "") : NavKey

/**
 * Round 84 (D-583): NavKey for one target's RESULT DETAIL — the full seven
 * test verdicts with durations, messages and per-target re-runs.
 */
@Serializable
data class ExtensionTestingTargetKey(val targetId: Long = 0L) : NavKey

/**
 * Round 84 (D-583): NavKey for the TESTING STATISTICS page — totals, per-
 * system pass rates, per-kind durations/failure rates and the run history
 * (the report: "there should be dedicated stats page for it").
 */
@Serializable
object ExtensionTestingStatsKey : NavKey
