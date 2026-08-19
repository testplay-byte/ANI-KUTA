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
    implementation("androidx.compose.material:material-icons-extended")
    // D-223: Palette API for adaptive color extraction from cover images.
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.core.ktx)
    // D-223: Coil3 for loading cover images as Bitmaps for Palette extraction.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // D-223: Logger for color extraction logging.
    implementation(project(":core:common"))
    debugImplementation(libs.androidx.ui.tooling)
}
