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

        // HARD RULE (CORE_RULES.md §8): ONLY arm64-v8a + armeabi-v7a. No x86/x86_64.
        ndk {
            abiFilters += anikuta.buildlogic.AndroidConfig.abiFilters
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
