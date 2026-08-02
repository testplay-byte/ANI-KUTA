plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.player"

    defaultConfig {
        // Player needs the MPV native libs
        ndk {
            abiFilters += anikuta.buildlogic.AndroidConfig.abiFilters
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))
    implementation(project(":core:watch-progress"))
    implementation(project(":core:source-api"))

    // MPV native lib (via wrapper module)
    api(project(":core:player-mpv-lib"))

    // Seeker — Compose seekbar (for controls, Phase 4)
    implementation(libs.seeker)

    // Media session — for background media controls
    implementation(libs.androidx.media)

    // TrueType parser — for subtitle font parsing
    implementation(libs.truetype.parser)

    // Coil3 — for episode thumbnails in switching overlay (Phase 4)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    // Coroutines + serialization
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logcat)
}
