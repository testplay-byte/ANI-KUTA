plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.debugbubble"
}

dependencies {
    implementation(project(":core:debug-api"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:preferences"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
