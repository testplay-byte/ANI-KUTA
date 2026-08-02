plugins {
    id("anikuta.android.application.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    // Core modules
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:network"))
    implementation(project(":core:anilist"))
    implementation(project(":core:watch-progress"))
    implementation(project(":core:activity-tracker"))
    implementation(project(":core:provider-api"))
    implementation(project(":core:source-api"))
    implementation(project(":core:player-mpv-lib"))
    implementation(project(":core:player"))
    implementation(project(":core:video-resolver"))
    implementation(project(":core:download"))
    implementation(project(":core:metadata"))
    implementation(project(":core:tracker-api"))
    implementation(project(":core:tracker-anilist"))

    // Data modules
    implementation(project(":data:extension"))

    // Feature modules (impl — the app wires them)
    implementation(project(":feature:anime-browse:api"))
    implementation(project(":feature:anime-browse:impl"))
    implementation(project(":feature:anime-details:api"))
    implementation(project(":feature:anime-details:impl"))
    implementation(project(":feature:anime-library:api"))
    implementation(project(":feature:anime-library:impl"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
}
