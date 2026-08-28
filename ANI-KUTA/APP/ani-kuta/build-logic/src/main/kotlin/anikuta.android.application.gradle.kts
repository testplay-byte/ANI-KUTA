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
        getByName("release") {
            isMinifyEnabled = false
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
