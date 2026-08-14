package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Singleton bridge between the app's Activity lifecycle + the AccessibilityService's WsRelayClient.
 *
 * The AccessibilityService runs independently of the app's Activity lifecycle (it's system-bound).
 * But the user wants an "app-open" health-check: when they open the app, the test-controller should
 * verify it's connected + restart if needed.
 *
 * **Cooldown** (D-198 v2.2): [ensureConnected] is called on EVERY `onActivityResumed` (which fires
 * multiple times — returning from settings, keyboard open/close, etc.). To avoid spamming toasts +
 * launching redundant reconnect coroutines, it only runs the check once per [COOLDOWN_MS] (10s).
 * The WsRelayClient's single-flight mutex handles the actual serialization.
 *
 * Flow:
 *   1. [TestAccessibilityService.onServiceConnected] → [register] (stores a WeakReference to the client).
 *   2. `:app/src/debug/DebugInit.kt` `onActivityResumed(MainActivity)` → [ensureConnected].
 *   3. [ensureConnected] checks the client's connection state:
 *      - Client is null → toast "⚠️ Test controller disabled — enable in Settings → Accessibility".
 *      - Client is connected → toast "✅ Test controller online".
 *      - Client is disconnected → toast "🔄 Reconnecting…" + calls client.start().
 *
 * D-198 v3: updated to use WsRelayClient instead of MqttBridge.
 */
object TestControllerStatus {
    private const val TAG = "Anikuta:Test:Status"
    private const val COOLDOWN_MS = 10_000L  // only check once per 10s

    @Volatile private var clientRef: WeakReference<WsRelayClient>? = null
    @Volatile private var lastCheckTime: Long = 0L

    /** Called by [TestAccessibilityService.onServiceConnected]. */
    fun register(client: WsRelayClient) {
        clientRef = WeakReference(client)
        Logger.i(TAG) { "WS client registered" }
    }

    /** Called by [TestAccessibilityService.onUnbind]. */
    fun unregister(client: WsRelayClient) {
        val ref = clientRef
        if (ref?.get() === client) {
            clientRef = null
            Logger.i(TAG) { "WS client unregistered" }
        }
    }

    /** Whether the client is currently connected to the relay. */
    fun isConnected(): Boolean = clientRef?.get()?.isConnected() == true

    /**
     * Disconnect the client (D-198 v4.1 — user toggled the controller OFF).
     * The client will NOT auto-reconnect until [ensureConnected] is called again.
     */
    fun disconnect() {
        val client = clientRef?.get()
        if (client != null) {
            GlobalScope.launch(Dispatchers.IO) {
                runCatching { client.stop() }
                    .onFailure { Logger.e(TAG) { "disconnect failed: ${it::class.java.simpleName}: ${it.message}" } }
            }
            Logger.i(TAG) { "client disconnected by user toggle" }
        }
    }

    /**
     * Called from `:app/src/debug/DebugInit.kt`'s `onActivityResumed` hook (when the app opens).
     * Checks the client's state + shows a toast. If disconnected, triggers a reconnect.
     *
     * Cooldown: only runs once per [COOLDOWN_MS]. Concurrent calls within the cooldown are skipped.
     */
    fun ensureConnected() {
        val now = System.currentTimeMillis()
        if (now - lastCheckTime < COOLDOWN_MS) return
        lastCheckTime = now

        val client = clientRef?.get()
        if (client == null) {
            TestToaster.show("⚠️ Test controller disabled — enable in Settings → Accessibility", throttleMs = 10_000L)
            return
        }
        if (client.isConnected()) {
            TestToaster.show("✅ Test controller online", throttleMs = 10_000L)
        } else {
            // D-198 v4: there's always a default URL (Cloudflare), so just reconnect.
            TestToaster.show("🔄 Reconnecting test controller…", throttleMs = 5000L)
            Logger.i(TAG) { "ensureConnected: client disconnected — triggering reconnect" }
            GlobalScope.launch(Dispatchers.IO) {
                runCatching { client.start() }
                    .onFailure { Logger.e(TAG) { "reconnect failed: ${it::class.java.simpleName}: ${it.message}" } }
            }
        }
    }
}
