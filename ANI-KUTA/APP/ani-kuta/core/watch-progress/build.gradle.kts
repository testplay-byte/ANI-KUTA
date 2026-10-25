plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.watchprogress"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    // Round 121: the shared PlaybackResumePolicy's contract locks (moved
    // here from :core:player so every consumer reaches it via this module).
    // Round 127 (D-789): the cumulative played-time lock tests run the REAL
    // store against in-memory SQLite — the auto-mark's two-condition gate is
    // tested, not assumed (the user is not device-testing this round).
    testImplementation(libs.junit)
    testImplementation(libs.sqldelight.sqlite.driver)
    testImplementation(libs.kotlinx.coroutines.test)
}
