plugins {
    alias(libs.plugins.kotlin.serialization)
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.feature.animesearch.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(libs.kotlinx.serialization.json)
}
