package com.confused.anikuta.core.notifications

import android.graphics.Bitmap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * D-500: the IN-APP heads-up banner — the seam that lets the app SHOW the
 * episode banner while the user is on ANY screen, instead of only posting a
 * (silently-arriving) system notification.
 *
 * # The device round that asked for it
 *
 * The user's v1.1.13 report: the test notification "showed the notification
 * itself properly but it apparently did not show the banner alongside it",
 * and "it never shows banner notifications at all… the application [should]
 * show the banner whenever the user is on any screen, rather than just
 * silently giving a notification." The system-notification side was already
 * correct (BigPictureStyle); what the app NEVER did was surface the composed
 * banner INSIDE the app. This controller is that missing half.
 *
 * # How it works (deliberately boring)
 *
 * [NotificationManager] emits an [InAppBannerEvent] right after every episode
 * notification post (real + test, both post paths) — carrying the SAME
 * composed banner bitmap the system notification renders, so the in-app card
 * is a faithful double of the notification, not a re-composition.
 *
 * Delivery semantics: a SharedFlow with replay=0 and a lifecycle-aware
 * collector in the app's root composable. An event emitted while the app is
 * BACKGROUND has no STARTED collector and is simply dropped — the system
 * notification already covers that case, and a stale banner greeting the user
 * on the next app-open would be wrong. Foreground-only by construction.
 *
 * Bitmap ownership: the composer hands over a fully-composed ARGB_8888 bitmap;
 * it is never mutated after emission, so the cross-thread hand-off is safe.
 */
data class InAppBannerEvent(
    val id: Long,
    val bitmap: Bitmap?,   // null → the card renders the text-only layout
    val title: String,
    val text: String,
    val mainId: String,
)

class InAppBannerController {

    private val _events = MutableSharedFlow<InAppBannerEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<InAppBannerEvent> = _events

    /**
     * Fire-and-forget emission — never blocks, never throws, and a missing
     * collector (app backgrounded) silently drops the event. Notification
     * posting must NEVER fail over the in-app banner.
     */
    fun show(event: InAppBannerEvent) {
        _events.tryEmit(event)
    }
}
