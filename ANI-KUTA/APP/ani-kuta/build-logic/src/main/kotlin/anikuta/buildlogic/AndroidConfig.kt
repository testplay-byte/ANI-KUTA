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
    // Task 48 (device round 7 — PLAYBACK FIXED + resilience): 0.2.69 — THE
    // loadLinks root cause: the shim's generic newEpisode JSON-quoted every
    // String episode-data handle (upstream special-cases String — "just in
    // case java is wack"; AniKoto's loadLinks got "anikoto|…" with literal
    // quotes → instant "no links"; MovieBox subjectId=%22… → data:null →
    // play-info 400), fixUrl aligned to upstream semantics (http*-prefix
    // pass-through; opaque handles get the mainUrl prefix providers PARSE),
    // bridge getVideoList strips quotes from v0.2.68-cached episodes
    // (defensive heal); search-page INSTANT browse cache (memory + disk
    // snapshot, stale-while-revalidate — a cached feed renders before the
    // plugin manager even finishes loading; refresh failures never blank a
    // shown page); year now in the details header meta row next to the title;
    // the playback 403 RECOVERY LADDER (same-URL retry → pinned-link
    // re-resolve → next mirror — position preserved; deferred switch-error
    // surfacing at 8s instead of the 30s timeout); per-track subtitle headers
    // end-to-end (CS SubtitleFile.headers → Track → Resolver → WatchKey wire
    // format → SubtitleEngine per-request headers); CS DOWNLOADS (bit-62
    // sources mint a rotating-link ResolveContext — expired extractor links
    // self-heal mid-download via the existing ReResolver machinery; download
    // mainId race now toasts instead of silently no-op'ing; subtitle track
    // header parsing uses the comma-smart parser); playback HAPTICS (seek
    // ticks, play/pause clicks, scrub-release confirms — gated by the new
    // player_haptic_feedback preference, default ON).
    // Task 48.1 (device round 8 — CRASH + 428 + watch-page metadata): 0.2.70 —
    // (1) THE CRASH: CloudflareBlockedException now extends IOException (it
    // escaped OkHttp's AsyncCall — which only catches IOException — and hit
    // the uncaught handler on the dispatcher thread: "resolving spinner 20s
    // then the app dies") + a terminal CsInterceptorSafetyNet interceptor on
    // the plugin client so NO interceptor Throwable can ever kill the process
    // (plugin NoClassDefFoundErrors etc. now surface as honest resolver
    // errors); (2) headless Cloudflare solver fast-fail (zero cookies 8s
    // after page-finish = unsolvable interactive challenge — was burning the
    // full 20s per host, ×N hosts serialized) + early-success cookie polling;
    // (3) THE 428: MpvHeaderFields — the canonical comma-GLUING header parser
    // (the old one truncated every User-Agent at "(KHTML," — the cache proxy
    // sent mangled UAs upstream) + mpv-side backslash escaping at ALL
    // http-header-fields boundaries (mpv splits on ',' and honors ONLY \-
    // escapes — verified against mpv m_option.c; the direct-retry path sent
    // the same truncated UA), SubtitleEngine's naive split fixed too;
    // (4) 428/429 join the recovery ladder's looksExpired set (a stale
    // sign/t can never be fixed by a same-URL retry — straight to re-resolve
    // for a fresh sign) + PlayerObserver captures them; (5) watch-page
    // episode metadata UNION fix (buildEpisodeMetadataSerialized iterated the
    // AniList-only map — EMPTY for CS content — so the player page showed no
    // description, no episode thumbnails, no synopses; now extension episodes
    // ∪ metadata with extension-first priority, newline-sanitized + capped);
    // (6) plugin loader self-heal (crash-restart lands in ErrorActivity where
    // CommonActivity never gets an activity → MovieBox errored for the whole
    // session; a late-arriving activity now triggers a reload); (7) the
    // .data.json corruption fix (SAF "w" mode does NOT truncate — 7 download
    // files showed valid JSON + stale tails; now "rwt" + delete-recreate
    // fallback + a per-folder write mutex against interleaved read-modify-
    // writes).
    // v0.2.71 (Task 49, device round 9 — "no extension resolves any videos"):
    // (1) THE DEAD-DISPATCH FIX — loadExtractor normalized the embed URL but
    // NOT the extractor mainUrl (scheme stayed on one side), so dispatch could
    // never match: every embed-based provider (53/80 census plugins) silently
    // resolved 0 links. Both sides now normalized (http/https + www + trailing
    // slash), no-match logs a WARN naming the host, CloudflareBlockedException
    // rethrows out of getSafeUrl (root cause no longer masked), and
    // LoadExtractorDispatchTest locks the contract;
    // (2) ERROR VISIBILITY — VideoResolver swallowed every bridge/provider
    // exception into a generic "No videos available"; failures are now
    // captured per-hoster (isolation kept) and rethrown when the final result
    // is empty, so the resolver sheet shows the REAL reason (CF block, timeout,
    // provider down, hidden-count); timeouts get distinct messages;
    // DetailsViewModel's linked==null silent no-op becomes an honest error;
    // (3) THE CONSOLE LOGGING TOOL — release-available in-app log console
    // (Settings → Developer tools → Console logs) over a new RingLogBuffer in
    // :core:common wired in ALL builds (Logger min-level INFO in release,
    // D-362); the com.lagradost.api.Log facade (plugin logging!) mirrors into
    // the ring via a sink + runCatching (JVM-test-safe); WebViewResolver /
    // CsNetLoggingInterceptor / NiceResponse raw-Log sites mirrored; export =
    // version/device header + ring snapshot + own-process logcat → share sheet
    // via the existing FileProvider;
    // (4) HLS QUALITY SELECTION — the bridge now expands unlabeled M3U8
    // masters into one link per quality variant (M3u8Helper.parseMasterPlaylist
    // extracted pure + tested; fail-open; ≤4 fetches/resolve, ≤8 variants);
    // (5) DASH SURFACING — MpdParser (XXE-hardened): static single-file .mpd
    // manifests become directly playable VIDEO links (separate audio rides the
    // mpv audio-add plumbing); dynamic/multi-segment stay hidden but LOGGED;
    // (6) CF SOLVER HARDENING — 200-HTML challenges now need ≥2 markers
    // (Turnstile-embedding pages false-positived), the solver loads the
    // CHALLENGED PATH (not host root), and the WebView ATTACHES to the live
    // activity (1dp — attached views pass challenge-JS probes detached ones
    // fail; the original app solves attached).
    // v0.2.72 (Task 50, device round 10 — THE SEPARATION: "the aniyomi system
    // is not working either. I think you have mixed them up… Cloud stream
    // handles things a bit differently so we cannot use the exact same system"):
    // (1) THE ANIYOMI REGRESSION (D-364) — D-294's unconditional parent-first
    //     classloader made the host's serialization-2.x classes win at
    //     class-resolution, so extensions bundling 1.x hit NoSuchMethodError AT
    //     RESOLUTION time while browse/search kept working ("some episodes
    //     don't resolve"). Old-kuta's child-first loader restored + hardened
    //     with parent-first exclusion prefixes (kotlin./API/network pinned
    //     host-side) + per-class LinkageError → parent-first retry;
    // (2) THE SEPARATION (D-365) — VideoResolver dispatches via the new
    //     isCloudStreamBridged marker into two pipelines: AniyomiSourcePipeline
    //     (restored: probe memoization, honest hoster-only errors, lazy
    //     resolveVideo, literal-"null" URL filter) and CloudstreamSourcePipeline
    //     (UPSTREAM semantics: no outer timeout — the bridge's budget wraps
    //     ONLY provider.loadLinks and a timeout KEEPS every streamed link
    //     instead of discarding them; Cloudflare blocks keep partials; the
    //     HLS/MPD expansion runs outside the budget);
    // (3) LINK CACHE + PURGE (D-366) — 20-min CloudStream link cache (re-entry/
    //     mirror-switch replay instantly; forceRefresh on the recovery ladder +
    //     dead downloads) and the v0.2.68 stale episode-cache purge (NULL/blank
    //     episode_url rows restored with the SERIES url — could never resolve);
    // (4) HONEST EPISODES (D-367) — comingSoon → real error (not silent
    //     "No episodes found"), TorrentLoadResponse → one honest torrent row,
    //     shared dub data handles → label-neutral rows; +8 census mirror-host
    //     extractors (dood.to/.wf/d000d, vidhidevip/plus, mixdrop.ag,
    //     streamsb.net, waaw.to; 35→43) + provider-name collision WARN;
    // (5) SELECTION INTELLIGENCE + UN-MIXING (D-368) — old-kuta server-grouping
    //     rules (audio tokens never server names, resolution priority, Server
    //     A/B/C, "All Videos" raw fallback), the 5-tier episode-switch
    //     preference (keeps server/audio/quality across episodes), and the
    //     Link-Source sheet sectioned into Aniyomi / CloudStream.
    const val versionCode = 72
    const val versionName = "0.2.72"

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
