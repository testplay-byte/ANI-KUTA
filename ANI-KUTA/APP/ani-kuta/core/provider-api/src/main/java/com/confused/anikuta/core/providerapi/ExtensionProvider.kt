package com.confused.anikuta.core.providerapi

import com.confused.anikuta.core.common.ContentType

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
