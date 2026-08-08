plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.ratings"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.sqldelight.coroutines.extensions)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
}
