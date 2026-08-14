package com.confused.anikuta.core.updates

/**
 * Interface for posting notifications when new episodes are found.
 *
 * D-193 Phase 9: Defined in `:core:updates` to avoid a circular dependency
 * (`:core:updates` needs to call `NotificationManager.postNotification()` but
 * `:core:notifications` doesn't depend on `:core:updates`). The implementation
 * lives in `:core:notifications` (NotificationManager implements this interface)
 * + is wired in `:app` via Koin.
 *
 * CORE_RULES §7: Backend logic — no UI.
 * CORE_RULES §20: Implementations should log with their own tag.
 */
fun interface NotificationSender {
    /**
     * Post a notification for a new episode.
     *
     * @param mainId The content's mainId.
     * @param episodeNumber The episode number.
     * @param audioVariant "sub" | "dub" | "unknown".
     * @param triggerType "watchable" | "schedule" | "immediate".
     * @return true if the notification was posted, false if suppressed (master toggle off,
     *   per-anime config off, dedup, or permission denied).
     */
    suspend fun postNotification(
        mainId: String,
        episodeNumber: Double,
        audioVariant: String,
        triggerType: String,
    ): Boolean
}
