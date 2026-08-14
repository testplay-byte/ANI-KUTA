plugins {
    id("anikuta.android.application.compose")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ── Signing config: fixed debug keystore for consistent APK updates ──
    // Per user request: "sign the APKs with some temporary random key so that
    // I do not have to uninstall the old one. I can directly update it."
    // This keystore is committed to the repo (it's a debug key, not a release key).
    // Phase 9 will replace with a proper release signing setup.
    signingConfigs {
        create("anikutaDebug") {
            storeFile = file("anikuta-debug.keystore")
            storePassword = "anikuta"
            keyAlias = "anikuta"
            keyPassword = "anikuta"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("anikutaDebug")
        }
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
    implementation(project(":core:smart-matcher"))
    implementation(project(":core:content"))
    implementation(project(":core:data-cache"))

    // Data modules
    implementation(project(":data:extension"))

    // Feature modules (impl — the app wires them)
    implementation(project(":feature:anime-browse:api"))
    implementation(project(":feature:anime-browse:impl"))
    implementation(project(":feature:anime-details:api"))
    implementation(project(":feature:anime-details:impl"))
    implementation(project(":feature:anime-library:api"))
    implementation(project(":feature:anime-library:impl"))
    implementation(project(":feature:anime-search:api"))
    implementation(project(":feature:anime-search:impl"))
    implementation(project(":feature:extensions-settings:api"))
    implementation(project(":feature:extensions-settings:impl"))
    implementation(project(":feature:download"))
    implementation(project(":feature:watch:api"))
    implementation(project(":feature:watch:impl"))
    implementation(project(":feature:anime-history:api"))
    implementation(project(":feature:anime-history:impl"))
    implementation(project(":feature:updates:api"))
    implementation(project(":feature:updates:impl"))
    implementation(project(":core:updates"))
    implementation(project(":core:schedule"))
    implementation(project(":core:ratings"))
    implementation(project(":core:notifications"))
    implementation(project(":core:debug-api"))  // always on classpath (types only)
    implementation(project(":core:test-api"))   // D-197: test-controller types (always on classpath; debug-only impl)

    // Debug bubble — debug builds only (D-163). Release builds contain zero
    // debug-bubble code. Wiring in :app/src/debug/DebugInit.kt.
    debugImplementation(project(":feature:debug-bubble"))

    // Test controller — debug builds only (D-197). AccessibilityService + relay client for
    // autonomous remote testing. Release builds contain zero test-controller code.
    // Wiring in :app/src/debug/ (AppRouteRegistryImpl, DebugNavBinder, DebugInit extensions).
    debugImplementation(project(":core:test-controller"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // DocumentFile — used by scanSubtitleFilesOnDisk (SAF subtitle scan in :app).
    // :core:download declares this as `implementation` so it isn't transitively
    // visible here; :app references DocumentFile directly, so it needs its own dep.
    implementation(libs.androidx.documentfile)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.androidx.ui.tooling)

    // Navigation — hand-rolled (D-150): mutableStateListOf<NavKey> + when(currentKey).
    // Nav3 (androidx.navigation3) was removed; no navigation dependency here.

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization (for Injekt Json registration in AnikutaApp)
    implementation(libs.kotlinx.serialization.json)

    // D.4: Coil image loading (for ImageLoaderFactory — 500MB disk cache)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
