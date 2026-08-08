plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.feature.updates.api"
}

dependencies {
    implementation(project(":core:navigation-api"))
    implementation(libs.kotlinx.serialization.json)
}
