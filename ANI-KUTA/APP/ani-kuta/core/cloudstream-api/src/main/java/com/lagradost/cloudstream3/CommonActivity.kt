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

enum class FocusDirection {
    Start,
    End,
    Up,
    Down,
}

object CommonActivity {

    private var _activity: java.lang.ref.WeakReference<Activity>? = null

    var activity: Activity?
        get() = _activity?.get()
        private set(value) {
            _activity = java.lang.ref.WeakReference(value)
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
