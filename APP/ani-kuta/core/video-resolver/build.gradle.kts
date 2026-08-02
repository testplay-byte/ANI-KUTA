plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.videoresolver"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:source-api"))
    implementation(project(":core:provider-api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.rxjava)
    implementation(libs.logcat)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
