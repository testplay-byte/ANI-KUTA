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
    //
    // ── Task 53 (round 13) — the playback-fixes release ─────────────────────
    // 0.4.1 root-causes every v0.4.0 device finding (doc cloudstream-v2/04):
    // RC-1 vendored M3u8Helper's invented referer param (AniKoto's 0-links,
    // 19 s silent walk), RC-2 the player's default Mobile-Chrome UA (the 428
    // class on UA-picky CDNs) + clean-retry profile, RC-3 the collectAsState
    // one-dispatch lag that replayed the previous episode's link (generation
    // lock + engine hard-reset), RC-4 upstream's 120 s loadLinks timeout,
    // RC-6/7 the AnymeX-pattern resolve sheet + Sources/Audio&Subs sheets,
    // RC-8 the diagnosability overhaul (request-profile logging, one filter).
    const val versionCode = 66
    const val versionName = "0.4.1"

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
