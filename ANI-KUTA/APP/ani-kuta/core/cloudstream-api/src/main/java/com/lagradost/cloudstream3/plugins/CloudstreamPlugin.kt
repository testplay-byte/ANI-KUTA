// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.plugins

/**
 * Build-time marker annotation on the plugin entry class (referenced by ALL plugins'
 * dex type tables). Runtime retention means a loader *could* scan for it — ours, like
 * upstream's, uses manifest.json's pluginClassName instead (doc 02 §2).
 */
@Suppress("unused")
@Target(AnnotationTarget.CLASS)
annotation class CloudstreamPlugin
