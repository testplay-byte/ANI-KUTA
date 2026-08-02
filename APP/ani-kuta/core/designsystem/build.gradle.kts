plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.core.designsystem"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.ui.tooling)
}
