plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.anilist"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
