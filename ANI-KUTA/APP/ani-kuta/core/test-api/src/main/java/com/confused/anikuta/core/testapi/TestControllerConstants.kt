package com.confused.anikuta.core.testapi

/**
 * Shared constants for the autonomous test-controller (D-197).
 *
 * The test-controller is a debug-only feature (mirrors the debug-bubble pattern —
 * `debugImplementation` in `:app`). These constants are always on the classpath via
 * `:core:test-api` (types-only), so `:app/src/main` can reference the registries
 * without depending on the debug-only `:core:test-controller` impl.
 *
 * Transport architecture (D-198): hybrid.
 *   - ntfy.sh used for ONE bootstrap message per session (delivers {relayUrl, token} to the phone).
 *   - Bun HTTP relay mini-service (port [DEFAULT_RELAY_PORT]) for all real traffic.
 */
object TestControllerConstants {

    /** Default port the Bun relay mini-service listens on (avoids 3000 Next.js + 3003 websocket example). */
    const val DEFAULT_RELAY_PORT = 3030

    /**
     * ntfy.sh topic used for the one-shot bootstrap message at session start.
     *
     * Public (ntfy topics are always public — CORE_RULES §11). The message body is
     * `{relayUrl, token, sessionId, expires}`. Anyone who guesses this topic can read
     * a session's relay URL + token, but the token rotates per session and expires.
     * For an additional safety factor, an optional PIN can be validated (see [SETTING_TEST_PIN]).
     */
    const val NTFY_BOOTSTRAP_TOPIC = "anikuta-debug-bridge-v1"

    // ── SettingsRepository keys (stored in the `app_settings` table, category="debug") ──
    /** The phone's unique device ID (generated once, persisted). Sent to the relay for identification. */
    const val SETTING_DEVICE_ID = "debug.test.device_id"
    /** Last-known relay URL (cached from the ntfy bootstrap or manually entered). */
    const val SETTING_RELAY_URL = "debug.test.relay_url"
    /** Last-known relay token (cached from the ntfy bootstrap or manually entered). */
    const val SETTING_RELAY_TOKEN = "debug.test.relay_token"
    /** Whether the controller should auto-start polling on app launch (default: false). */
    const val SETTING_AUTO_START = "debug.test.auto_start"
    /** Optional 6-digit PIN second factor (validated against the bootstrap message's `pin` field). */
    const val SETTING_TEST_PIN = "debug.test.pin"

    // ── HTTP timeouts (phone → relay) ──
    /** Long-poll hold for `GET /poll` (phone waits up to 25s for a command). */
    const val HTTP_POLL_TIMEOUT_MS = 25_000L
    /** Long-poll hold for `GET /result/:id` (agent waits up to 30s for a result). */
    const val HTTP_RESULT_TIMEOUT_MS = 30_000L
    /** Connect + call timeout for the phone's HTTP client. */
    const val HTTP_CALL_TIMEOUT_MS = 60_000L

    // ── Screenshot encoding ──
    /** Max dimension (width or height) of a captured screenshot before downscaling. */
    const val SCREENSHOT_MAX_DIMENSION = 1080
    /** JPEG quality (0-100). 70 keeps a 1080p screenshot ~50-150KB. */
    const val SCREENSHOT_JPEG_QUALITY = 70

    // ── HTTP paths (phone ↔ relay contract — must match mini-services/agent-bridge/src/index.ts) ──
    const val PATH_CMD = "/cmd"
    const val PATH_POLL = "/poll"
    const val PATH_RESULT = "/result"
    const val PATH_SCREENSHOT = "/screenshot"
    const val PATH_STATE = "/state"
    const val PATH_BOOTSTRAP_ACK = "/bootstrap_ack"
}
