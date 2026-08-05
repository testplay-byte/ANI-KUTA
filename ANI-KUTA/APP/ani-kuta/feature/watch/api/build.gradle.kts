plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.feature.watch.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.serialization.json)
}
