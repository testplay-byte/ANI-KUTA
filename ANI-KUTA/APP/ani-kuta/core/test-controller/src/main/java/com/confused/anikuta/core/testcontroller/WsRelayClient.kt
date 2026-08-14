package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.SettingsRepository
import com.confused.anikuta.core.testapi.TestCommand
import com.confused.anikuta.core.testapi.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * WebSocket transport between the test-controller (phone) + the AI agent (sandbox). D-198 v3.
 *
 * **Why WebSocket (not MQTT):** the MQTT approach failed because all 4 public brokers timed
 * out on the user's mobile network (carrier blocking MQTT ports). WebSocket uses standard
 * HTTPS ports (443) via the sandbox's Caddy gateway, which no carrier blocks.
 *
 * **Architecture:**
 * ```
 * Agent (sandbox, Python)           WS relay (:3030, inside Next.js)           Phone (this client)
 *   │  ws://localhost:3030            │                                          │
 *   ├─ connect + send command ──────►  │  ◄── wss://PUBLIC_URL/?XTransformPort=3030 ── connect + register as "phone"
 *   │                                 │  ── forward command to phone ──────────────►  execute
 *   │                                 │  ◄── result + screenshot ────────────────────  send back
 *   │  ◄── forward result ───────────  │
 *   └─ disconnect (one-shot)          │  (phone stays connected, auto-reconnect)
 * ```
 *
 * **Persistence:** the relay runs inside the Next.js dev server process (persistent — survives
 * across Bash tool calls because the dev server is the sandbox's main process). The phone
 * maintains a persistent WS connection + auto-reconnects on drop.
 *
 * **Relay URL:** read from [SettingsRepository] (key `debug.test.relay_url`). Set by the user
 * in the TestControllerSettingsScreen (More → Settings → Test Controller). The URL format is
 * `wss://SANDBOX_PUBLIC_URL/?XTransformPort=3030` (the sandbox's public preview URL + the
 * Caddy gateway port-forward query param).
 *
 * **Single-flight:** [start] uses a Mutex so concurrent calls (app-open health-check + initial
 * connect + retry) don't overlap. Idempotent (no-op if already connected).
 *
 * **Auto-reconnect:** OkHttp's WebSocket has built-in reconnect (via [RETRY_DELAY_MS]).
 * If the relay is down (e.g., Next.js dev server restarted), the phone keeps retrying.
 *
 * **Toast notifications:** every state change shows a throttled toast via [TestToaster].
 *
 * D-198 v3.
 */
class WsRelayClient(
    private val executor: TestControllerExecutor,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Ws"

        /** SettingsRepository key for the relay URL. */
        const val SETTING_RELAY_URL = "debug.test.relay_url"

        /** Reconnect delay (if the WS drops). */
        private const val RECONNECT_DELAY_MS = 5_000L

        /** Max time to wait for a result after sending a command (phone-side). */
        private const val COMMAND_RESPONSE_TIMEOUT_MS = 30_000L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private val httpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)  // keep-alive pings
        .readTimeout(0, TimeUnit.SECONDS)    // WS stays open indefinitely
        .build()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connectedUrl: String? = null
    @Volatile private var retryJob: Job? = null
    @Volatile private var isStarting = false

    /** Single-flight mutex: ensures only ONE start() runs at a time. */
    private val startMutex = Mutex()

    /**
     * Connect to the WebSocket relay. Reads the URL from [SettingsRepository].
     * Idempotent: no-op if already connected. If no URL is configured, shows a toast.
     * If all connection attempts fail, schedules a retry in [RECONNECT_DELAY_MS].
     */
    suspend fun start() {
        // Fast path: already connected.
        if (webSocket != null && isConnected()) return

        startMutex.withLock {
            // Re-check after acquiring the lock.
            if (webSocket != null && isConnected()) return@withLock
            if (isStarting) return@withLock  // Another call is already starting.
            isStarting = true

            retryJob?.cancel()
            retryJob = null

            val url = settings.getSetting(SETTING_RELAY_URL)?.trim()
            if (url.isNullOrBlank()) {
                TestToaster.show("⚠️ No relay URL — configure in Settings → Test Controller", throttleMs = 10_000L)
                Logger.w(TAG) { "no relay URL configured (setting=$SETTING_RELAY_URL)" }
                isStarting = false
                return@withLock
            }

            TestToaster.show("Connecting to relay…", throttleMs = 2000L)
            Logger.i(TAG) { "connecting to WS relay: $url" }

            try {
                // Close any existing socket.
                webSocket?.cancel()
                webSocket = null

                val request = Request.Builder().url(url).build()
                val ws = httpClient.newWebSocket(request, RelayListener(url))
                webSocket = ws
                connectedUrl = url
                Logger.i(TAG) { "WebSocket opened — waiting for connection ack" }
            } catch (e: Exception) {
                Logger.e(TAG) { "WS connect failed: ${e::class.java.simpleName}: ${e.message}" }
                TestToaster.show("❌ Relay connection failed — retrying in 5s")
                isStarting = false
                scheduleRetry()
            }
        }
    }

    /** OkHttp WebSocket listener — handles open/message/closing/failure. */
    private inner class RelayListener(private val url: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Logger.i(TAG) { "WS connected to $url" }
            isStarting = false
            // Register as the phone.
            val registerMsg = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, String>>(),
                mapOf("kind" to "register", "role" to "phone"),
            )
            webSocket.send(registerMsg)
            TestToaster.show("✅ Test controller connected", throttleMs = 10_000L)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Logger.d(TAG) { "WS message: ${text.take(200)}" }
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Logger.w(TAG) { "WS closing: $code $reason" }
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Logger.w(TAG) { "WS closed: $code $reason" }
            handleDisconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Logger.e(TAG) { "WS failure: ${t::class.java.simpleName}: ${t.message}" }
            isStarting = false
            handleDisconnect()
        }
    }

    /** Handle a message from the relay (either a command or a pong). */
    private fun handleMessage(text: String) {
        // Parse the message kind.
        val element = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return
        val jsonObj = runCatching { element.jsonObject }.getOrNull() ?: return
        val kind = jsonObj["kind"]?.let { it.jsonPrimitive.content } ?: return

        when (kind) {
            "ack" -> {
                Logger.i(TAG) { "relay acknowledged phone registration" }
            }
            "command", "ping" -> {
                // The relay forwarded a command. Decode + execute.
                // The message is the raw TestCommand JSON (with a "kind" field added by the relay).
                // We decode from the original text — TestCommand uses "type" as its discriminator,
                // so the extra "kind" field is ignored (ignoreUnknownKeys=true).
                val command = runCatching { json.decodeFromString(TestCommand.serializer(), text) }.getOrNull()
                if (command == null) {
                    Logger.w(TAG) { "failed to decode command: ${text.take(200)}" }
                    return
                }
                Logger.i(TAG) { "executing command ${command.id} (${command::class.simpleName})" }
                scope.launch {
                    val outcome = executor.execute(command)
                    sendResult(outcome)
                }
            }
            else -> {
                Logger.d(TAG) { "unknown message kind: $kind" }
            }
        }
    }

    /** Send the TestResult (+ screenshot if any) back to the relay. */
    private suspend fun sendResult(outcome: TestControllerExecutor.ExecutionOutcome) {
        val ws = webSocket ?: run {
            Logger.w(TAG) { "sendResult: no WebSocket (not connected)" }
            return
        }
        // Send screenshot first (if any) — base64-encoded in a JSON message.
        outcome.screenshotBytes?.let { bytes ->
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val shotMsg = """{"kind":"screenshot","id":"${outcome.result.id}","data":"$b64"}"""
            ws.send(shotMsg)
        }
        // Send the result JSON.
        val resultJson = json.encodeToString(TestResult.serializer(), outcome.result)
        ws.send(resultJson)
        Logger.d(TAG) { "result sent for ${outcome.result.id}" }
    }

    /** Handle WS disconnect — clear state + schedule retry. */
    private fun handleDisconnect() {
        webSocket = null
        connectedUrl = null
        isStarting = false
        TestToaster.show("⚠️ Connection lost — reconnecting in 5s")
        scheduleRetry()
    }

    /** Schedule a single retry in [RECONNECT_DELAY_MS]. */
    private fun scheduleRetry() {
        retryJob?.cancel()
        retryJob = scope.launch(Dispatchers.IO) {
            delay(RECONNECT_DELAY_MS)
            Logger.i(TAG) { "retry: attempting reconnect" }
            start()
        }
    }

    /** Disconnect + close. Called on service unbind. */
    fun stop() {
        retryJob?.cancel()
        retryJob = null
        webSocket?.cancel()
        webSocket = null
        connectedUrl = null
        isStarting = false
        Logger.i(TAG) { "WS relay client stopped" }
    }

    /** Whether the client is currently connected to the relay. */
    fun isConnected(): Boolean = webSocket != null

    /** The connected relay URL (for status display). Null if not connected. */
    fun connectedUrl(): String? = connectedUrl

    /** The configured relay URL (from SettingsRepository, even if not connected). */
    fun configuredUrl(): String? = settings.getSetting(SETTING_RELAY_URL)?.trim()?.ifBlank { null }
}
