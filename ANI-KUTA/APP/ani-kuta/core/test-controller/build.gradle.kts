plugins {
    alias(libs.plugins.kotlin.serialization)
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.testcontroller"
}

dependencies {
    // Types (TestCommand/TestResult, DebugNavRegistry, DebugWindowRegistry, AppRouteRegistry, constants).
    implementation(project(":core:test-api"))
    // NavKey marker.
    implementation(project(":core:navigation-api"))
    // Logger, LogLevel, LogAppender.
    implementation(project(":core:common"))
    // AnikutaDatabase (for activity-event queries).
    implementation(project(":core:database"))
    // SettingsRepository (for get_preference / set_preference).
    implementation(project(":core:preferences"))

    // Reuse the debug-bubble's data classes (DebugLogBuffer, DebugNetworkStats,
    // DebugDatabaseBrowser) — D-201. Both modules are debug-only; the data classes
    // don't reference Compose, so this module stays Compose-free.
    // Coupling note: could be refactored into a shared `:core:debug-data` later.
    implementation(project(":feature:debug-bubble"))

    // OkHttp — used for the WebSocket relay client (WsRelayClient) + DebugNetworkStats
    // (reused from debug-bubble) extends okhttp3.Interceptor. D-198 v3: replaced Paho MQTT
    // with OkHttp WebSocket (more reliable on mobile networks, uses port 443 via Caddy).
    implementation(libs.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
