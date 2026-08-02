package com.confused.anikuta.data.extension.manager

import android.content.Context
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.data.extension.loader.ExtensionLoader
import com.confused.anikuta.data.extension.model.Extension
import com.confused.anikuta.data.extension.model.LoadResult
import com.confused.anikuta.data.extension.trust.TrustService
import eu.kanade.tachiyomi.animesource.AnimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages installed extensions and their sources.
 *
 * This is the central hub for the extension system. It:
 * - Loads installed extensions on app start.
 * - Maintains a registry of all available [AnimeSource] instances.
 * - Exposes reactive state (CORE_RULES §23) so the UI updates when extensions change.
 * - Handles trust verification (untrusted extensions are flagged, not loaded).
 *
 * The manager does NOT handle installation/uninstallation — that's [ExtensionInstaller].
 * The manager does NOT fetch extension lists from repos — that's [ExtensionRepoApi].
 *
 * CORE_RULES §20: All operations logged with tag "Anikuta:Data:Extension:Manager".
 */
class ExtensionManager(
    private val context: Context,
    private val trustService: TrustService,
) {

    companion object {
        private const val TAG = "Anikuta:Data:Extension:Manager"
    }

    private val loader = ExtensionLoader(context)

    // ── Reactive state (CORE_RULES §23 — live updates) ────────────────────────

    private val _installedExtensions = MutableStateFlow<List<Extension>>(emptyList())
    val installedExtensions: StateFlow<List<Extension>> = _installedExtensions.asStateFlow()

    private val _untrustedExtensions = MutableStateFlow<List<Extension>>(emptyList())
    val untrustedExtensions: StateFlow<List<Extension>> = _untrustedExtensions.asStateFlow()

    private val _sources = MutableStateFlow<Map<Long, AnimeSource>>(emptyMap())
    val sources: StateFlow<Map<Long, AnimeSource>> = _sources.asStateFlow()

    // ── Loading ────────────────────────────────────────────────────────────────

    /**
     * Load all installed extensions. Called on app start.
     *
     * Extensions are split into:
     * - Trusted: their sources are loaded and available.
     * - Untrusted: their fingerprint needs user confirmation.
     */
    fun loadAll() {
        Logger.i(TAG) { "Loading all extensions..." }

        val results = loader.loadAll()
        val trusted = mutableListOf<Extension>()
        val untrusted = mutableListOf<Extension>()
        val sourceMap = mutableMapOf<Long, AnimeSource>()

        for (result in results) {
            when (result) {
                is LoadResult.Success -> {
                    val ext = result.extension
                    if (trustService.isTrusted(ext.signatureFingerprint)) {
                        trusted.add(ext)
                        ext.sources.forEach { source ->
                            sourceMap[source.id] = source
                            Logger.d(TAG) { "Registered source: ${source.name} (id=${source.id})" }
                        }
                    } else {
                        Logger.w(TAG) { "Extension ${ext.name} is untrusted (fingerprint: ${ext.signatureFingerprint})" }
                        untrusted.add(ext)
                    }
                }
                is LoadResult.Error -> {
                    Logger.e(TAG) { "Failed to load ${result.packageName}: ${result.message}" }
                }
                is LoadResult.Untrusted -> {
                    Logger.w(TAG) { "Extension ${result.packageName} is untrusted" }
                }
            }
        }

        _installedExtensions.value = trusted
        _untrustedExtensions.value = untrusted
        _sources.value = sourceMap

        Logger.i(TAG) { "Loaded ${trusted.size} trusted extensions (${sourceMap.size} sources), ${untrusted.size} untrusted" }
    }

    /**
     * Trust an untrusted extension and load its sources.
     */
    fun trustExtension(extension: Extension) {
        Logger.i(TAG) { "Trusting extension: ${extension.name}" }

        extension.signatureFingerprint?.let { trustService.trust(it) }

        // Move from untrusted to installed
        _untrustedExtensions.value = _untrustedExtensions.value.filter { it.packageName != extension.packageName }

        // Register its sources
        val sourceMap = _sources.value.toMutableMap()
        extension.sources.forEach { source ->
            sourceMap[source.id] = source
        }
        _sources.value = sourceMap
        _installedExtensions.value = _installedExtensions.value + extension
    }

    /**
     * Get a source by ID.
     */
    fun getSource(id: Long): AnimeSource? = _sources.value[id]

    /**
     * Get all available sources.
     */
    fun getAllSources(): List<AnimeSource> = _sources.value.values.toList()

    /**
     * Reload extensions (after an install/uninstall).
     */
    fun reload() {
        Logger.i(TAG) { "Reloading extensions..." }
        loadAll()
    }
}
