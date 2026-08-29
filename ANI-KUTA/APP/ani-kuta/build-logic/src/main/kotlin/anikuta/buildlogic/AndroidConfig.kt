package anikuta.buildlogic

/**
 * Shared Android configuration for all ANIKUTA modules.
 * Applied by the convention plugins in build-logic.
 */
object AndroidConfig {
    const val applicationId = "com.confused.anikuta"
    const val compileSdk = 36  // Kept at 36 (was originally for Nav3; Nav3 removed D-150, SDK left at 36 for the compose 1.10 line + future-proofing — D-322)
    const val minSdk = 24
    const val targetSdk = 36

    // Session 3 (device round 2): version bump discipline — EVERY build the
    // user can download gets a new versionCode/versionName so device-side
    // verification is unambiguous (the round-2 report flagged the stuck 0.2.63
    // across the Task 41/42 builds).
    //
    // Task 44 (device round 3): 0.2.65 — activity-context plugin loading
    // (ClassCastException fix), Cloudflare bypass interceptor, sectioned
    // browse rows, plugin-detail button layout, retry spinners.
    const val versionCode = 65
    const val versionName = "0.2.65"

    // HARD RULE (CORE_RULES.md §8, updated D-251 per user instruction): ONLY
    // arm64-v8a in SHIPPED APKs. No armeabi-v7a, no x86/x86_64.
    // EXCEPTION (user-authorized, D-246 emulator-testing support): a TEST-ONLY
    // x86_64 build is produced in CI via `-PemulatorX64Build=true` — it goes to a
    // SEPARATE artifact and never ships. The main APK stays arm64-v8a-only.
    val abiFilters = listOf("arm64-v8a")

    /** ABIs for the CI emulator-test build (native x86_64 — no ARM translation). */
    val emulatorAbiFilters = listOf("x86_64")

    // JVM target for Kotlin + Java
    const val jvmTarget = "17"
}
