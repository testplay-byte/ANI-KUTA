plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.activitytracker"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
