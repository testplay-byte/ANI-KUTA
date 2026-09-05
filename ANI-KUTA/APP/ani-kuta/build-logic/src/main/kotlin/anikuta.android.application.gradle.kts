// Convention plugin: Android application module
// Usage: plugins { id("anikuta.android.application") }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = anikuta.buildlogic.AndroidConfig.compileSdk

    defaultConfig {
        applicationId = anikuta.buildlogic.AndroidConfig.applicationId
        minSdk = anikuta.buildlogic.AndroidConfig.minSdk
        targetSdk = anikuta.buildlogic.AndroidConfig.targetSdk
        versionCode = anikuta.buildlogic.AndroidConfig.versionCode
        versionName = anikuta.buildlogic.AndroidConfig.versionName

        // ABI POLICY (CORE_RULES.md §8, D-423 round 35): arm64-v8a for the
        // dev/CI verification line; `-PreleaseAllAbis=true` (the tag-driven
        // release-apk.yml workflow ONLY) expands the set to ALL FOUR ABIs so
        // the app module's splits block emits one APK per ABI + universal.
        // EXCEPTION (D-246, user-authorized): `-PemulatorX64Build=true` swaps
        // the ABI set to x86_64 for the CI TEST-ONLY emulator artifact.
        ndk {
            val isEmulatorBuild = (findProperty("emulatorX64Build") as? String) == "true"
            val isReleaseAllAbis = (findProperty("releaseAllAbis") as? String) == "true"
            abiFilters += when {
                isEmulatorBuild -> anikuta.buildlogic.AndroidConfig.emulatorAbiFilters
                isReleaseAllAbis -> anikuta.buildlogic.AndroidConfig.releaseAllAbiFilters
                else -> anikuta.buildlogic.AndroidConfig.abiFilters
            }
        }
    }

    buildTypes {
        // D-436 (round 38 — the R8 retirement): minification is REMOVED from
        // the release line per the user's explicit instruction. The v1.1.1
        // device round proved it: the obfuscated release builds broke the
        // extension system (plugins loaded via DexClassLoader resolve HOST
        // classes by their COMPILED names — some extensions loaded, others
        // failed, and the ones that loaded returned no results), while the
        // unminified debug build with identical code worked properly. The
        // D-413 keep-rule set covered every name-resolved surface we could
        // enumerate (com.lagradost/eu.kanade/injekt/gson/okhttp/kotlin/JNI/
        // WorkManager/serialization companions) — but keep-rule coverage can
        // never be guaranteed COMPLETE for a plugin ecosystem: R8's
        // optimization passes (inlining, merging, dead-code elimination)
        // alter runtime-resolved behavior beyond renaming. So the release
        // line ships UNMINIFIED: no obfuscation, no code shrinking, no
        // resource shrinking. app/proguard-rules.pro was DELETED with this
        // change (D-413's record lives in decisions.md); if minification is
        // ever deliberately re-enabled, the rules must be re-created from
        // that record first. The APK size cost is accepted (the app is
        // native-lib dominated).
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = anikuta.buildlogic.AndroidConfig.jvmTarget
    }

    buildFeatures {
        buildConfig = true
    }
}
