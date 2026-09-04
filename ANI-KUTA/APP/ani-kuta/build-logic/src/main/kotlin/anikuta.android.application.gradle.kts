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

        // HARD RULE (CORE_RULES.md §8, updated D-251): ONLY arm64-v8a in SHIPPED
        // APKs. No armeabi-v7a, no x86/x86_64.
        // EXCEPTION (D-246, user-authorized): `-PemulatorX64Build=true` swaps the ABI
        // set to x86_64 for the CI TEST-ONLY emulator artifact (separate build + upload
        // step — the shipped APK is always the arm64-v8a build above).
        ndk {
            val isEmulatorBuild = (findProperty("emulatorX64Build") as? String) == "true"
            abiFilters += if (isEmulatorBuild) {
                anikuta.buildlogic.AndroidConfig.emulatorAbiFilters
            } else {
                anikuta.buildlogic.AndroidConfig.abiFilters
            }
        }
    }

    buildTypes {
        // D-413 (round 33 — the v1.1.1 publishable release): R8 FULL MODE
        // (obfuscation + shrinking + optimization; the AGP default) plus
        // resource shrinking. The keep rules live in app/proguard-rules.pro —
        // they cover every surface that is resolved BY NAME at runtime (the
        // DexClassLoader plugin-compat classpath, the MPV/FFmpeg JNI
        // surfaces, WorkManager workers, kotlinx-serialization companions).
        // DexGuard (the commercial Guardsquare product) was assessed and
        // deliberately not used — it cannot be provisioned without a paid
        // per-company license; R8 full mode is the standard equivalent.
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
