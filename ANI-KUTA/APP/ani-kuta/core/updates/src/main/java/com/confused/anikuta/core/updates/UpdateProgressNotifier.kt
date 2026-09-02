package com.confused.anikuta.core.updates

/**
 * Interface for posting BACKGROUND-STATUS notifications while the update
 * engine checks anime for new episodes.
 *
 * Task 63 (round 23 — C): the user asked to never be "in the blind" about
 * background work — "it will show me the status on the notification bar …
 * it is testing this anime for its released episodes … so I am not in the
 * blind. I know what the application is trying to do, how it is trying to
 * do it, and when".
 *
 * Same seam pattern as [NotificationSender]: defined in `:core:updates` to
 * avoid a circular dependency (the engine must not depend on
 * `:core:notifications`); the implementation lives in `:app`
 * (UpdateProgressNotifierImpl) and is wired via Koin (nullable — tests and
 * headless use pass null and nothing happens).
 *
 * Contract:
 * - [onCheckStart] once, before the first anime is checked.
 * - [onProgress] before each anime check (title = the anime being checked).
 * - Exactly ONE terminal call — [onFinish] (check ran to completion) or
 *   [onFailed] (the check aborted mid-run).
 * - All methods are NON-suspend and fast: the engine calls them from its
 *   IO worker threads (two of them from around a `synchronized` block).
 *
 * CORE_RULES §7: Backend logic — no UI. CORE_RULES §20: own log tag in the impl.
 */
interface UpdateProgressNotifier {
    /** A check run is starting with [total] anime to verify. */
    fun onCheckStart(total: Int)

    /**
     * The anime at position [current] of [total] is being checked now;
     * [title] is its display title ("" when unknown).
     */
    fun onProgress(current: Int, total: Int, title: String)

    /** The whole check ran to completion — [newEpisodes] were found. */
    fun onFinish(totalChecked: Int, newEpisodes: Int)

    /** The check aborted (worker retry, network failure) — [message] says why. */
    fun onFailed(message: String)
}
