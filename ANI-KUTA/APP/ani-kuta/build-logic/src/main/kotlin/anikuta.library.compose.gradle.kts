// Convention plugin: Android library module with Compose
// Usage: plugins { id("anikuta.library.compose") }

plugins {
    id("anikuta.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures {
        compose = true
    }
}
