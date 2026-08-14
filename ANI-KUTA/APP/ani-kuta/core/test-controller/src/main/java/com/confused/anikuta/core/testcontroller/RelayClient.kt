package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.SettingsRepository
import com.confused.anikuta.core.testapi.TestControllerConstants
import com.confused.anikuta.core.testapi.TestCommand
import com.confused.anikuta.core.testapi.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The phone-side HTTP client that long-polls the Bun relay for commands + posts results
 * (D-198 transport).
 *
 * Poll loop (runs on [Dispatchers.IO]):
 *   1. `GET {relayUrl}/poll?deviceId=X&token=T` (held ≤25s by the relay for a command, or returns `{empty:true}`).
 *   2. If a command arrives: deserialize → hand to [TestControllerExecutor.execute] → post result
 *      (and screenshot if any) back to the relay.
 *   3. Repeat immediately (no sleep) — the long-poll IS the wait.
 *   4. On any error (network, 401, etc.): backoff 2s, retry. The loop never exits unless the
 *      service is unbound (scope cancelled).
 *
 * Relay URL + token come from [SettingsRepository] (set by the ntfy bootstrap handler in
 * `TestAccessibilityService` / `:app/src/debug`). If neither is set, the client idles (waiting
 * for the user/agent to configure them) — it polls `getSetting` every 5s for a config change.
 *
 * Result posting:
 *   - `POST {relayUrl}/screenshot/{id}?token=T` body=PNG bytes (if screenshot present).
 *   - `POST {relayUrl}/result?token=T` body=TestResult JSON (always).
 *   Screenshot is posted BEFORE result so the agent never sees `hasScreenshot:true` with a 404.
 */
class RelayClient(
    private val appContext: android.content.Context,
    private val executor: TestControllerExecutor,
    private val settings: SettingsRepository,
    private val httpClient: OkHttpClient,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Relay"
        private const val POLL_BACKOFF_MS = 2_000L
        private const val CONFIG_IDLE_POLL_MS = 5_000L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private var loopJob: Job? = null
    @Volatile private var deviceId: String = ""

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(Dispatchers.IO) { runLoop() }
        Logger.i(TAG) { "relay client started" }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        Logger.i(TAG) { "relay client stopped" }
    }

    private suspend fun runLoop() {
        while (scope.isActive) {
            val relayUrl = settings.getSetting(TestControllerConstants.SETTING_RELAY_URL)?.trimEnd('/')
            val token = settings.getSetting(TestControllerConstants.SETTING_RELAY_TOKEN)
            if (relayUrl.isNullOrBlank() || token.isNullOrBlank()) {
                // No config yet — wait for the ntfy bootstrap or manual entry.
                Logger.i(TAG) { "no relay config yet — waiting ${CONFIG_IDLE_POLL_MS}ms" }
                delay(CONFIG_IDLE_POLL_MS)
                continue
            }
            ensureDeviceId()
            try {
                pollOnce(relayUrl, token)
            } catch (e: Exception) {
                Logger.w(TAG) { "poll failed: ${e::class.java.simpleName}: ${e.message} — backoff ${POLL_BACKOFF_MS}ms" }
                delay(POLL_BACKOFF_MS)
            }
        }
    }

    private fun ensureDeviceId() {
        if (deviceId.isBlank()) {
            deviceId = settings.getSetting(TestControllerConstants.SETTING_DEVICE_ID)
                ?: java.util.UUID.randomUUID().toString().also { id ->
                    settings.upsertSetting(TestControllerConstants.SETTING_DEVICE_ID, id, "string", "debug")
                }
        }
    }

    private suspend fun pollOnce(relayUrl: String, token: String) {
        val pollUrl = "$relayUrl${TestControllerConstants.PATH_POLL}?deviceId=$deviceId&token=${token.urlEncode()}"
        val request = Request.Builder()
            .url(pollUrl)
            .get()
            .build()
        httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Logger.w(TAG) { "poll returned ${resp.code} — backoff" }
                delay(POLL_BACKOFF_MS)
                return
            }
            val body = resp.body?.string()
            if (body.isNullOrBlank()) { delay(POLL_BACKOFF_MS); return }
            val envelope = runCatching { json.decodeFromString<PollEnvelope>(body) }.getOrNull()
            if (envelope == null) { delay(POLL_BACKOFF_MS); return }
            if (envelope.command == null) return // empty / keepalive — poll again immediately
            val command = runCatching { json.decodeFromJsonElement<TestCommand>(envelope.command) }.getOrNull()
            if (command == null) {
                Logger.w(TAG) { "failed to decode command: ${envelope.command}" }
                return
            }
            Logger.d(TAG) { "executing command ${command.id} (${command::class.simpleName})" }
            val outcome = executor.execute(command)
            postResult(relayUrl, token, outcome)
        }
    }

    private fun postResult(relayUrl: String, token: String, outcome: TestControllerExecutor.ExecutionOutcome) {
        // 1. Screenshot first (if any) — so /result/:id's hasScreenshot:true is backed by a real file.
        outcome.screenshotBytes?.let { bytes ->
            val shotUrl = "$relayUrl${TestControllerConstants.PATH_SCREENSHOT}/${outcome.result.id}?token=${token.urlEncode()}"
            val shotReq = Request.Builder()
                .url(shotUrl)
                .post(bytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
            runCatching { httpClient.newCall(shotReq).execute().close() }
                .onFailure { Logger.w(TAG) { "screenshot post failed: ${it.message}" } }
        }
        // 2. Result JSON.
        val resultUrl = "$relayUrl${TestControllerConstants.PATH_RESULT}?token=${token.urlEncode()}"
        val resultJson = json.encodeToString(TestResult.serializer(), outcome.result)
        val resultReq = Request.Builder()
            .url(resultUrl)
            .post(resultJson.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { httpClient.newCall(resultReq).execute().close() }
            .onFailure { Logger.w(TAG) { "result post failed: ${it.message}" } }
    }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")

    @kotlinx.serialization.Serializable
    private data class PollEnvelope(
        val command: kotlinx.serialization.json.JsonElement? = null,
        val empty: Boolean? = null,
    )
}
