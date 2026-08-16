package anikuta.buildlogic

/**
 * Shared Android configuration for all ANIKUTA modules.
 * Applied by the convention plugins in build-logic.
 */
object AndroidConfig {
    const val applicationId = "com.confused.anikuta"
    const val compileSdk = 36  // Kept at 36 (was originally for Nav3; Nav3 removed D-150, SDK left at 36 for Compose BOM 2025.03 + future-proofing)
    const val minSdk = 24
    const val targetSdk = 36
    const val versionCode = 18
    const val versionName = "0.2.17"

    // HARD RULE (CORE_RULES.md §8): ONLY arm64-v8a + armeabi-v7a. No x86/x86_64.
    val abiFilters = listOf("arm64-v8a", "armeabi-v7a")

    // JVM target for Kotlin + Java
    const val jvmTarget = "17"
}
