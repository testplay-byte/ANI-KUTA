package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.preferences.SettingsRepository
import com.confused.anikuta.core.testapi.AppRouteRegistry
import com.confused.anikuta.feature.debugbubble.data.DebugDatabaseBrowser
import com.confused.anikuta.feature.debugbubble.data.DebugLogBuffer
import com.confused.anikuta.feature.debugbubble.data.DebugNetworkStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * The AccessibilityService that hosts the autonomous test-controller (D-197, D-199).
 *
 * Enabled by the user ONCE via Settings → Accessibility (cannot be enabled programmatically
 * without root/system-signing). Once enabled, the system binds + calls [onServiceConnected],
 * where we:
 *   1. Configure the service info (also set in `test_controller_service_config.xml`, but this
 *      ensures `canPerformGestures` + `flagRetrieveInteractiveWindows` + `flagReportViewIds`
 *      are applied even if the XML was stripped).
 *   2. Resolve all deps from Koin (GlobalContext — AnikutaApp has already started Koin before
 *      the service binds). The deps: DebugLogBuffer, DebugNetworkStats, DebugDatabaseBrowser,
 *      AnikutaDatabase, SettingsRepository, AppRouteRegistry (the last is implemented in
 *      `:app/src/debug` + registered in `debugKoinModules()`).
 *   3. Construct the executor + providers.
 *   4. Start the [MqttBridge] — connects to the public MQTT broker (hardcoded, no user config).
 *
 * [onAccessibilityEvent] is a no-op. [onUnbind] stops the MQTT bridge + cancels the scope.
 * The system auto-restarts the service on process death (state lost — the bridge reconnects
 * automatically via Paho's auto-reconnect).
 *
 * **D-198 v2**: replaced ntfy.sh + Bun HTTP relay with MQTT for plug-and-play (no user config,
 * no URL discovery, no persistent background process in the sandbox).
 *
 * **Release builds**: this class is NOT on the classpath (`:core:test-controller` is
 * `debugImplementation` in `:app`), and the `<service>` declaration is only in the debug
 * manifest merge. Zero test-controller code in release APKs (D-202).
 */
class TestAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "Anikuta:Test:Service"
        private const val OUR_PACKAGE = "com.confused.anikuta"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mqttBridge: MqttBridge? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Re-assert the service config (belt + suspenders — the XML should already set this,
        // but some OEM builds ignore parts of the XML meta-data).
        runCatching {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                notificationTimeout = 100
                packageNames = arrayOf(OUR_PACKAGE)
            }
            serviceInfo = info
        }.onFailure { Logger.w(TAG) { "serviceInfo re-assert failed: ${it.message}" } }

        // Resolve all deps from Koin. AnikutaApp.onCreate has run before the service binds.
        val koin = GlobalContext.get()
        val logBuffer = koin.get<DebugLogBuffer>()
        val networkStats = koin.get<DebugNetworkStats>()
        val dbBrowser = koin.get<DebugDatabaseBrowser>()
        val database = koin.get<AnikutaDatabase>()
        val settingsRepo = koin.get<SettingsRepository>()
        val routeRegistry = koin.get<AppRouteRegistry>()

        val treeSerializer = AccessibilityTreeSerializer()
        val gestureExecutor = GestureExecutor(this, treeSerializer)
        val screenshotCapture = ScreenshotCapture(this)
        val navExecutor = NavExecutor(routeRegistry)

        val executor = TestControllerExecutor(
            service = this,
            scope = scope,
            treeSerializer = treeSerializer,
            gestureExecutor = gestureExecutor,
            screenshotCapture = screenshotCapture,
            logcatProvider = LogcatProvider(logBuffer),
            networkLogsProvider = NetworkLogsProvider(networkStats),
            activityLogsProvider = ActivityLogsProvider(database),
            databaseProvider = DatabaseProvider(applicationContext, dbBrowser),
            preferencesProvider = PreferencesProvider(settingsRepo),
            navExecutor = navExecutor,
        )

        // D-198 v2: MQTT bridge — connects to the public broker (hardcoded, no user config).
        // Auto-reconnect handles broker drops. The agent sends commands via one-shot MQTT
        // publish (no persistent process on the agent side either).
        mqttBridge = MqttBridge(executor = executor, scope = scope)
        scope.launch { mqttBridge?.start() }
        Logger.i(TAG) { "test controller connected — MQTT bridge starting (broker=hivemq, channel=anikuta/test/v1)" }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Polling-driven — ignore events. (Could cache last event for diagnostics, but not needed.)
    }

    override fun onInterrupt() {
        Logger.w(TAG) { "test controller interrupted" }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mqttBridge?.stop()
        mqttBridge = null
        scope.cancel()
        Logger.i(TAG) { "test controller unbound" }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
