// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Session-1 note (doc 23 §4): the video-click-action surface is provided so plugins
// that register actions LOAD cleanly; no host UI consumes them yet (zero census
// plugins implement VideoClickAction — this is shape-completeness, not a feature).
package com.lagradost.cloudstream3.actions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import com.lagradost.cloudstream3.ui.result.LinkLoadingResult
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.atomicListOf
import java.util.concurrent.Callable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object VideoClickActionHolder {
    val allVideoClickActions = atomicListOf<VideoClickAction>()

    private const val ACTION_ID_OFFSET = 1000

    fun getActionById(id: Int): VideoClickAction? =
        allVideoClickActions.getOrNull(id - ACTION_ID_OFFSET)

    fun getByUniqueId(uniqueId: String): VideoClickAction? =
        allVideoClickActions.firstOrNull { it.uniqueId() == uniqueId }

    fun uniqueIdToId(uniqueId: String?): Int? {
        if (uniqueId == null) return null
        val index = allVideoClickActions.indexOfFirst { it.uniqueId() == uniqueId }
        return if (index >= 0) index + ACTION_ID_OFFSET else null
    }

    fun getPlayers(activity: Activity? = null): List<VideoClickAction> =
        allVideoClickActions.filter { it.isPlayer }
}

abstract class VideoClickAction {
    abstract val name: UiText

    /** If true, the app will show dialog to select source - result.links[index]. */
    open val oneSource: Boolean = false

    /** If true, this action could be selected as default player (one press action) in settings. */
    open val isPlayer: Boolean = false

    /** Which type of sources this action can handle. */
    open val sourceTypes: Set<ExtractorLinkType> = ExtractorLinkType.entries.toSet()

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    /** Runs [callable] on the UI thread, bubbling up exceptions. */
    @Throws
    suspend fun <T> uiThread(callable: Callable<T>): T? =
        withContext(Dispatchers.Main) { callable.call() }

    @Throws
    suspend fun launchResult(intent: Intent?, options: ActivityOptionsCompat? = null) {
        // Not wired in this host (session 1).
    }

    @Throws
    suspend fun launch(intent: Intent?, bundle: Bundle? = null) {
        // Not wired in this host (session 1).
    }

    fun uniqueId(): String = "$sourcePlugin:${this::class.qualifiedName}"

    @Throws
    abstract fun shouldShow(context: Context?, video: ResultEpisode?): Boolean

    /** Safe version of shouldShow — we don't trust extension devs to handle exceptions. */
    fun shouldShowSafe(context: Context?, video: ResultEpisode?): Boolean =
        runCatching { shouldShow(context, video) }.getOrDefault(false)

    /**
     * This function is called when the action is clicked.
     * @param context The current activity
     * @param video The episode/movie that was clicked
     * @param result The result of the link loading, contains video & subtitle links
     * @param index if oneSource is true, this is the index of the selected source
     */
    @Throws
    abstract suspend fun runAction(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?)

    /** Safe version of runAction — we don't trust extension devs to handle exceptions. */
    fun runActionSafe(context: Context?, video: ResultEpisode, result: LinkLoadingResult, index: Int?) {
        // Not wired in this host (session 1).
    }
}
