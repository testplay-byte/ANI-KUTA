plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.extensionssettings.impl"
}

dependencies {
    implementation(project(":feature:extensions-settings:api"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:navigation-api"))
    implementation(project(":core:preferences"))
    implementation(project(":core:source-api"))
    implementation(project(":data:extension"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // Phase 4: SourcePreferencesScreen (PreferenceFragmentCompat)
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    debugImplementation(libs.androidx.ui.tooling)
}
