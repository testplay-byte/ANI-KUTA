package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Singleton bridge between the app's Activity lifecycle + the AccessibilityService's MqttBridge.
 *
 * The AccessibilityService runs independently of the app's Activity lifecycle (it's system-bound).
 * But the user wants an "app-open" health-check: when they open the app, the test-controller should
 * verify it's connected + restart if needed.
 *
 * Flow:
 *   1. [TestAccessibilityService.onServiceConnected] → [register] (stores a WeakReference to the bridge).
 *   2. `:app/src/debug/DebugInit.kt` `onActivityResumed(MainActivity)` → [ensureConnected].
 *   3. [ensureConnected] checks the bridge's connection state:
 *      - Bridge is null → toast "⚠️ Test controller disabled — enable in Settings → Accessibility".
 *      - Bridge is connected → toast "✅ Test controller online (broker label)".
 *      - Bridge is disconnected → toast "🔄 Reconnecting…" + calls bridge.start() (which runs the broker fallback).
 *
 * Uses GlobalScope for the reconnect coroutine — acceptable for a debug-only tool (the coroutine
 * is fire-and-forget + the bridge handles its own lifecycle).
 *
 * D-198 v2.1.
 */
object TestControllerStatus {
    private const val TAG = "Anikuta:Test:Status"

    @Volatile
    private var bridgeRef: WeakReference<MqttBridge>? = null

    /** Called by [TestAccessibilityService.onServiceConnected]. */
    fun register(bridge: MqttBridge) {
        bridgeRef = WeakReference(bridge)
        Logger.i(TAG) { "bridge registered" }
    }

    /** Called by [TestAccessibilityService.onUnbind]. */
    fun unregister(bridge: MqttBridge) {
        val ref = bridgeRef
        if (ref?.get() === bridge) {
            bridgeRef = null
            Logger.i(TAG) { "bridge unregistered" }
        }
    }

    /** Whether the bridge is currently connected to a broker. */
    fun isConnected(): Boolean = bridgeRef?.get()?.isConnected() == true

    /**
     * Called from `:app/src/debug/DebugInit.kt`'s `onActivityResumed` hook (when the app opens).
     * Checks the bridge's state + shows a toast. If disconnected, triggers a reconnect.
     */
    fun ensureConnected() {
        val bridge = bridgeRef?.get()
        if (bridge == null) {
            TestToaster.show("⚠️ Test controller disabled — enable in Settings → Accessibility", throttleMs = 10_000L)
            return
        }
        if (bridge.isConnected()) {
            val label = bridge.connectedBrokerLabel() ?: "unknown"
            TestToaster.show("✅ Test controller online ($label)", throttleMs = 10_000L)
        } else {
            TestToaster.show("🔄 Reconnecting test controller…", throttleMs = 5000L)
            Logger.i(TAG) { "ensureConnected: bridge disconnected — triggering reconnect" }
            GlobalScope.launch(Dispatchers.IO) {
                runCatching { bridge.start() }
                    .onFailure { Logger.e(TAG) { "reconnect failed: ${it.message}" } }
            }
        }
    }
}
