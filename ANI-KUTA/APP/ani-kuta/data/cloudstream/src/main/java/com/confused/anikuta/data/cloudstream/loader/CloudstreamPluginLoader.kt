@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)

package com.confused.anikuta.data.cloudstream.loader

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import com.confused.anikuta.core.common.Logger
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import dalvik.system.PathClassLoader
import kotlinx.serialization.json.Json

/**
 * The result of loading one .cs3 plugin. Never silent — failures carry the real
 * reason (D-295/D-296 pattern; contrast upstream's toast-and-forget, doc 02 §5.3).
 */
sealed interface PluginLoadResult {
    /** Loaded OK (or was ALREADY loaded — [CloudstreamPluginLoader.loadPlugin]
     * is idempotent); providers are the MainAPI instances the plugin registered. */
    data class Success(
        val plugin: BasePlugin,
        val manifest: BasePlugin.Manifest,
        val providers: List<MainAPI>,
        val extractorCount: Int,
    ) : PluginLoadResult

    data class Failure(val reason: String, val cause: Throwable? = null) : PluginLoadResult
}

/**
 * Loads a .cs3 plugin file (doc 02 §5.3, our own implementation):
 *
 * 1. `file.setReadOnly()` — Android 14+ refuses writable dex files.
 * 2. `PathClassLoader(filePath, context.classLoader)` — PARENT-FIRST (D-294
 *    invariant; host API classes always win — the #1 structural safety property).
 * 3. Read `manifest.json` AS A CLASSLOADER RESOURCE (matches upstream semantics).
 * 4. `loadClass(manifest.pluginClassName)` → no-arg constructor → cast to BasePlugin.
 * 5. `requiresResources` → AssetManager.addAssetPath reflection trick.
 * 6. Dispatch `load(context)` (Plugin subclass) or `load()` (BasePlugin) — the
 *    plugin registers its providers into OUR APIHolder registry via the compat
 *    module's registerMainAPI.
 *
 * Task 44 (activity-context contract): the Context passed to `Plugin.load()`
 * is the LIVE MainActivity when available (upstream passes `this@MainActivity`;
 * plugins like MovieBoxProvider immediately cast it to AppCompatActivity to
 * stash it for their settings dialogs — passing the Application context was the
 * device-reported ClassCastException). Falls back to the app context only when
 * no activity is alive (process-start edge case).
 *
 * Counting registered providers before/after gives the provider list; unload
 * removes them again (the classloader itself stays alive — no dex unload on ART).
 *
 * IDEMPOTENT (device report, session 2): the manager calls `loadAll()` after
 * every state change, and `loadAll` re-loads every installed record. Loading a
 * path that is already active used to return `Failure("Plugin already loaded")`
 * — which made EVERY fresh install land in "Failed to load" and made enabling
 * a second plugin evict the first. A repeat load of an unchanged file is now a
 * [PluginLoadResult.Success] carrying the live registry state. Callers that
 * REPLACE the file at a known path (update/reinstall) must [unloadPlugin] first
 * — see CloudstreamPluginManager.installPlugin.
 */
