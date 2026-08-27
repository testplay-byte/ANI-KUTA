package com.confused.anikuta.data.extension.provider

import com.confused.anikuta.core.common.ContentType
import com.confused.anikuta.core.providerapi.SourceDescriptor
import com.confused.anikuta.core.providerapi.VideoExtensionProvider
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.model.AnimeExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Aniyomi-compatible implementation of [VideoExtensionProvider] (D-302).
 *
 * This is the app's official facade over the Aniyomi extension ecosystem
 * (previously `:core:provider-api` was scaffolded with zero implementations and
 * every consumer bound directly to the Aniyomi-specific [ExtensionManager]).
 * Existing consumers keep working unchanged; NEW consumers should depend on
 * [VideoExtensionProvider] so a second ecosystem can be added later without
 * touching feature code.
 *
 * The provider adapts the manager's `AnimeSource` registry into the app-owned
 * [SourceDescriptor] model — no third-party types cross this boundary.
 */
class AniyomiExtensionProvider(
    private val manager: ExtensionManager,
) : VideoExtensionProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val ecosystemId: String = "aniyomi"
    override val displayName: String = "Aniyomi extensions"
    override val supportedContentTypes: Set<ContentType> = setOf(ContentType.VIDEO)

    private val _sources = MutableStateFlow<List<SourceDescriptor>>(emptyList())
    override val sources: StateFlow<List<SourceDescriptor>> = _sources.asStateFlow()

    init {
        // Mirror the manager's source registry into app-owned descriptors.
        scope.launch {
            manager.sources.collect { registry ->
                _sources.value = registry.values
                    .map { source ->
                        SourceDescriptor(
                            id = source.id,
                            name = source.name,
                            lang = source.lang,
                            isEnabled = true,
                        )
                    }
                    .sortedBy { it.name.lowercase() }
            }
        }
    }

    override fun findSource(id: Long): SourceDescriptor? =
        _sources.value.find { it.id == id }

    override fun install(pkgName: String) {
        // Resolve the newest Available entry for the package and install it.
        val available = manager.availableExtensions.value.find { it.pkgName == pkgName }
        if (available == null) {
            // Not in a configured repo — trigger an update check; the install can
            // be re-attempted once the repo index is fresh.
            manager.checkForUpdates()
            return
        }
        scope.launch {
            manager.installExtension(available).collect { }
        }
    }

    override fun uninstall(pkgName: String) {
        // The manager's uninstall needs an AnimeExtension instance — resolve it
        // from the installed list; fall back to the system uninstall by pkgName.
        val installed = manager.installedExtensions.value.find { it.pkgName == pkgName }
        if (installed != null) {
            manager.uninstallExtension(installed)
        } else {
            manager.installer.uninstallApk(pkgName)
        }
    }

    override fun setEnabled(pkgName: String, enabled: Boolean) {
        if (enabled) manager.enableExtension(pkgName) else manager.disableExtension(pkgName)
    }

    override fun checkForUpdates() {
        manager.checkForUpdates(force = true)
    }
}
