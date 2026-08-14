package com.confused.anikuta.core.testcontroller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
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
import okhttp3.OkHttpClient
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

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
 *   3. Construct the executor + providers + a dedicated OkHttpClient for the relay (separate
 *      from the app's default client so polling doesn't pollute DebugNetworkStats).
 *   4. Start the [RelayClient] poll loop.
 *
 * [onAccessibilityEvent] is a no-op (polling-driven, not event-driven). [onUnbind] stops the
 * relay client + cancels the scope. The system auto-restarts the service on process death
 * (state lost — the relay client re-reads config from SettingsRepository + reconnects).
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
    private var executor: TestControllerExecutor? = null
    private var relayClient: RelayClient? = null

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

        exec = TestControllerExecutor(
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
        executor = exec

        val relayHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS) // long-poll hold
            .retryOnConnectionFailure(true)
            .build()

        relayClient = RelayClient(
            appContext = applicationContext,
            executor = exec,
            settings = settingsRepo,
            httpClient = relayHttp,
            scope = scope,
        )
        relayClient?.start()
        Logger.i(TAG) { "test controller connected — relay client started (device=${settingsRepo.getSetting("debug.test.device_id") ?: "pending"})" }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Polling-driven — ignore events. (Could cache last event for diagnostics, but not needed.)
    }

    override fun onInterrupt() {
        Logger.w(TAG) { "test controller interrupted" }
    }

    override fun onUnbind(): Boolean {
        relayClient?.stop()
        relayClient = null
        executor = null
        scope.cancel()
        Logger.i(TAG) { "test controller unbound" }
        return super.onUnbind()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // Held as a field so the compiler doesn't complain about nullability in onServiceConnected.
    private lateinit var exec: TestControllerExecutor
}
