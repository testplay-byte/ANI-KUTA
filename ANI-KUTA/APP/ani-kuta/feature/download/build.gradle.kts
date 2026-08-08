plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.download"
}

dependencies {
    implementation(project(":core:download"))
    implementation(project(":core:debug-api"))  // DB-7: LocalDebugContext
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:video-resolver"))

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
    implementation(libs.androidx.documentfile)

    debugImplementation(libs.androidx.ui.tooling)
}
