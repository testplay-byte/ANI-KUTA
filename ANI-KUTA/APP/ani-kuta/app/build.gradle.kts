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
    implementation(project(":core:playback-cache"))  // Video caching (test-feature branch)

    // Data modules
    implementation(project(":data:extension"))
    implementation(project(":data:cloudstream"))  // CloudStream V2: extension system runtime

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
    // CloudStream V2 (task 52): the dedicated CS watch screen — runs on the
    // Media3 engine (:core:cs-player), never touches the MPV watch stack.
    implementation(project(":feature:cs-watch:api"))
    implementation(project(":feature:cs-watch:impl"))
    // Task 58 (round 18 — downloads): the CS link models (CsVideoLink/
    // CsSubtitle) for the download-request builder + the resolve sheet's
    // download-mode callback (:feature:cs-watch:impl declares cs-player as
    // `implementation`, so it isn't transitively visible here).
    implementation(project(":core:cs-player"))
    implementation(project(":feature:anime-history:api"))
    implementation(project(":feature:anime-history:impl"))
    implementation(project(":feature:updates:api"))
    implementation(project(":feature:updates:impl"))
    implementation(project(":core:updates"))
    implementation(project(":core:schedule"))
    implementation(project(":core:ratings"))
    implementation(project(":core:notifications"))
    implementation(project(":core:app-update"))
    implementation(project(":core:ads"))  // Ad system — smart-link interstitial (D-272)
    implementation(project(":core:debug-api"))  // always on classpath (types only)

    // Debug bubble — debug builds only (D-163). Release builds contain zero
    // debug-bubble code. Wiring in :app/src/debug/DebugInit.kt.
    debugImplementation(project(":feature:debug-bubble"))

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // CloudStream V2: AppCompat — MainActivity extends AppCompatActivity so
    // CloudStream plugins receive an AppCompatActivity as their load() Context
    // (the documented plugin pattern stashes it for settings dialogs). Also
    // puts androidx.appcompat.* on the runtime classpath for plugin dexes that
    // reference it (parent-first resolution against the host).
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // DocumentFile — used by scanSubtitleFilesOnDisk (SAF subtitle scan in :app).
    // :core:download declares this as `implementation` so it isn't transitively
    // visible here; :app references DocumentFile directly, so it needs its own dep.
    implementation(libs.androidx.documentfile)

    // Compose
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
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

// ── D-322: dependency/runtime alignment guard ────────────────────────────────
//
// The v0.2.60 startup crash (NoSuchMethodError: sharedElement$default) happened
// because koin-compose 4.2.2 transitively requires androidx Compose 1.10.x,
// which silently overrode the compose BOM's 1.7.8 constraint in the PACKAGED
// runtime classpath while every module compiled against 1.7.8. A Gradle
// platform constraint can only RAISE versions — it can never cap a version a
// dependency requires, so the BOM never actually controlled what shipped.
//
// This task fails the build whenever the compose/lifecycle versions that would
// be packaged into the APK deviate from the explicit pins in
// gradle/libs.versions.toml. Any future dependency that tries to drag the
// compose line above the pin (the exact v0.2.60 failure mode) breaks the build
// loudly here instead of crashing on the user's device at startup.
//
// Scope notes:
//  - androidx.compose.material3 → the pinned material3 line (1.3.1).
//  - androidx.compose.material / material-icons-* → the pinned icons line
//    (1.7.8; icons artifacts were deprecated after 1.7.8).
//  - androidx.compose.material (non-icons: `material`, `material-ripple`) and
//    androidx.compose.material3.adaptive are UNPINNED internal transitives
//    (required only by material3 1.3.1 / seeker) — no app code compiles
//    against them, so their exact version is not asserted.
//  - Every other androidx.compose.* artifact must match the pinned compose line.
//  - androidx.lifecycle must match the pinned lifecycle line.
val composeLineVersion = libs.versions.compose.get()
val material3LineVersion = libs.versions.composeMaterial3.get()
val materialIconsLineVersion = libs.versions.composeMaterialIcons.get()
val lifecycleLineVersion = libs.versions.lifecycle.get()

tasks.register("checkDependencyAlignment") {
    doLast {
        fun expectedVersion(group: String, name: String): String? = when {
            group == "androidx.compose.material3" -> material3LineVersion
            group == "androidx.compose.material" && name.startsWith("material-icons") ->
                materialIconsLineVersion
            // Unpinned internal transitives — see scope notes above.
            group == "androidx.compose.material" -> null
            group == "androidx.compose.material3.adaptive" -> null
            group.startsWith("androidx.compose.") -> composeLineVersion
            group == "androidx.lifecycle" -> lifecycleLineVersion
            else -> null
        }

        val resolvedModules = configurations.getByName("releaseRuntimeClasspath")
            .resolvedConfiguration
            .lenientConfiguration
            .allModuleDependencies
            .associate { "${it.moduleGroup}:${it.moduleName}" to it.moduleVersion }

        val mismatches = resolvedModules.entries
            .sortedBy { it.key }
            .mapNotNull { (ga, version) ->
                val group = ga.substringBefore(':')
                val name = ga.substringAfter(':')
                val expected = expectedVersion(group, name) ?: return@mapNotNull null
                if (version != expected) "$ga — resolved $version, pinned $expected" else null
            }

        if (mismatches.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Dependency/runtime alignment failure (D-322 guard):")
                    appendLine("The compose/lifecycle versions that would be PACKAGED into the APK do not")
                    appendLine("match the project pins in gradle/libs.versions.toml — a dependency is pulling")
                    appendLine("a different version. Compile/runtime skew is how the v0.2.60 startup crash")
                    appendLine("happened (see D-322). Align the dependency or bump the pin deliberately:")
                    mismatches.forEach { appendLine("  - $it") }
                }
            )
        }

        val guarded = resolvedModules.count { (ga, _) ->
            expectedVersion(ga.substringBefore(':'), ga.substringAfter(':')) != null
        }
        logger.lifecycle("D-322 dependency alignment OK: $guarded compose/lifecycle artifacts match the pins.")
    }
}

tasks.named("preBuild") { dependsOn("checkDependencyAlignment") }
