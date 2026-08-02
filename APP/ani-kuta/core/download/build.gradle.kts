plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.download"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:network"))

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logcat)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
