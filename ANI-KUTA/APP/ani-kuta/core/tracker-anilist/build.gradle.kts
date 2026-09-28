plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.trackeranilist"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:tracker-api"))
    implementation(project(":core:preferences"))
    implementation(project(":core:anilist"))
    implementation(project(":core:database"))
    implementation(project(":core:content"))
    implementation(project(":core:activity-tracker"))
    // ROUND 102 (WS-E — the tracking contract): the TrackingWatchSyncBridge
    // observes the watch_progress table through the store — the reactive
    // reconciler that finally carries the player's episode completions to
    // AniList (run 36460088747's missing dependency, the only compile error).
    implementation(project(":core:watch-progress"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
