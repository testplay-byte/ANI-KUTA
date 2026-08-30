// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.utils.atomicListOf

/**
 * The Android-side plugin base class (20/80 census plugins extend this; the rest
 * extend the cross-platform BasePlugin). Adds context-aware load + optional
 * resources + a settings hook. Referenced by our loader when dispatching lifecycle.
 */
abstract class Plugin : BasePlugin() {

    /**
     * Called when your Plugin is loaded.
     * @param context Context
     */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        // If not overridden by an extension then try the cross-platform load()
        load()
    }

    private val videoClickActions = atomicListOf<VideoClickAction>()

    /**
     * Used to register VideoClickAction instances (long-press player actions).
     */
    fun registerVideoClickAction(element: VideoClickAction) {
        element.sourcePlugin = this.filename
        videoClickActions.add(element)
    }

    /**
     * This will contain your resources if you specified requiresResources in gradle.
     */
    var resources: Resources? = null

    /**
     * This will add a button in the settings allowing you to add custom settings.
     */
    var openSettings: ((context: Context) -> Unit)? = null
}
