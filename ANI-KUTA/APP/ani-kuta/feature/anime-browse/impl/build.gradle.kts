plugins {
    id("anikuta.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.animebrowse"
}

dependencies {
    implementation(project(":feature:anime-browse:api"))
    implementation(project(":feature:anime-details:api"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:preferences"))  // D-320: cover-transition gate
    implementation(project(":core:anilist"))
    implementation(project(":core:common"))
    implementation(project(":core:data-cache"))
    // Phase 3: continue-watching carousel
    implementation(project(":core:watch-progress"))
    implementation(project(":core:content"))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)
}