class CloudstreamPluginLoader(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Everything remembered about one active plugin, for idempotent re-loads. */
    private data class LoadedEntry(
        val plugin: BasePlugin,
        val manifest: BasePlugin.Manifest,
        val extractorCount: Int,
    )

    /** provider-name → plugin filePath, so the manager can map back. */
    private val providerOwners = HashMap<String, String>()
    private val loadedPlugins = HashMap<String, LoadedEntry>() // filePath → entry

    fun isLoaded(filePath: String): Boolean = loadedPlugins.containsKey(filePath)

    fun providersFor(filePath: String): List<MainAPI> =
        APIHolder.allProviders.withLock {
            APIHolder.allProviders.filter { providerOwners[it.name] == filePath }
        }

    fun loadPlugin(file: java.io.File): PluginLoadResult {
        val filePath = file.absolutePath
        // Idempotent re-load of an already-active plugin: SUCCESS with the live
        // registry state (see class KDoc — the old Failure here broke installs).
        loadedPlugins[filePath]?.let { entry ->
            return PluginLoadResult.Success(
                plugin = entry.plugin,
                manifest = entry.manifest,
                providers = providersFor(filePath),
                extractorCount = entry.extractorCount,
            )
        }
        return try {
            // 1. Read-only before opening the dex (Android 14+ SecurityException guard).
            file.setReadOnly()

            // 2. Parent-first classloader over the .cs3 zip.
            val loader = PathClassLoader(filePath, context.classLoader)

            // 3. manifest.json as a classloader resource.
            val manifestStream = loader.getResourceAsStream("manifest.json")
                ?: return PluginLoadResult.Failure("No manifest.json inside ${file.name}")
            val manifestText = manifestStream.use { it.readBytes().decodeToString() }
            val manifest = with(AppUtils) { parseJson(manifestText, BasePlugin.Manifest::class) }
            val entryClass = manifest.pluginClassName
                ?: return PluginLoadResult.Failure("manifest.json has no pluginClassName")

            // 4. Instantiate the entry class.
            val instance = loader.loadClass(entryClass).getDeclaredConstructor().newInstance()
            val plugin = instance as? BasePlugin
                ?: return PluginLoadResult.Failure(
                    "$entryClass does not extend BasePlugin/Plugin",
                )

            // 5. Optional resources wiring (the documented addAssetPath mechanism).
            if (manifest.requiresResources) {
                wireResources(plugin, file)
            }

            // 6. Ownership bookkeeping + lifecycle dispatch.
            plugin.filename = filePath
            val providersBefore = APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }
            val extractorsBefore = extractorApis.withLock { extractorApis.toList() }

            if (plugin is Plugin) {
                // Task 44: the documented plugin contract — load() receives the
                // live ACTIVITY (upstream: `this@MainActivity`), because Android
                // plugins routinely cast it to AppCompatActivity. The app context
                // remains the fallback when no activity is alive yet.
                val loadContext: Context =
                    CommonActivity.activity as? Context ?: context
                Logger.i(TAG) {
                    "load(${file.name}): context=" +
                        loadContext.javaClass.simpleName
                }
                plugin.load(loadContext)
            } else {
                plugin.load()
            }

            val providersAfter = APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }
            val newProviders = providersAfter.filter { it !in providersBefore }
            val newExtractorCount = extractorApis.withLock { extractorApis.toList() }.size - extractorsBefore.size
            newProviders.forEach { provider ->
                val previousOwner = providerOwners.put(provider.name, filePath)
                // Task 50 (round 10): WARN on provider-name collisions across
                // DIFFERENT plugin files. Behavior is unchanged — last-wins,
                // exactly like upstream's name→index apiMap (later registration
                // overwrites the earlier index) — but the collision deserves a
                // log line: bridged sources resolve BY NAME (APIHolder
                // .getApiFromNameNull), so the last registered instance is the
                // one every episode resolve dispatches to, silently shadowing
                // the first plugin's provider.
                if (previousOwner != null && previousOwner != filePath) {
                    Logger.w(TAG) {
                        "plugin provider name collision: '${provider.name}' from " +
                            "${filePath.substringAfterLast('/')} overrides " +
                            "${previousOwner.substringAfterLast('/')} — bridged sources resolve " +
                            "by name; the LAST registered instance wins"
                    }
                }
            }
            loadedPlugins[filePath] = LoadedEntry(plugin, manifest, newExtractorCount)

            Logger.i(TAG) {
                "Loaded ${file.name} v${manifest.version}: ${newProviders.size} provider(s), " +
                    "$newExtractorCount extractor(s)"
            }

            PluginLoadResult.Success(
                plugin = plugin,
                manifest = manifest,
                providers = newProviders,
                extractorCount = newExtractorCount,
            )
        } catch (t: Throwable) {
            Logger.e(TAG) { "Failed to load ${file.name}: ${t::class.simpleName}: ${t.message}" }
            PluginLoadResult.Failure("${t::class.simpleName}: ${t.message}", t)
        }
    }

    /** Removes the plugin's registered providers/extractors (classloader leaks — accepted, W9). */
    fun unloadPlugin(filePath: String) {
        val entry = loadedPlugins[filePath] ?: return
        runCatching { entry.plugin.beforeUnload() }
        APIHolder.allProviders.withLock {
            APIHolder.allProviders.filter { providerOwners[it.name] == filePath }
                .forEach { provider ->
                    APIHolder.removePluginMapping(provider)
                    providerOwners.remove(provider.name)
                }
        }
        extractorApis.withLock {
            extractorApis.toList().filter { it.sourcePlugin == filePath }
                .forEach { extractorApis.remove(it) }
        }
        loadedPlugins.remove(filePath)
        Logger.i(TAG) { "Unloaded $filePath" }
    }

    /**
     * The resource-wiring trick for requiresResources plugins: reflectively add the
     * .cs3 zip as an asset path so plugin layouts/drawables resolve (doc 02 §5.3.6).
     */
    private fun wireResources(plugin: BasePlugin, file: java.io.File) {
        runCatching {
            val assetManager = AssetManager::class.java.getDeclaredConstructor().newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            addAssetPath.invoke(assetManager, file.absolutePath)
            val resources = Resources(
                assetManager,
                context.resources.displayMetrics,
                context.resources.configuration,
            )
            (plugin as? Plugin)?.resources = resources
        }.onFailure {
            Logger.w(TAG) { "Resource wiring failed for ${file.name}: ${it.message}" }
        }
    }

    companion object {
        private const val TAG = "Anikuta:Data:Cloudstream:Loader"
    }
}
