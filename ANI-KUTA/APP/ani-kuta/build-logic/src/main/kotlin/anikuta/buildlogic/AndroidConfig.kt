package anikuta.buildlogic

/**
 * Shared Android configuration for all ANIKUTA modules.
 * Applied by the convention plugins in build-logic.
 */
object AndroidConfig {
    const val applicationId = "com.confused.anikuta"
    const val compileSdk = 36  // Nav3 1.1.5 requires SDK 36
    const val minSdk = 24
    const val targetSdk = 36
    const val versionCode = 1
    const val versionName = "0.1.0"

    // HARD RULE (CORE_RULES.md §8): ONLY arm64-v8a + armeabi-v7a. No x86/x86_64.
    val abiFilters = listOf("arm64-v8a", "armeabi-v7a")

    // JVM target for Kotlin + Java
    const val jvmTarget = "17"
}
