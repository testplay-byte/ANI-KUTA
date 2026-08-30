// :feature:cs-watch:impl — the dedicated CloudStream watch screen (task 52 / round 12).
//
// A Compose screen that MIRRORS the aniyomi watch UX (player surface + glass
// controls + links/quality sheet + subtitle sheet + episode switching + watch
// progress) while running on the dedicated Media3 engine (:core:cs-player).
// It shares ZERO code with :feature:watch:impl — the MPV screen stays
// byte-identical (CORE_RULES §5's player carve-out applies here the same way:
// the screen composable owns the player view + lifecycle effects; a plain
// state holder holds observable state).
plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.cswatch.impl"
}

dependencies {
    implementation(project(":feature:cs-watch:api"))
    implementation(project(":core:cs-player"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:preferences"))
    implementation(project(":core:watch-progress"))  // SAME provider-agnostic store as the aniyomi screen
    implementation(project(":data:cloudstream"))     // CloudstreamLinkResolver (Phase C)

    // Media3 — the engine types flow through :core:cs-player's public surface
    // (Player access for PlayerView + track enumeration in the sheets).
    implementation(libs.media3.common)
    implementation(libs.media3.ui)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.ktx)  // WindowInsetsControllerCompat (immersive mode scaffolding)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.kotlinx.coroutines.android)

    // Coil3 — episode thumbnails in the episodes sheet.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.ui.tooling)
}
