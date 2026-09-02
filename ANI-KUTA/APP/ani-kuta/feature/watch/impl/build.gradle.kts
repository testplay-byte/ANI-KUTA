plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.watch.impl"
}

dependencies {
    implementation(project(":feature:watch:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:player"))
    implementation(project(":core:player-mpv-lib"))
    implementation(project(":core:preferences"))
    implementation(project(":core:video-resolver"))
    implementation(project(":core:watch-progress"))
    implementation(project(":core:ratings"))  // Phase 4: per-episode rating UI
    implementation(project(":core:download"))
    implementation(project(":core:playback-cache"))  // Video caching (test-feature branch)
    implementation(project(":core:activity-tracker"))  // D-192: activity tracking
    implementation(project(":data:extension"))
    implementation(project(":core:source-api"))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.kotlinx.coroutines.android)

    // Coil3 — for episode thumbnails in the watch page episode list
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.ui.tooling)
}
