package com.confused.anikuta.core.appupdate

/**
 * Thrown by an [UpdateSource] when the CHECK ITSELF fails (HTTP error, rate
 * limit, network unreachable, malformed response). The round-133 fix for the
 * silent failure: null used to mean BOTH "no update" and "the check failed",
 * so a failed check flashed "You are on the latest version" — a lie. The
 * manager now records the exception as lastCheckError and the About screen
 * shows its error card instead.
 */
class UpdateCheckException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A pluggable source for app update information.
 *
 * # Design
 *
 * The update system supports multiple sources. Currently:
 * - [GitHubUpdateSource] — checks the GitHub Releases API.
 *
 * Future sources (architecturally ready — just add a new implementation):
 * - Custom JSON endpoint — a self-hosted JSON file with version + APK URL.
 * - Firebase Remote Config — for A/B testing update rollouts.
 * - In-app purchases — gate updates behind a premium tier.
 *
 * Each source implements [fetchLatestUpdate] which returns an [AppUpdateInfo]
 * or null if no update is available.
 *
 * # Error Handling (round 133: honest failures)
 *
 * A source THROWS [UpdateCheckException] when the check itself fails (HTTP
 * error, rate limit, network unreachable, malformed response) — the manager
 * records it and the UI shows the error card. Returning null means ONLY
 * "no update available" (the source answered and the installed version is the
 * newest). The old contract ("never throw; null on failure") made a dead
 * network look identical to "up to date".
 */
interface UpdateSource {
    /** A unique identifier for this source (e.g., "github", "custom"). */
    val id: String

    /**
     * Fetches the latest available update info, or null if:
     * - No update is available (current version is latest).
     * - The source is unreachable (network error, API rate limit, etc.).
     * - The response is malformed.
     *
     * @param currentVersionCode the installed app's version code (for comparison).
     * @param currentVersionName the installed app's version name (for comparison).
     */
    suspend fun fetchLatestUpdate(currentVersionCode: Long, currentVersionName: String): AppUpdateInfo?
}
