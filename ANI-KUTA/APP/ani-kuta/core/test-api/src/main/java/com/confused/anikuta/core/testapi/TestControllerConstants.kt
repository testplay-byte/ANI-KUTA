package com.confused.anikuta.core.testapi

/**
 * Shared constants for the autonomous test-controller (D-197).
 *
 * The test-controller is a debug-only feature (mirrors the debug-bubble pattern —
 * `debugImplementation` in `:app`). These constants are always on the classpath via
 * `:core:test-api` (types-only), so `:app/src/main` can reference the registries
 * without depending on the debug-only `:core:test-controller` impl.
 *
 * **Transport architecture (D-198 v2): MQTT.**
 * Both the phone + the agent connect to a free public MQTT broker (hardcoded below —
 * no signup, no API key, no rate limit). Both use the same hardcoded topics (the
 * "channel name"). No user-entered config needed — truly plug-and-play:
 *   1. User enables the AccessibilityService once (Settings → Accessibility).
 *   2. The app auto-connects to the broker on service start.
 *   3. The agent does one-shot connect→publish→wait→disconnect per command.
 *
 * **Why MQTT (not ntfy.sh + Bun relay):**
 *  - ntfy.sh rate limits (250 msg/day + 2MB attachments) are too low for interactive testing.
 *  - The Bun HTTP relay required a persistent background process in the sandbox (which dies
 *    between Bash tool calls) + URL discovery (the sandbox has no public-URL env var).
 *  - MQTT has none of these issues: no rate limit, no persistent process, no URL discovery.
 *
 * **Security:** The broker + topics are public (in the source code). Anyone who subscribes can
 * see commands + results. For a debug-only tool with one user + one phone, this is acceptable.
 * The topic name acts as a "channel password" (unguessable enough for debug use). If stronger
 * auth is needed, a per-session token can be added (agent generates it, phone validates it).
 */
object TestControllerConstants {

    // ── MQTT broker (hardcoded — both phone + agent use these) ──

    /**
     * The public MQTT broker URI. Both the phone + the agent connect here.
     *
     * HiveMQ public broker — WSS (WebSocket Secure) on port 8884, path /mqtt.
     * No signup, no API key, no rate limit (reasonable use).
     * Fallback: `wss://broker.emqx.io:8084/mqtt` (EMQX public broker) — change here if HiveMQ is down.
     */
    const val MQTT_BROKER_URI = "wss://broker.hivemq.com:8884/mqtt"

    // ── MQTT topics (hardcoded — the "channel name" for each direction) ──

    /** Agent → phone: TestCommand JSON. The phone subscribes to this. */
    const val MQTT_TOPIC_CMD = "anikuta/test/v1/cmd"

    /** Phone → agent: TestResult JSON. The agent subscribes to this. */
    const val MQTT_TOPIC_RESULT = "anikuta/test/v1/result"

    /** Phone → agent: JPEG screenshot bytes. Topic = `SHOT_TOPIC_PREFIX + commandId`. The agent subscribes to `SHOT_TOPIC_PREFIX + "#"` (wildcard). */
    const val MQTT_TOPIC_SHOT_PREFIX = "anikuta/test/v1/shot/"

    // ── SettingsRepository keys (stored in the `app_settings` table, category="debug") ──
    /** The phone's unique device ID (generated once, persisted). Included in `pong` results. */
    const val SETTING_DEVICE_ID = "debug.test.device_id"

    // ── Screenshot encoding ──
    /** Max dimension (width or height) of a captured screenshot before downscaling. */
    const val SCREENSHOT_MAX_DIMENSION = 1080
    /** JPEG quality (0-100). 70 keeps a 1080p screenshot ~50-150KB. */
    const val SCREENSHOT_JPEG_QUALITY = 70
}
