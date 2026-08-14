package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.testapi.TestCommand
import com.confused.anikuta.core.testapi.TestResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * MQTT transport between the test-controller (phone) + the AI agent (sandbox). D-198 v2.
 *
 * **Single-flight** (D-198 v2.2): a [Mutex] ensures only ONE [start] runs at a time. Previous
 * version had concurrent start() calls (initial connect + ensureConnected app-open hook + retry
 * job all overlapping), which caused Paho's async client to interleave connect attempts + time out.
 * Now, concurrent callers wait for the first to finish (or skip if it already succeeded).
 *
 * **Broker fallback**: tries 4 brokers in order until one connects. Carriers commonly block 8884;
 * 8084/8083/8000 are more commonly allowed. The phone picks the first that connects + stays on
 * it (auto-reconnect). The agent script uses the SAME order, so they converge.
 *
 * **Timeouts**: 15s per broker (mobile networks need more time for the TLS + WebSocket handshake
 * than the previous 8s allowed). Total max fallback cycle: 4 × 15s = 60s.
 *
 * **Retry**: if all brokers fail, schedules a single retry in 30s (not recursive — uses a Job
 * that can be cancelled by stop() or a new successful start()).
 *
 * **Toast notifications**: every state change shows a throttled toast via [TestToaster].
 *
 * Topics (hardcoded — both phone + agent use the same strings):
 *  - `anikuta/test/v1/cmd` — agent → phone (TestCommand JSON).
 *  - `anikuta/test/v1/result` — phone → agent (TestResult JSON).
 *  - `anikuta/test/v1/shot/<commandId>` — phone → agent (JPEG bytes).
 */
