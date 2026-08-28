plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.core.ads"
}

dependencies {
    // Core modules — ads depends on :common (Logger), :preferences (PreferenceStore),
    // :designsystem (theme for the interstitial UI). Deliberately does NOT depend on
    // :navigation-api or any :feature — the ads system is a self-contained gate that
    // wraps a `() -> Unit` proceed-callback (CORE_RULES §5/§7: one responsibility,
    // defined contracts, isolated from the rest of the app per the user's request).
    implementation(project(":core:common"))
    implementation(project(":core:preferences"))
    implementation(project(":core:designsystem"))

    // Compose (BOM-managed) — for the SmartLinkAdInterstitial full-screen Dialog.
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)

    // Lifecycle — ProcessLifecycleOwner observes app foreground/background transitions
    // (ON_STOP / ON_START) so the coordinator can measure how long the user spent
    // outside the app in the browser. Not previously a dependency anywhere in the
    // project (CORE_RULES §8 research sub-agent confirmed) — added here.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Misc
    implementation(libs.androidx.core.ktx)
    implementation(libs.logcat)
    debugImplementation(libs.androidx.ui.tooling)
}
