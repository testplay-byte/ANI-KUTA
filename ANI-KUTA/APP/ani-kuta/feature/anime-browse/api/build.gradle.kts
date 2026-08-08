plugins {
    alias(libs.plugins.kotlin.serialization)
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.feature.animebrowse.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(libs.kotlinx.serialization.json)
}
