plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.download"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:network"))
    implementation(project(":core:content"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.documentfile)
    implementation(libs.logcat)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
