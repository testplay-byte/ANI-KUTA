plugins {
    id("anikuta.library.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.animedetails"
}

dependencies {
    implementation(project(":feature:anime-details:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:anilist"))
    implementation(project(":core:common"))
    implementation(project(":core:metadata"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:preferences"))
    implementation(project(":core:source-api"))
    implementation(project(":core:video-resolver"))
    implementation(project(":core:smart-matcher"))
    implementation(project(":core:content"))
    implementation(project(":core:data-cache"))
    implementation(project(":core:download"))
    implementation(project(":core:watch-progress"))
    implementation(project(":data:extension"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.rxjava)

    debugImplementation(libs.androidx.ui.tooling)
}
