plugins {
    id("anikuta.library")
}

android {
    namespace = "com.confused.anikuta.core.preferences"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
