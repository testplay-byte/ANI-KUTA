plugins {
    alias(libs.plugins.kotlin.serialization)
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.navigation"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
