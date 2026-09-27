// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — StreamPlay and Ultima reference upstream's plugin-manager
// singleton (the dex census: getPluginsOnline / getPlugins / getPluginPath /
// loadSinglePlugin / unloadPlugin + the INSTANCE field). ANI-KUTA's real
// plugin lifecycle lives in :data:cloudstream's CloudstreamPluginManager —
// this compat anchor is INERT by design (empty registries, loadSinglePlugin
// answering false): the compat library cannot depend on the app's data layer,
// and the census plugins only touch these members for their own settings UIs,
// which degrade gracefully. Documented divergence, doc 79 §4.
package com.lagradost.cloudstream3.plugins

import android.content.Context
import java.io.File

/** The inert plugin-manager anchor (see the file header). */
object PluginManager {

    /** Maps absolute plugin file paths to their loaded instances (inert: empty). */
    val plugins: MutableMap<String, BasePlugin> = LinkedHashMap()

    /** The online-plugin registry the app-side plugin browser reads (inert: empty). */
    fun getPluginsOnline(): Array<PluginData> = emptyArray()

    /** The side-loaded (no-repository) registry (inert: empty). */
    fun getPluginsLocal(): Array<PluginData> = emptyArray()

    /**
     * Loads one plugin by its provider api name. Inert — the app's own manager
     * owns the real lifecycle; this answers "not handled" so callers fall back.
     */
    suspend fun loadSinglePlugin(context: Context, apiName: String): Boolean = false

    /** Unloads a plugin by absolute path (inert no-op). */
    fun unloadPlugin(absolutePath: String) {}

    /**
     * The deterministic install path for a repository plugin — mirrors the
     * upstream layout (filesDir/online_plugins/<repo>/<name>.cs3) so plugins
     * that compare paths keep matching their own expectations.
     */
    fun getPluginPath(
        context: Context,
        internalName: String,
        repositoryUrl: String,
    ): File {
        val folder = repositoryUrl.replace(Regex("[^a-zA-Z0-9]"), "")
        val fileName = internalName.replace(Regex("[^a-zA-Z0-9]"), "")
        return File("${context.filesDir}/online_plugins/$folder/$fileName.cs3")
    }
}
