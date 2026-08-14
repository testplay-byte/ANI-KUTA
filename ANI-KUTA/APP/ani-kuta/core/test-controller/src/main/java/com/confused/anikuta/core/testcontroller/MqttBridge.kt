package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.testapi.TestCommand
import com.confused.anikuta.core.testapi.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MQTT-based transport between the test-controller (phone) + the AI agent (sandbox).
 *
 * **D-198 v2** — replaced the ntfy.sh + Bun HTTP relay approach with MQTT for true plug-and-play:
 *  - No user-entered config (broker URL + channel are hardcoded below).
 *  - No background relay process in the sandbox (the agent does one-shot connect→send→disconnect).
 *  - No URL discovery problem (both sides connect to a known public broker).
 *  - No rate limits (unlike ntfy.sh's 250 msg/day cap).
 *
 * **Architecture:**
 * ```
 * Agent (Bun, one-shot)         MQTT broker              Phone (this MqttBridge, persistent)
 *   │  wss://broker.hivemq.com:8884/mqtt    │  wss://broker.hivemq.com:8884/mqtt  │
 *   ├─ publish cmd ──────────────────────►  │  ──────────────────────────────► subscribe cmd
 *   │                                        │                                      │ execute
 *   │  subscribe result  ◄──────────────────┤  ◄────────────────────────────────── publish result
 *   │  subscribe shot/<id> ◄──────────────  │  ◄────────────────────────────────── publish shot/<id>
 *   │  (wait for result, then disconnect)    │  (stays connected, auto-reconnect) │
 * ```
 *
 * **Topics** (hardcoded — act as the "channel name"; public but unguessable enough for debug use):
 *  - `anikuta/test/v1/cmd` — agent → phone (TestCommand JSON).
 *  - `anikuta/test/v1/result` — phone → agent (TestResult JSON).
 *  - `anikuta/test/v1/shot/<commandId>` — phone → agent (JPEG bytes).
 *
 * **Security:** The broker + topics are public (in the source code). Anyone who subscribes can see
 * commands + results. For a debug-only tool with one user + one phone, this is acceptable. If the
 * user wants auth later, a per-session token can be added (agent generates it, publishes it, phone
 * validates it before executing commands). For now: no auth, truly plug-and-play.
 *
 * **Reconnection:** Paho's `isAutomaticReconnect = true` handles broker drops. The phone stays
 * subscribed across reconnects.
 *
 * **QoS:** QoS 1 (at least once) for commands + results. Ensures delivery; rare duplicates are
 * idempotent for read commands, + action commands (tap/swipe) tolerate a rare double-fire.
 */
class MqttBridge(
    private val executor: TestControllerExecutor,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Mqtt"

        // Free public MQTT broker (no signup, no API key, no rate limit).
        // HiveMQ public broker — WSS (WebSocket Secure) on port 8884, path /mqtt.
        // Fallback: broker.emqx.io:8084 (EMQX public broker) — change here if HiveMQ is down.
        private const val BROKER_URI = "wss://broker.hivemq.com:8884/mqtt"

        // Hardcoded topics (the "channel name"). Both the phone + the agent use these exact strings.
        private const val CMD_TOPIC = "anikuta/test/v1/cmd"
        private const val RESULT_TOPIC = "anikuta/test/v1/result"
        private const val SHOT_TOPIC_PREFIX = "anikuta/test/v1/shot/"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @Volatile
    private var client: MqttAsyncClient? = null

    /** Connect to the broker + subscribe to the command topic. Idempotent (no-op if already connected). */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (client?.isConnected == true) return@withContext
        try {
            val clientId = "anikuta-phone-${UUID.randomUUID()}"
            val c = MqttAsyncClient(BROKER_URI, clientId, MemoryPersistence())
            c.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    Logger.i(TAG) { if (reconnect) "reconnected to broker" else "connected to broker ($serverURI)" }
                    if (reconnect) {
                        // Re-subscribe after reconnect (Paho drops subs on reconnect).
                        runCatching {
                            c.subscribe(CMD_TOPIC, 1)
                        }.onFailure { Logger.w(TAG) { "re-subscribe failed: ${it.message}" } }
                    }
                }
                override fun connectionLost(cause: Throwable?) {
                    Logger.w(TAG) { "connection lost: ${cause?.message ?: "unknown"} — auto-reconnect will retry" }
                }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic == CMD_TOPIC && message != null) {
                        val payload = String(message.payload, Charsets.UTF_8)
                        handleCommand(payload)
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // No-op — fire-and-forget publishing.
                }
            })
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
                maxInflight = 100
            }
            c.connect(options).await(timeoutSec = 15)
            c.subscribe(CMD_TOPIC, 1).await(timeoutSec = 5)
            client = c
            Logger.i(TAG) { "MQTT bridge started — listening on $CMD_TOPIC (broker=$BROKER_URI)" }
        } catch (e: Exception) {
            Logger.e(TAG) { "MQTT start failed: ${e::class.java.simpleName}: ${e.message}" }
            // Don't throw — the service should survive + retry. Paho's auto-reconnect will keep trying.
            client = null
        }
    }

    /** Disconnect from the broker. Called on service unbind. */
    fun stop() {
        runCatching {
            client?.let { c ->
                if (c.isConnected) {
                    c.disconnectForcibly(2, 3, true)
                }
                c.close()
            }
        }.onFailure { Logger.w(TAG) { "MQTT stop failed: ${it.message}" } }
        client = null
        Logger.i(TAG) { "MQTT bridge stopped" }
    }

    /** Deserialize + execute a command, then publish the result (+ screenshot if any). */
    private fun handleCommand(payload: String) {
        val command = runCatching { json.decodeFromString<TestCommand>(payload) }.getOrNull()
        if (command == null) {
            Logger.w(TAG) { "failed to decode command: ${payload.take(200)}" }
            return
        }
        Logger.d(TAG) { "executing command ${command.id} (${command::class.simpleName})" }
        scope.launch {
            val outcome = executor.execute(command)
            publishResult(outcome)
        }
    }

    /** Publish the TestResult JSON (+ screenshot bytes if any) to the broker. */
    private suspend fun publishResult(outcome: TestControllerExecutor.ExecutionOutcome) =
        withContext(Dispatchers.IO) {
            val c = client ?: run {
                Logger.w(TAG) { "publish: client is null (not connected)" }
                return@withContext
            }
            if (!c.isConnected) {
                Logger.w(TAG) { "publish: client not connected — result will be lost" }
                return@withContext
            }
            // Publish screenshot FIRST (so the agent's /result/:id hasScreenshot:true is backed by a real file).
            outcome.screenshotBytes?.let { bytes ->
                runCatching {
                    c.publish(SHOT_TOPIC_PREFIX + outcome.result.id, bytes, 1, false)
                }.onFailure { Logger.w(TAG) { "screenshot publish failed: ${it.message}" } }
            }
            // Publish the result JSON.
            val resultJson = json.encodeToString(TestResult.serializer(), outcome.result)
            runCatching {
                c.publish(RESULT_TOPIC, resultJson.toByteArray(Charsets.UTF_8), 1, false)
            }.onFailure { Logger.w(TAG) { "result publish failed: ${it.message}" } }
        }

    /** Whether the bridge is currently connected to the broker. */
    fun isConnected(): Boolean = client?.isConnected == true
}

// ── Paho token → coroutine adapter ──

private suspend fun IMqttToken.await(timeoutSec: Long) =
    kotlinx.coroutines.suspendCancellableCoroutine<IMqttToken> { cont ->
        val timeout = TimeUnit.SECONDS.toMillis(timeoutSec)
        val watchdog = kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(timeout)
            if (cont.isActive) cont.resumeWithException(java.util.concurrent.TimeoutException("MQTT operation timed out after ${timeoutSec}s"))
        }
        setActionCallback(object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                watchdog.cancel()
                if (cont.isActive) cont.resume(asyncActionToken ?: this@await)
            }
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                watchdog.cancel()
                if (cont.isActive) cont.resumeWithException(exception ?: RuntimeException("MQTT operation failed"))
            }
        })
    }
