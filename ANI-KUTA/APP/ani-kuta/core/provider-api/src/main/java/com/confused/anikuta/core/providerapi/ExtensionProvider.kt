package com.confused.anikuta.core.providerapi

import com.confused.anikuta.core.common.ContentType
import kotlinx.coroutines.flow.StateFlow

/**
 * The base contract for all extension providers.
 *
 * An extension provider is a system that loads and manages content sources
 * from a specific ecosystem. Each ecosystem (e.g., the Aniyomi-compatible
 * extension ecosystem) gets one provider implementation.
 *
 * This interface is the app's own abstraction — it does NOT expose any
 * third-party types. The [VideoExtensionProvider], [ImageExtensionProvider],
 * and [TextExtensionProvider] sub-interfaces add content-type-specific methods.
 *
 * Architecture plan §8 (C1 fix): split into per-content-type sub-interfaces
 * so a provider can implement only the content types it supports.
 */
sealed interface ExtensionProvider {

    /** Unique identifier for this ecosystem (e.g., "aniyomi", "mangayomi"). */
    val ecosystemId: String

    /** Human-readable name for display in settings. */
    val displayName: String

    /** Which content types this provider supports. */
    val supportedContentTypes: Set<ContentType>
}

/**
 * A source exposed through the provider abstraction — the app-owned view of a
 * content source, free of any third-party types (D-302).
 *
 * Consumers that only need "what sources exist + open one" should depend on
 * this model instead of the Aniyomi-specific [eu.kanade.tachiyomi.animesource.AnimeSource].
 */
data class SourceDescriptor(
    /** Stable identifier within the provider's ecosystem. */
    val id: Long,
    /** Display name. */
    val name: String,
    /** ISO 639-1 language code (blank when unspecified). */
    val lang: String,
    /** Whether the source can appear in the standard source picker. */
    val isEnabled: Boolean,
)

/**
 * Video-content extension provider (D-302).
 *
 * This is the app-facing facade over an extension ecosystem's source registry.
 * Exactly one implementation exists today — the Aniyomi-compatible ecosystem
 * (`AniyomiExtensionProvider` in `:data:extension`) — but new consumers
 * (settings UIs, pickers, future ecosystems like Mangayomi/Sora/CloudStream)
 * program against THIS interface so the ecosystem can be swapped or added
 * without touching feature code.
 *
 * All operations are non-blocking: state arrives through [sources] (a
 * [StateFlow], CORE_RULES §23) and mutations return immediately, with the
 * result landing in the flow.
 */
interface VideoExtensionProvider : ExtensionProvider {

    /** The live set of usable sources (enabled + loaded). */
    val sources: StateFlow<List<SourceDescriptor>>

    /** Look up one source by its ecosystem id (null when unknown/disabled). */
    fun findSource(id: Long): SourceDescriptor?

    /**
     * Install (or update) an extension by package name from its repository.
     * Implementations handle download + PackageInstaller dispatch; the terminal
     * state arrives asynchronously via the provider's flows.
     */
    fun install(pkgName: String)

    /** Uninstall an extension by package name. */
    fun uninstall(pkgName: String)

    /** Enable/disable a package's sources without uninstalling. */
    fun setEnabled(pkgName: String, enabled: Boolean)

    /** Trigger an update check against the configured repositories. */
    fun checkForUpdates()
}
