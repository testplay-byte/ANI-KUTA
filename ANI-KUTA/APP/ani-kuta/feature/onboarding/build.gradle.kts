plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.feature.onboarding"
}

dependencies {
    // D-403 (round 28): the onboarding setup wizard — a SINGLE module (the
    // feature:download precedent, no api/impl split): the only external
    // consumer is MainActivity, and the wizard has no cross-feature contracts.
    implementation(project(":core:download")) // DownloadPreferences (the SAF folder pref)
    implementation(project(":core:preferences")) // the Preference<T> class (downloadFolderUri's return type — core:download's implementation dep is NOT transitive)
    implementation(project(":core:designsystem"))
    implementation(project(":core:common"))
    implementation(project(":core:navigation-api"))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
    implementation(libs.androidx.activity.compose) // rememberLauncherForActivityResult
    implementation(libs.androidx.lifecycle.runtime.compose) // LocalLifecycleOwner (ON_RESUME re-verification)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose.viewmodel) // koinInject comes from koin-compose (transitive)

    implementation(libs.androidx.documentfile) // the folder-validity verification

    debugImplementation(libs.androidx.ui.tooling)

    // D-403: the wizard step machine is pure logic — unit-tested.
    testImplementation(libs.junit)
}
