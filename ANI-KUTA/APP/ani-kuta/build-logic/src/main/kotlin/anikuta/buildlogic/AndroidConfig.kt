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
    // ── CloudStream V2 era (Task 51 / round 11) ──────────────────────────────
    // The CloudStream rebuild on streaming/CLOUDSTREAM-V2 (forked from main
    // v0.2.63) opens the 0.3.x line: a fresh, modular, well-documented CS
    // implementation (plugin system + repos + trust UI + search + details +
    // episodes/seasons; playback deliberately excluded until its own port).
    // ── Task 52 (round 12) — the playback port ───────────────────────────────
    // 0.4.0 completes the CS system: link resolution (loadLinks), the dedicated
    // Media3 ExoPlayer engine (:core:cs-player) and the dedicated CS watch
    // screen (:feature:cs-watch) — the aniyomi playback stack untouched.
    // Minor-bump precedent: headline features get the minor (0.2.63 → 0.3.0).
    // versionCode continues the monotonic line from 0.3.0's 64.
    const val versionCode = 65
    const val versionName = "0.4.0"

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
