// build-logic: provides convention plugins for ANIKUTA modules.
// Plugins: anikuta.library, anikuta.library.compose,
//          anikuta.android.application, anikuta.android.application.compose

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.plugins.android.application.get().toString())
    implementation(libs.plugins.android.library.get().toString())
    implementation(libs.plugins.kotlin.android.get().toString())
    implementation(libs.plugins.kotlin.compose.get().toString())
}
