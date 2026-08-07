// build-logic: provides convention plugins for ANIKUTA modules.
// Plugins: anikuta.library, anikuta.library.compose,
//          anikuta.android.application, anikuta.android.application.compose

plugins {
    `kotlin-dsl`
}

dependencies {
    // Maven artifact coordinates (NOT plugin IDs) for the Gradle plugins.
    // These are needed so the precompiled script plugins can apply them.
    implementation("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
}
