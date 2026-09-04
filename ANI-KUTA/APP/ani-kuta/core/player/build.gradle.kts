plugins {
    id("anikuta.library.compose")
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
    implementation(project(":core:designsystem"))
    implementation(project(":core:preferences"))
    implementation(project(":core:watch-progress"))
    implementation(project(":core:source-api"))
    implementation(project(":core:network"))

    // OkHttp — for subtitle engine (downloading external subtitle files to temp)
    implementation(libs.okhttp)

    // Compose (for controls)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)

    // MPV native lib (via wrapper module)
    api(project(":core:player-mpv-lib"))

    // D-414 (round 33): seeker (Compose seekbar — the controls are hand-
    // rolled), androidx.media (the old non-media3 media lib — zero imports;
    // the CS player stack uses Media3), and truetype-parser (zero imports)
    // REMOVED — verified unused before removal.

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
}
