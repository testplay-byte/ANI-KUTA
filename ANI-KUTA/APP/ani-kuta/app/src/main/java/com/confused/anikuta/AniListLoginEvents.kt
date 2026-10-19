package com.confused.anikuta

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * D-759 (round 124 — the navigation reliability round): the AniList login
 * EVENT.
 *
 * ## What it replaces
 *
 * The OAuth login result used to travel through two `@Volatile` flags on the
 * Activity, and the composition POLLED them every 200 ms forever:
 *
 * ```
 * LaunchedEffect(Unit) {
 *     while (true) {
 *         delay(200)
 *         if (mainActivity?.anilistLoginSuccess == true) { ... }
 *     }
 * }
 * ```
 *
 * That shape had three problems:
 *  - the 200 ms poll ran for the whole life of the composition (a wake-up
 *    every 200 ms, forever, with no lifecycle bound);
 *  - the loop's body read `currentKey` — a PLAIN `val` captured at the FIRST
 *    composition — so the "am I already on the Trackers page?" guard was
 *    answered against the screen the user was on when the app STARTED. A login
 *    that happened minutes later pushed a DUPLICATE More + Trackers entry;
 *  - the token exchange ran in a `MainScope()` that was never cancelled (a
 *    leaked scope per redirect).
 *
 * ## The shape now
 *
 * The login result is an EVENT on a buffered channel: it is delivered exactly
 * once, it survives until the composition collects it (no loss when the
 * exchange finishes before the UI is up), and the collection is bound to the
 * composition's lifetime. The navigation guard reads the LIVE back stack.
 */
object AniListLoginEvents {

    /** What the login produced. */
    sealed interface Event {
        /** The token exchange succeeded. */
        data object Success : Event

        /** The token exchange failed. */
        data class Failed(val message: String) : Event
    }

    /**
     * BUFFERED (not conflated): every event is delivered exactly once, and an
     * event posted before the composition starts collecting waits for it.
     */
    private val channel = Channel<Event>(capacity = Channel.BUFFERED)

    /** The login events, in order. */
    val events = channel.receiveAsFlow()

    /** The login succeeded. */
    fun success() {
        channel.trySend(Event.Success)
    }

    /** The login failed. */
    fun failed(message: String) {
        channel.trySend(Event.Failed(message))
    }
}
