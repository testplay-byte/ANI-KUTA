// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// BasePlugin is the class every .cs3 entry class extends (60/80 census plugins;
// the remaining 20 extend the Android-side Plugin subclass). registerMainAPI /
// registerExtractorAPI feed OUR runtime registry (APIHolder + extractorApis) —
// that is how the loader collects the providers a plugin registers on load().
@file:Suppress("DEPRECATION_ERROR")

package com.lagradost.cloudstream3.plugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PLUGIN_TAG = "PluginInstance"

abstract class BasePlugin {

    /**
     * Used to register provider instances of MainAPI.
     * @param element MainAPI provider you want to register
     */
    fun registerMainAPI(element: MainAPI) {
        element.sourcePlugin = this.filename
        APIHolder.addPluginMapping(element)
    }

    /**
     * Used to register extractor instances of ExtractorApi.
     * @param element ExtractorApi you want to register
     */
    fun registerExtractorAPI(element: ExtractorApi) {
        element.sourcePlugin = this.filename
        extractorApis.add(element)
    }

    /**
     * Called when your Plugin is being unloaded.
     */
    @Throws(Throwable::class)
    open fun beforeUnload() {
    }

    /**
     * Called when your Plugin is loaded.
     */
    @Throws(Throwable::class)
    open fun load() {
    }

    /** Full file path to the plugin. */
    @Deprecated(
        "Renamed to `filename` to follow conventions",
        replaceWith = ReplaceWith("filename"),
        level = DeprecationLevel.ERROR,
    )
    var __filename: String?
        get() = filename
        set(value) {
            filename = value
        }

    var filename: String? = null

    /** The manifest.json model inside every .cs3 (doc 02 §1.6). */
    @Serializable
    class Manifest {
        @JsonProperty("name") @SerialName("name")
        var name: String? = null

        @JsonProperty("pluginClassName") @SerialName("pluginClassName")
        var pluginClassName: String? = null

        @JsonProperty("requiresResources") @SerialName("requiresResources")
        var requiresResources: Boolean = false

        @JsonProperty("version") @SerialName("version")
        var version: Int? = null
    }
}
