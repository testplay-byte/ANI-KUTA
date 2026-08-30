// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// SKELETON (doc 23 §4): the app-side activity holder (16/80 census plugins reference
// it, mostly for context/toast access). Cast-session plumbing is omitted (no GMS in
// this host). Our app sets the activity instance from its own MainActivity.
@file:Suppress("ktlint")

package com.lagradost.cloudstream3

import android.app.Activity
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.Toast
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FocusDirection {
    Start,
    End,
    Up,
    Down,
}

object CommonActivity {

    // Task 46 (device round 5, ANI-KUTA addition — NOT part of the mirrored
    // upstream surface): the activity reference ALSO exposed as a StateFlow so
    // the plugin manager can SUSPEND the first .cs3 load until an Activity is
    // alive. Root cause this fixes: manager init{} ran loadAll() synchronously
    // inside Application.onCreate — before MainActivity exists — so plugins
    // that cast their load(context) to AppCompatActivity (the MovieBoxProvider
    // pattern) threw ClassCastException on EVERY cold start ("trusted it, quit,
    // reopened → Failed to load"). Plugins stay untouched; the host now simply
    // waits for the activity the same way upstream CloudStream effectively does
    // (its plugins load while MainActivity is alive).
    private val _activityFlow = MutableStateFlow<Activity?>(null)

    /** See [_activityFlow] — emits the current activity (null while none is live). */
    val activityFlow: StateFlow<Activity?> = _activityFlow.asStateFlow()

    private var _activity: java.lang.ref.WeakReference<Activity>? = null

    var activity: Activity?
        get() = _activityFlow.value
        private set(value) {
            _activity = java.lang.ref.WeakReference(value)
            _activityFlow.value = value
        }

    @androidx.annotation.MainThread
    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
    }

    val displayMetrics: DisplayMetrics = Resources.getSystem().displayMetrics

    // screenWidth and screenHeight always refer to the screen while in landscape mode.
    val screenWidth: Int
        get() = if (displayMetrics.widthPixels > displayMetrics.heightPixels) {
            displayMetrics.widthPixels
        } else {
            displayMetrics.heightPixels
        }

    val screenHeight: Int
        get() = if (displayMetrics.widthPixels > displayMetrics.heightPixels) {
            displayMetrics.heightPixels
        } else {
            displayMetrics.widthPixels
        }

    val screenWidthWithOrientation: Int
        get() = displayMetrics.widthPixels

    val screenHeightWithOrientation: Int
        get() = displayMetrics.heightPixels

    var isPipDesired: Boolean = false
    var isInPIPMode: Boolean = false

    val onColorSelectedEvent = Event<Pair<Int, Int>>()
    val onDialogDismissedEvent = Event<Int>()

    var keyEventListener: ((Pair<KeyEvent?, Boolean>) -> Boolean)? = null
    var appliedTheme: Int = 0
    var appliedColor: Int = 0

    private var currentToast: Toast? = null

    fun showToast(message: String?, duration: Int? = null) {
        val activity = activity ?: return
        val appContext = activity.applicationContext
        Handler(Looper.getMainLooper()).post {
            currentToast?.cancel()
            currentToast = Toast.makeText(appContext, message ?: return@post, duration ?: Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    fun showToast(message: UiText?, duration: Int? = null) {
        val activity = activity ?: return
        showToast(message?.asString(activity), duration)
    }
}
