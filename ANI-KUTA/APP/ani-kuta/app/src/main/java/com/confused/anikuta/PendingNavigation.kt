package com.confused.anikuta

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ROUND 102 (WS-D): the UNIFIED PENDING-NAVIGATION INTAKE — the reactive
 * channel that finally makes the app detect its own links, cold AND warm.
 *
 * THE BUG IT FIXES (the v1.1.58 device report: "the app did not even detect
 * its own links"): the round-101 intake read the Activity's intent ONCE via
 * a `remember {}` block inside the composition. That only ever sees the
 * COLD-START intent — a WARM delivery (app already running, user taps an
 * `anikuta://content/…` link, a notification, or the AniList OAuth redirect)
 * arrived through `onNewIntent`, which nothing re-read. With the manifest's
 * new `singleTask` launch mode those warm deliveries route to the EXISTING
 * activity instance — so the intake had to become reactive to work AT ALL.
 *
 * THE SHAPE (the SearchTabExitSignal singleton-signal pattern):
 *  - MainActivity.onCreate AND onNewIntent both call [offerFromIntent] —
 *    every entry path funnels into ONE parser; the intent is STRIPPED as it
 *    is parsed (data nulled + extras removed), so a config-change recreation
 *    or recomposition can never re-offer the same delivery.
 *  - AppRoot COLLECTS [pending] and performs the navigation (the content
 *    resolver lives there), then calls [consume] — exactly-once semantics.
 *
 * The three intake sources (all additive — a future source is one more
 * field + one more parser line):
 *  1. the app's own share deep link — `anikuta://content/{mainId}` (the
 *     :core:share ContentShareLinkFactory target);
 *  2. the notification tap — the `notification_main_id` extra (D-193) or the
 *     `open_update_history` flag (D-388);
 *  3. (handled separately, no navigation) the AniList OAuth redirect.
 */
object PendingNavigation {

    /** One parsed delivery, awaiting the composition's handling. */
    data class Request(
        /** `anikuta://content/{mainId}` — open the app on that content. */
        val shareMainId: String? = null,
        /** The notification-tap content (its `notification_main_id` extra). */
        val notificationMainId: String? = null,
        /** The update-check notification's "open the history page" flag. */
        val openUpdateHistory: Boolean = false,
    )

    private val _pending = MutableStateFlow<Request?>(null)

    /** The current un-handled request (null = nothing pending). */
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    /**
     * Parses + records a delivery from an Activity intent, then STRIPS the
     * intent so the same delivery can never be offered twice (rotation,
     * recomposition, a duplicate onNewIntent). Called from onCreate AND
     * onNewIntent — the only two places an intent enters a singleTask
     * activity.
     */
    fun offerFromIntent(intent: android.content.Intent?) {
        if (intent == null) return

        var request: Request? = null

        // 1 — the app's own share deep link (anikuta://content/{mainId}).
        val deepLinkMainId =
            com.confused.anikuta.core.share.ContentShareLinkFactory.parseDeepLink(intent.dataString)
        if (!deepLinkMainId.isNullOrBlank()) {
            request = (request ?: Request()).copy(shareMainId = deepLinkMainId)
        }

        // 2a — the notification-tap content.
        val notifMainId = intent.getStringExtra("notification_main_id")
        if (!notifMainId.isNullOrBlank()) {
            request = (request ?: Request()).copy(notificationMainId = notifMainId)
        }

        // 2b — the update-check notification's history flag.
        if (intent.getBooleanExtra("open_update_history", false)) {
            request = (request ?: Request()).copy(openUpdateHistory = true)
        }

        // Strip the parsed delivery markers off the intent (the parse has
        // already captured everything; a recreated activity re-reads a clean
        // intent). FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY delivers the ORIGINAL
        // intent from the recents stack — the strip ALSO covers that (the
        // history relaunch re-parses the stripped intent → nothing offered).
        // NOTE: the data URI is stripped ONLY when it parsed as OUR content
        // link — the OAuth redirect (`anikuta://anilist-auth#token…`) carries
        // its token in the data URI + is handled by MainActivity's own
        // handleAniListOAuthRedirect, so its URI is left untouched regardless
        // of call order.
        if (deepLinkMainId != null) {
            intent.data = null
        }
        intent.removeExtra("notification_main_id")
        intent.removeExtra("open_update_history")

        if (request != null) {
            _pending.value = request
        }
    }

    /** The composition's exactly-once handshake: called after handling. */
    fun consume() {
        _pending.value = null
    }
}
