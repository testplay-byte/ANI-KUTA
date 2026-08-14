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
 *   4. Start the [WsRelayClient] — connects to the sandbox WebSocket relay.
 *
 * [onAccessibilityEvent] is a no-op. [onUnbind] stops the WS client + cancels the scope.
 * The system auto-restarts the service on process death (state lost — the client reconnects
 * automatically).
 *
 * **D-198 v3**: replaced MQTT with a sandbox-hosted WebSocket relay (the MQTT approach failed
 * because all 4 public brokers timed out on the user's mobile network — carrier blocking
 * MQTT ports). The WS relay runs on port 3030 inside the Next.js dev server (persistent),
 * and the phone connects via wss://PUBLIC_URL/?XTransformPort=3030 (port 443, no carrier blocks).
 * The user configures the relay URL in the TestControllerSettingsScreen (More → Settings).
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
    private var wsClient: WsRelayClient? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        // D-198 v4.1: DO NOT override serviceInfo — the XML config (test_controller_service_config.xml)
        // has canRetrieveWindowContent="true" + canPerformGestures="true". Overriding serviceInfo
        // programmatically creates a new AccessibilityServiceInfo() that loses these XML-only
        // attributes (they have no programmatic setter). This was the root cause of:
        //   - getRootInActiveWindow() returning null (canRetrieveWindowContent lost)
        //   - dispatchGesture callbacks never firing (canPerformGestures lost)
        // The XML config is sufficient — just log what the system gave us for diagnostics.
        runCatching {
            val info = serviceInfo
            if (info != null) {
                Logger.i(TAG) {
                    "service config: canRetrieveWindowContent=${info.canRetrieveWindowContent}, " +
                    "flags=0x${info.flags.toString(16)}"
                }
            } else {
                Logger.w(TAG) { "serviceInfo is null — XML config not loaded" }
            }
        }

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
        val actionPreviewOverlay = ActionPreviewOverlay(this)

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
            actionPreviewOverlay = actionPreviewOverlay,
        )

        // D-198 v3: WebSocket relay client — connects to the sandbox relay.
        // The relay URL is configured by the user in the TestControllerSettingsScreen.
        // Auto-reconnect handles relay drops.
        TestToaster.init(applicationContext)
        wsClient = WsRelayClient(executor = executor, settings = settingsRepo, scope = scope)
        TestControllerStatus.register(wsClient!!)
        TestToaster.show("🔌 Test controller starting…")
        scope.launch { wsClient?.start() }
        Logger.i(TAG) { "test controller connected — WS relay client starting" }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Polling-driven — ignore events. (Could cache last event for diagnostics, but not needed.)
    }

    override fun onInterrupt() {
        Logger.w(TAG) { "test controller interrupted" }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        wsClient?.let { TestControllerStatus.unregister(it) }
        wsClient?.stop()
        wsClient = null
        scope.cancel()
        Logger.i(TAG) { "test controller unbound" }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
