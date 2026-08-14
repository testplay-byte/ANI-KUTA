package com.confused.anikuta.core.testcontroller

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.lang.ref.WeakReference

/**
 * Singleton bridge between the app's Activity lifecycle + the AccessibilityService's WsRelayClient.
 *
 * D-198 v4.2: Added a master on/off toggle ([SETTING_ENABLED_KEY]). When the user toggles the
 * controller OFF in the settings screen, [ensureConnected] will NOT reconnect. The toggle state
 * is read from [SettingsRepository] on every check.
 *
 * Flow:
 *   1. [TestAccessibilityService.onServiceConnected] → [register] (stores a WeakReference to the client).
 *   2. `:app/src/debug/DebugInit.kt` `onActivityResumed(MainActivity)` → [ensureConnected].
 *   3. [ensureConnected] checks:
 *      a. Is the toggle ON? (read from SettingsRepository). If OFF → do nothing (stay disconnected).
 *      b. Is the client connected? If yes → toast "online".
 *      c. If disconnected → toast "reconnecting" + call client.start().
 *
 * D-198 v4.2.
 */
object TestControllerStatus {
    private const val TAG = "Anikuta:Test:Status"
    private const val COOLDOWN_MS = 10_000L  // only check once per 10s

    /** SettingsRepository key for the on/off toggle. */
    const val SETTING_ENABLED_KEY = "debug.test.enabled"

    @Volatile private var clientRef: WeakReference<WsRelayClient>? = null
    @Volatile private var overlayRef: WeakReference<ActionPreviewOverlay>? = null
    @Volatile private var lastCheckTime: Long = 0L

    /** Called by [TestAccessibilityService.onServiceConnected]. */
    fun register(client: WsRelayClient) {
        clientRef = WeakReference(client)
        Logger.i(TAG) { "WS client registered" }
    }

    /** Called by [TestAccessibilityService.onServiceConnected] to register the overlay. */
    fun registerOverlay(overlay: ActionPreviewOverlay) {
        overlayRef = WeakReference(overlay)
        Logger.i(TAG) { "overlay registered" }
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
     * Whether the test controller is enabled (the master toggle).
     * Reads from SettingsRepository. Default: true.
     */
    fun isEnabled(): Boolean {
        return try {
            val settings = GlobalContext.get().get<SettingsRepository>()
            settings.getSetting(SETTING_ENABLED_KEY)?.toBooleanStrictOrNull() ?: true
        } catch (e: Exception) {
            true  // Default to enabled if SettingsRepository isn't available yet.
        }
    }

    /**
     * Disconnect the client (D-198 v4.1 — user toggled the controller OFF).
     * The client will NOT auto-reconnect because [ensureConnected] checks [isEnabled] first.
     * D-198 v5.2: also clears all overlay dots from the screen.
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
        // D-198 v5.2: clear all overlay dots from the screen.
        overlayRef?.get()?.clearAllOverlays()
    }

    /**
     * Called from `:app/src/debug/DebugInit.kt`'s `onActivityResumed` hook (when the app opens).
     * Checks the client's state + shows a toast. If disconnected, triggers a reconnect.
     *
     * D-198 v4.2: If the master toggle is OFF, this does nothing — the controller stays off.
     *
     * Cooldown: only runs once per [COOLDOWN_MS]. Concurrent calls within the cooldown are skipped.
     */
    fun ensureConnected() {
        // D-198 v4.2: check the master toggle FIRST. If OFF, don't reconnect.
        if (!isEnabled()) {
            Logger.d(TAG) { "ensureConnected: controller is toggled OFF — skipping" }
            return
        }

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
            TestToaster.show("🔄 Reconnecting test controller…", throttleMs = 5000L)
            Logger.i(TAG) { "ensureConnected: client disconnected — triggering reconnect" }
            GlobalScope.launch(Dispatchers.IO) {
                runCatching { client.start() }
                    .onFailure { Logger.e(TAG) { "reconnect failed: ${it::class.java.simpleName}: ${it.message}" } }
            }
        }
    }
}
