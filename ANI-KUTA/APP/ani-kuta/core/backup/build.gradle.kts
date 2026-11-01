plugins {
    id("anikuta.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.confused.anikuta.core.backup"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    // The SAF folder preference + the backup location contract live here.
    implementation(project(":core:download"))
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
