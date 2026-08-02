// Convention plugin: Android library module
// Usage in module build.gradle.kts: plugins { id("anikuta.library") }

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = anikuta.buildlogic.AndroidConfig.compileSdk

    defaultConfig {
        minSdk = anikuta.buildlogic.AndroidConfig.minSdk
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
