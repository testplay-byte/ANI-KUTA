plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.datacache"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.logcat)

    // SQLDelight
    implementation(libs.sqldelight.android.driver)
    implementation(libs.sqldelight.coroutines.extensions)

    // Koin
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
