plugins {
    id("anikuta.library.compose")
}

android {
    namespace = "com.confused.anikuta.core.designsystem"
}

dependencies {
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.foundation)
    // D-320: SharedTransitionScope/AnimatedVisibilityScope for the experimental
    // cover transition locals + helper.
    implementation(libs.androidx.compose.animation)  // D-322: explicit 1.10.4 pin — API renamed on this line (see SharedTransitionLocals.kt)
    implementation(libs.androidx.material.icons.extended)  // D-322: explicit pin (icons deprecated after 1.7.8)
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
