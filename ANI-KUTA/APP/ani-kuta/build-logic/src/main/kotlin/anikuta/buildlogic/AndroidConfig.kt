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
    // ── Task 56 (round 16) — the device-feedback-fixes release ─────────────
    // 0.4.4 fixes the five v0.4.3 device findings (doc cloudstream-v2/07):
    // F1 the resolve sheet NEVER auto-opens playback (remembered-server +
    // single-link auto-selects removed — the user always picks), F2 quality
    // chips sort highest-LEFTMOST with Unknown/Auto at the far right (both
    // stacks — the aniyomi accordion had NO sort at all), F3 sub/dub episode
    // lists show per-flavor ordinals (Dub restarts at "EP 1") + tag-stripped
    // names, F4 COMBINED mode merges sibling rows pairwise by ORDINAL (the
    // global-numbering reality broke the round-15 number-equality pairing —
    // 12+12 shows now render 12 rows and a tap resolves BOTH flavors), F5 the
    // LazyColumn duplicate-key crash on multi-quality DASH manifests (all raw
    // lists key by row index). Auto-advance stays within the current flavor.
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
    // ── Task 54 (round 14) — the watch-page UI-parity release ───────────────
    // 0.4.2 makes the CloudStream stack LOOK like the aniyomi stack (doc
    // cloudstream-v2/05): the resolve sheet renders in the aniyomi
    // ResolverSheet's design (server accordion + quality chips + RobotoFamily),
    // the CS watch screen becomes a real two-mode watch PAGE (pill bar + 16:9
    // player + currently-playing description + episode rows + fullscreen
    // controls with lock/canvas-seekbar/speed sheet), and the player sheets
    // adopt the Qualities-and-Servers / Subtitles / Speed sheet languages.
    // Aniyomi remains byte-untouched — parity via replicated design tokens.
    const val versionCode = 69
    const val versionName = "0.4.4"

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
