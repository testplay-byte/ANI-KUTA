plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.notifications"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:content"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
