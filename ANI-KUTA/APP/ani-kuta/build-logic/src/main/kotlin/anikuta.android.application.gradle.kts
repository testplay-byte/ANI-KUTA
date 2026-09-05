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