class MqttBridge(
    private val executor: TestControllerExecutor,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "Anikuta:Test:Mqtt"

        private val BROKERS = listOf(
            BrokerConfig("HiveMQ WSS", "broker.hivemq.com", 8884, "/mqtt", useTls = true),
            BrokerConfig("EMQX WSS", "broker.emqx.io", 8084, "/mqtt", useTls = true),
            BrokerConfig("EMQX WS", "broker.emqx.io", 8083, "/mqtt", useTls = false),
            BrokerConfig("HiveMQ WS", "broker.hivemq.com", 8000, "/mqtt", useTls = false),
        )

        private const val CMD_TOPIC = "anikuta/test/v1/cmd"
        private const val RESULT_TOPIC = "anikuta/test/v1/result"
        private const val SHOT_TOPIC_PREFIX = "anikuta/test/v1/shot/"

        private const val CONNECT_TIMEOUT_SEC = 15L   // per broker (mobile networks need more time)
        private const val RETRY_DELAY_MS = 30_000L     // if all brokers fail, retry after 30s
    }

    private data class BrokerConfig(
        val label: String,
        val host: String,
        val port: Int,
        val path: String,
        val useTls: Boolean,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @Volatile private var client: MqttAsyncClient? = null
    @Volatile private var connectedBroker: BrokerConfig? = null
    @Volatile private var retryJob: Job? = null

    /** Single-flight mutex: ensures only ONE start() runs at a time. */
    private val startMutex = Mutex()

    /**
     * Connect to the first reachable broker + subscribe to the command topic.
     *
     * **Single-flight**: if another start() is already running, this call waits for it to finish,
     * then checks if the connection succeeded. If it did, this call is a no-op. If it didn't,
     * this call runs the broker fallback cycle again.
     *
     * Idempotent: no-op if already connected. If all brokers fail, schedules a single retry in 30s.
     */
    suspend fun start() {
        // Fast path: already connected.
        if (client?.isConnected == true) return

        // Single-flight: only one start() runs at a time. Concurrent callers wait.
        startMutex.withLock {
            // Re-check after acquiring the lock (another caller might have connected).
            if (client?.isConnected == true) return@withLock
            retryJob?.cancel()
            retryJob = null

            for (broker in BROKERS) {
                TestToaster.show("Connecting to ${broker.label}…", throttleMs = 2000L)
                Logger.i(TAG) { "trying broker ${broker.label} (${broker.host}:${broker.port}, tls=${broker.useTls})" }
                try {
                    connectToBroker(broker)
                    connectedBroker = broker
                    TestToaster.show("✅ Test controller connected (${broker.label})")
                    Logger.i(TAG) { "MQTT bridge started on ${broker.label} — listening on $CMD_TOPIC" }
                    return@withLock
                } catch (e: Exception) {
                    // Log the FULL exception (not just the message) — MqttException has a reason code.
                    val reason = if (e is MqttException) " (reasonCode=${e.reasonCode})" else ""
                    Logger.w(TAG) { "broker ${broker.label} failed: ${e::class.java.simpleName}: ${e.message}$reason" }
                    // Clean up the failed client so the next broker attempt starts fresh.
                    runCatching { client?.disconnectForcibly(1, 2, true) }
                    runCatching { client?.close() }
                    client = null
                }
            }

            // All brokers failed — schedule a single retry (not recursive).
            TestToaster.show("❌ Test broker unreachable — will retry in 30s")
            Logger.e(TAG) { "all ${BROKERS.size} brokers failed — scheduling retry in ${RETRY_DELAY_MS}ms" }
            scheduleRetry()
        }
    }

    /** Schedule a single retry in [RETRY_DELAY_MS]. Cancels any previous retry job. */
    private fun scheduleRetry() {
        retryJob?.cancel()
        retryJob = GlobalScope.launch(Dispatchers.IO) {
            delay(RETRY_DELAY_MS)
            if (client?.isConnected != true) {
                Logger.i(TAG) { "retry: attempting broker fallback cycle again" }
                start()
            }
        }
    }

    /** Disconnect + close. Called on service unbind. Cancels any pending retry. */
    fun stop() {
        retryJob?.cancel()
        retryJob = null
        runCatching {
            client?.let { c ->
                if (c.isConnected) {
                    c.disconnectForcibly(2, 3, true)
                }
                c.close()
            }
        }.onFailure { Logger.w(TAG) { "MQTT stop failed: ${it.message}" } }
        client = null
        connectedBroker = null
        Logger.i(TAG) { "MQTT bridge stopped" }
    }

    /** Whether the bridge is currently connected to a broker. */
    fun isConnected(): Boolean = client?.isConnected == true

    /** The label of the connected broker (for status display). Null if not connected. */
    fun connectedBrokerLabel(): String? = connectedBroker?.label

    /** Connect to a specific broker + set up the message callback + subscribe. */
    private suspend fun connectToBroker(broker: BrokerConfig) {
        val uri = "${if (broker.useTls) "wss" else "ws"}://${broker.host}:${broker.port}${broker.path}"
        val clientId = "anikuta-phone-${UUID.randomUUID()}"
        Logger.d(TAG) { "connecting to $uri (clientId=$clientId)" }
        val c = MqttAsyncClient(uri, clientId, MemoryPersistence())
        c.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) {
                    Logger.i(TAG) { "reconnected to ${broker.label}" }
                    TestToaster.show("🔄 Reconnected (${broker.label})")
                    runCatching { c.subscribe(CMD_TOPIC, 1) }
                        .onFailure { Logger.w(TAG) { "re-subscribe failed: ${it.message}" } }
                }
            }
            override fun connectionLost(cause: Throwable?) {
                Logger.w(TAG) { "connection lost to ${broker.label}: ${cause?.message ?: "unknown"} — auto-reconnect will retry" }
                TestToaster.show("⚠️ Connection lost — reconnecting…")
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
            connectionTimeout = CONNECT_TIMEOUT_SEC.toInt()
            keepAliveInterval = 30
            maxInflight = 100
        }
        // The connect() token resolves when the CONNACK is received. Await with a timeout
        // slightly longer than connectionTimeout (to give the handshake room).
        c.connect(options).await(timeoutSec = CONNECT_TIMEOUT_SEC + 5)
        // Subscribe after connect succeeds.
        c.subscribe(CMD_TOPIC, 1).await(timeoutSec = 5)
        client = c
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

    /** Publish the TestResult JSON (+ screenshot bytes if any) to the connected broker. */
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
            outcome.screenshotBytes?.let { bytes ->
                runCatching {
                    c.publish(SHOT_TOPIC_PREFIX + outcome.result.id, bytes, 1, false)
                }.onFailure { Logger.w(TAG) { "screenshot publish failed: ${it.message}" } }
            }
            val resultJson = json.encodeToString(TestResult.serializer(), outcome.result)
            runCatching {
                c.publish(RESULT_TOPIC, resultJson.toByteArray(Charsets.UTF_8), 1, false)
            }.onFailure { Logger.w(TAG) { "result publish failed: ${it.message}" } }
        }
}

// ── Paho token → coroutine adapter ──

private suspend fun IMqttToken.await(timeoutSec: Long) =
    kotlinx.coroutines.suspendCancellableCoroutine<IMqttToken> { cont ->
        val timeout = TimeUnit.SECONDS.toMillis(timeoutSec)
        val watchdog = GlobalScope.launch {
            delay(timeout)
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
