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
    implementation(project(":core:provider-api"))  // CloudStream V2: InstallStep moved here (ecosystem-shared install lifecycle states)
    implementation(project(":data:extension"))
    implementation(project(":data:cloudstream"))  // CloudStream V2: the CS extensions section + plugin detail screens

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    // Phase 4: SourcePreferencesScreen (PreferenceFragmentCompat).
    // D-414 (round 33): these were HARDCODED (bypassing the catalog — the
    // appcompat pin even conflicted with the catalog's 1.7.1); now catalog refs.
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)

    debugImplementation(libs.androidx.ui.tooling)
}
