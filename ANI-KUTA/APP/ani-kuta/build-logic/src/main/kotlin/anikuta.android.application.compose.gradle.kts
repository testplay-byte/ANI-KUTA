// Convention plugin: Android application module with Compose
// Usage: plugins { id("anikuta.android.application.compose") }

plugins {
    id("anikuta.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}
