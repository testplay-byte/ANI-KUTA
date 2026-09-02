plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.download"
}

dependencies {
    implementation(project(":core:download"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:video-resolver"))

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

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.documentfile)

    debugImplementation(libs.androidx.ui.tooling)
}
