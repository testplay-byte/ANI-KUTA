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
    //
    // Task 45 (device round 4): 0.2.66 — the NiceResponse 8KB body-truncation
    // ROOT-CAUSE fix (browse/search 0-results + JsonEOFException), CloudStream
    // source BRIDGE (CS results open the STANDARD details screen — the custom
    // CS details page removed), untrust action in the Trusted Sources list,
    // Cloudflare manual-solve cookie sharing + WebView button on CS errors,
    // full http:/body: network diagnostic logging.
    //
    // Task 46 (device round 5): 0.2.67 — deferred initial plugin load (waits
    // for the first Activity: the MovieBox failed-to-load-after-restart fix),
    // single-checkmark source picker + "Aniyomi" heading + CloudStream
    // selection remembered across restarts, shield trust icon + one-tap untrust
    // (aniyomi parity), details-page enrichment (year + rating via the SAnime
    // channel, season-aware episode names, poster/background/episode-thumbnail
    // URL resolution) + details:/episodes: bridge diagnostics.
    //
    // Task 47 (device round 6 + PLAYBACK): 0.2.68 — search-page memory repaired
    // for real (the selection heal + loaders now await a NON-EMPTY provider
    // list — the two-layer WhileSubscribed chain's empty initial value was
    // resetting the persisted kind on every cold start; the top AniList/
    // Extension tab is now persisted too), search-time year seeds the details
    // page (year+score persist in the ext-extras JSON for cache reopens), and
    // THE PLAYBACK SESSION: bridge getVideoList (loadLinks → Video with
    // CS-semantics partial results, URL dedup, DRM/DASH/torrent filtering,
    // referer/UA header folding, subs + audio tracks), the REAL extractor
    // runtime (35 built-ins across 12 families + the shared jwplayer packed-JS
    // engine + Dood pass_md5 + StreamTape robotlink + MixDrop wurl + StreamSB
    // sources-API + Voe + Dailymotion/PixelDrain/Ok.ru/Streamlare APIs), the
    // real P.A.C.K.E.R. unpacker, the real M3u8Helper (master→variant fan-out),
    // built-in extractor registration at manager init, getHosterList
    // fast-fallback, and per-source video-list timeouts (CS 5s–8min clamp).
    const val versionCode = 68
    const val versionName = "0.2.68"

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
