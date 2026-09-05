plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.preferences"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
