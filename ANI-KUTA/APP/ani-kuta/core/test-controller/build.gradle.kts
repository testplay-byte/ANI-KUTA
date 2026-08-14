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
    // SettingsRepository (for get_preference / set_preference + relay config storage).
    implementation(project(":core:preferences"))

    // Reuse the debug-bubble's data classes (DebugLogBuffer, DebugNetworkStats,
    // DebugDatabaseBrowser) — D-201. Both modules are debug-only; the data classes
    // don't reference Compose, so this module stays Compose-free.
    // Coupling note: could be refactored into a shared `:core:debug-data` later.
    implementation(project(":feature:debug-bubble"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
