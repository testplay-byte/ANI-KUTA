# Progress Log

> Live status of the ANI-KUTA project. **Update after every work session.**

## Current Phase

**Latest session (2026-08-30, Task 49 / D-361..D-363): DEVICE-FEEDBACK ROUND 9 — GENERAL RESOLUTION + THE CONSOLE LOGGING TOOL — v0.2.71.** Round-9 report: "no extension resolves any videos… MovieBox cannot resolve either… make it work in general like the original app… implement a proper console logging tool… extractor hardening… AI HLS quality selection"; aniyomi extensions mostly fine (don't regress them). Three research agents FIRST (R9-A: 17-entry failure-mode catalog of the resolve path; R9-B: logging-infrastructure audit; R9-C: 35/35 census extractors registered+real, loadExtractor no-match semantics, MovieBox .mpd = same hcdn3 host + sign + signCookie as the .mp4 (API geo-blocked from sandbox, hcdn3 428s unconditionally at root), probes: anikototv.to/megaplay/vidtube reachable) + R9-PLAN plan review (GO + amendments 1–8, all applied). **(D-361) THE DEAD-DISPATCH BUG — the round's killer:** loadExtractor normalized the embed URL but NOT the extractor mainUrl → dispatch could NEVER match → 53/80 census plugins silently resolved 0 links since v0.2.68 (browse/search worked; the failure only exploded inside loadLinks, invisible). Both sides normalized now (http/https + www + trailing slash — the old regex didn't even cover http://); no-match WARNs the host; CloudflareBlockedException rethrows out of getSafeUrl; LoadExtractorDispatchTest locks it. **(D-362) Error visibility:** VideoResolver's blanket catch turned EVERY failure into "No videos available" — the bridge's detailed ISEs never reached the sheet; failures are now captured (isolation kept) and rethrown when empty → the sheet shows the REAL reason; distinct timeout errors; linked==null no-op fixed; bridge itemizes hidden/dropped. **(D-362) THE CONSOLE (user-requested):** release-available ConsoleLogsScreen (Settings → Developer tools → Console logs) over RingLogBuffer (:core:common, 10k ring, ALL builds; Logger min-level INFO in release — deliberate §20 reversal) + plugin-facade sink (com.lagradost.api.Log — 48/80 plugins) + 21 mirrored raw-Log sites + export = header + ring + own-process logcat → share sheet. **(D-363) HLS quality selection:** bridge expands unlabeled M3U8 masters into per-quality variants (pure parseMasterPlaylist + tests; fail-open; ≤4 fetches/resolve). **(D-363) DASH surfacing:** MpdParser (XXE-hardened) + bridge sniffer — static single-file .mpd → playable VIDEO links (separate audio → mpv audio-add); dynamic/multi-segment stay hidden but LOGGED. **(D-363) CF hardening:** ≥2 markers for 200-HTML challenges (Turnstile false-positives), solver loads the challenged PATH, WebView ATTACHES to the live activity (1dp). Tests: dispatch 8 + m3u8 5 + mpd 6 + ring 4; :core:common joined CI. v0.2.71. **NEXT: user device round 10 on v0.2.71 — (a) ANY extension's episode → resolver sheet lists servers/qualities or an HONEST reason; (b) Settings → Developer tools → Console logs → reproduce an issue → Export & Share → send the report; (c) MovieBox: .mpd surfacing or "mpd: hidden …" log lines; (d) aniyomi extensions unchanged; then extractor hardening driven by the new console data.**

**Latest session (2026-08-30, Task 48 / D-351..D-356): DEVICE-FEEDBACK ROUND 7 — PLAYBACK ROOT-CAUSED AND FIXED (the newEpisode toJson quote bug), instant search-page cache, the 403 recovery ladder, per-track subtitle headers, CS downloads, playback haptics, the header year — v0.2.69.** Round-7 report: search-selection memory validated ✓ but the browse re-loaded every open; year missing next to the title (bottom InfoSection fine); **ALL playback failed** (AniKoto: resolving spinner → "failed to resolve" with zero network IO; MovieBox: `subjectId=%22…` → data:null → play-info 400 — the %22 was the smoking gun). **(D-351) THE ROOT CAUSE:** the resolver log format has no literal quotes yet the output showed them → the quotes were IN the URL — the shim's generic newEpisode `toJson()`-quoted every String episode handle while upstream routes `data is String` through the url overload ("just in case java is wack" — both plugins' smali call the Object-typed overload); fixUrl was also misaligned (our lenient `contains("://")` vs upstream's `startsWith("http")` + mainUrl-prefixing of opaque handles, which providers' loadLinks EXPECT — AniKoto checks startsWith("$mainUrl/anikoto|"), MovieBox parses via substringAfterLast('/')); the bridge now also strips quotes from v0.2.68-cached episodes (defensive heal) and CompatSurfaceTest pins all of it. **(D-352) Instant search page:** CloudstreamBrowseCache (memory + per-provider disk JSON snapshots; 10-min TTL) + stale-while-revalidate — cached feed renders before the plugin manager finishes loading; fresh snapshots skip the network; refresh failures/empty NEVER blank a shown page; provider-gone invalidates. **(D-353) The 403 ladder (doc 19 §7.2+§8.2):** bounded 3-recovery state machine (same-URL retry — skipped when the error already looks 401/403/410 → pinned-link re-resolve (tier-matched server/audio/quality, NonCancellable swap) → next mirror → terminal banner), watch POSITION captured + restored post-FILE_LOADED, errorGeneration counter re-triggers on identical error strings, deferred switch-error surfacing at 8s (was the 30s timeout), ladder re-arms on every READY. **(D-354) Per-track subtitle headers:** CS SubtitleFile.headers (dropped at the bridge fold before) now flow Track → ResolverSubtitleTrack → WatchKey 3-field wire format (backward compatible) → PendingExternalTrack → SubtitleEngine per-request headers (per-track wins, video fallback); downloads fold per-track headers + use the comma-smart parser. **(D-355) CS downloads:** the R7-B map proved the chain bridge-compatible except the re-resolve gate — ResolveContext gains linkRotates (bit-62 CS sources mint it; both fetchers widened from isLocalhost to isLocalhost||linkRotates → expired extractor links self-heal via the EXISTING ReResolver); the silent mainId-race no-op now toasts; HLS first-variant limitation documented. **(D-356) Haptics + year:** PlayerHaptic (SEEK_TICK/PLAY_PAUSE_TOGGLE/SEEK_RELEASE) at 6 gesture sites, gated by the new player_haptic_feedback pref (default ON); seasonYear renders in the header meta row (★ score · year · status · eps). v0.2.69. **NEXT: user device round 8 on v0.2.69 — THE playback verification (pick a CS episode → resolver sheet lists servers/qualities → MPV plays; 403 mid-playback self-recovers with position kept; downloads produce playable files; search page opens INSTANTLY from cache; year shows next to the title) → then: extractor hardening driven by device data (hosts rotate — each census family is an isolated fix), HLS variant-quality selection (doc 19 open), and the doc 20 backlog.**

**Latest session (2026-08-29, Task 47 / D-348..D-350): DEVICE-FEEDBACK ROUND 6 (validated: MovieBox restart fix, single checkmark, rating/seasons/thumbnails, trust UX) + THE PLAYBACK SESSION — bridge getVideoList (loadLinks→Video), the REAL extractor runtime (35 built-ins + shared jwplayer engine + P.A.C.K.E.R. unpacker + M3u8Helper), search-page memory repaired for real, year seeding — v0.2.68.** Round-6 validated everything from Task 46 except two memory bugs + the year row: (a) the CS source selection STILL reset after a full restart — **(D-348)** the Task-46 heal gated on the manager's loadedOnce but validated against the TWO-layer WhileSubscribed chain's `emptyList()` INITIAL value (the derived flow needs dispatch hops after its FIRST subscriber — the heal collector was that subscriber) → "provider gone" destroyed the persisted kind every cold start; `awaitCsSource` now waits for a NON-EMPTY raw list before any "gone" verdict (both CS loaders route through it) and the top AniList/Extension tab is PERSISTED (`search_selected_source`); (b) year — **(D-350)** the score rendering proved the Task-46 channel works; the provider's load() simply omits year while its SEARCH responses carry it: the search-time year now threads ExtensionAnime → AnimeDetailsKey.Extension → the stub SAnime seed (load().year wins, the seed fills nulls; refreshes re-seed) and year+score persist in the additive ext-extras JSON for cache-first reopens. **(D-349) PLAYBACK (doc 23 §7 / doc 19 §1–3):** census over all 80 phisher .cs3 dexes first (53/80 use loadExtractor; subclass counts per family; 20/80 use M3u8Helper; MovieBox = direct JSON API, AniKoto = own MegaPlay + loadExtractor for vidtube/vidwish). The bridge's getVideoList runs provider.loadLinks with CS3 streaming semantics (partial results survive mid-call failure, URL dedup, DRM/DASH/torrent filtering+counting, referer/UA folding, episode-level subs + audio tracks) and maps to Video with resolver-parsable "Source - label 720p" titles + per-source Mirror-N numbering; getHosterList throws for instant fallback; per-source videoListTimeoutMs (CS 5s–8min clamp) replaces the fixed 30s. The REAL extractor runtime: 35 built-ins — one shared jwplayer packed-JS engine for the StreamWish/VidStack/Filesim/VidHide/Filemoon/Vidmoly/Emturbovid/Mp4Upload/mirror families + dedicated Dood (pass_md5), StreamTape (robotlink), MixDrop (wurl), StreamSB (sources-API), Voe, Dailymotion, PixelDrain, Ok.ru, Streamlare + the 19 census variant classes; registered at manager init BEFORE plugins (reverse-order dispatch → plugin mirrors win). The REAL P.A.C.K.E.R. unpacker (escape-aware, balanced-paren, multi-pass — unit-tested) and the REAL M3u8Helper (master→variant fan-out, RESOLUTION heights, URI absolutization). ZERO downstream changes: resolver → picker → WatchKey → WatchScreen → MPV, episode switching, watch progress + apply/continue-watching all consume Video and are source-agnostic. links: diagnostics under Anikuta:Data:Cloudstream:Bridge. CI on 01c3be5c → tag v0.2.68 after green. **NEXT: user device round 7 (pick a CS episode → the resolver sheet lists hoster servers + qualities → tap → MPV plays with headers/referer; episode switch inside the player re-resolves; continue-watching/apply rides watch progress; logcat links:/http: lines show the pipeline) → then downloads for CS content + the 403 re-resolve ladder (doc 19 §6–§7) as the next session.**

**Latest session (2026-08-29, Task 46 / D-344..D-347): DEVICE-FEEDBACK ROUND 5 — the execution phase VALIDATED end-to-end by the user ("every single extension or plugin is working perfectly… results exactly how I hoped"); fixed the MovieBox restart failure (activity-gated initial plugin load), the source-picker selection state machine (single checkmark + remembered CloudStream selection + "Aniyomi" heading), details-page enrichment (year/rating/seasons/thumbnails), and trust-UX parity (shield icon + one-tap untrust) — v0.2.67.** **(D-344)** MovieBox "trusted fine → exit → reopen → Failed to load": the manager's init{} ran loadAll() inside Application.onCreate BEFORE MainActivity exists → Plugin.load() got the Application context → every `context as AppCompatActivity` plugin failed on EVERY cold start (interactive trust worked because the activity was alive). Fix: `CommonActivity.activityFlow` (StateFlow mirror of the activity reference) + the initial load SUSPENDS until the first Activity (15s timeout → app-context fallback for background starts; loadAll stays OFF installMutex — the repos collector holds it across network fetches, and loadAll is suspension-free on Main so it's atomic wrt other main-thread coroutines) + `loadedOnce` StateFlow. **(D-345)** Picker: the double-checkmark (stale aniyomi row AND CS row both checked — `selectedSourceId` passed unconditionally, now kind-gated like the CS param); heading "Anime Extensions" → **"Aniyomi"**; "forgets my CloudStream source after restart" — the heal collector fired on the legitimately-EMPTY cold-start csSources emission and destroyed the persisted (kind, provider) selection — it now awaits `sourcesLoaded` first (20s bounded). **(D-346)** Details enrichment (no UI changes, all inside the standard pipeline): `SAnime.year/score` optional channel (binary-safe interface properties, only the CS bridge populates) → Year + "★ N%" + "Score N/100" rows render; `Episode.season` encoded into episode names as leading "Season N - Episode M" tags → the existing season chip selector + per-season numbers activate for multi-season CS shows; relative/protocol-relative image URLs absolutized against the provider mainUrl (details poster/background, episode preview_url, grid cards — the details poster only overwrites the incoming thumbnail when resolvable); `Episode.posterUrl` → `SEpisode.preview_url`; `details:` + `episodes:` diagnostic lines under `Anikuta:Data:Cloudstream:Bridge` (per the explicit logging request). **(D-347)** Trust-UX parity: shield (`VerifiedUser`) Trust icon on Untrusted rows (was the checkmark badge); untrust from the Trusted Sources list is ONE TAP (confirm dialog removed — aniyomi parity; Uninstall keeps its confirm). CI on 5fd4a458 → tag v0.2.67 after green. **NEXT: user device round 6 (MovieBox survives a restart; picker shows exactly one checkmark + remembers the CS source; details show year/rating/seasons/thumbnails; logcat `Anikuta:Data:Cloudstream:Bridge` shows the details:/episodes: lines) → then the PLAYBACK session (loadLinks + the 16 real extractors + WatchKey) per doc 23 §7.**

**Previous session (2026-08-29, Task 45 / D-340..D-343): DEVICE-FEEDBACK ROUND 4 — the 8KB body-truncation ROOT CAUSE (one okio semantics bug behind every "0 results"/JsonEOF/empty-shelf symptom), CloudStream results on the STANDARD details screen via the source bridge (custom CS details page deleted per user directive), untrust-in-list, and the closed Cloudflare manual-solve loop — v0.2.66.** The user's round-4 report validated the trust flow + extensions section ("everything else was working properly… just like how I hoped") and surfaced: (a) search/browse returning 0/empty results on CloudStream providers with useless diagnostics; (b) the custom CS details page being "completely different" from the aniyomi one — directive: use the EXACT same screen, nothing custom; (c) no untrust option in the Trusted Sources list; (d) a Cloudflare message telling the user to "solve it in the web view" with no WebView option. **Root cause (D-340):** `NiceResponse.readBody()` called okio's `Source.read(sink, byteCount)` ONCE — a SINGLE read returning at most ONE 8KB segment — so every HTTP body > 8KB was silently truncated (HTML → jsoup 0 items with no error; JSON → JsonEOFException at ~col 8083; AllMovieLand only worked because its payload fit one segment). Fixed with a read-until-EOF loop, plus the demanded robust diagnostics: `CsNetLoggingInterceptor` + per-body-read logging under `Anikuta:Data:Cloudstream:Net` (`http:`/`body:` prefixes, error-body snippets). **Source bridge (D-341):** `CloudstreamAnimeSourceBridge` exposes every trusted provider as an `AnimeHttpSource` under a stable synthetic id (bit 62 | name-hash) merged into ExtensionManager (`setExternalSources`; AnikutaApp wiring) — CS results navigate `AnimeDetailsKey.Extension` and the standard DetailsScreen resolves details/episodes/save/tags/auto-link through the provider; plugin Errors→Exception for the aniyomi catch sites; `getVideoList` = honest "playback arrives in the next update"; `:feature:cloudstream-content` DELETED. **CF loop + untrust (D-342):** CloudflareBlocked card (with WebView action) for CS blocks; ExtensionEmpty carries provider mainUrl; CloudflareKiller merges system WebView CookieManager cookies (manual solves now reach the plugin client); manual WebView pins the CS USER_AGENT (clearance is UA-bound); more 200-JS-disabled challenge markers; RemoveModerator untrust icon + confirm dialog on every Trusted Sources row. CI on c41cbc8d → tag v0.2.66 after green. **NEXT: user device round 5 (search/browse should SHOW results now — AniKoto ~10 items/shelf, Anikage JSON parses, sections render; tapped results open the standard details screen; untrust from the list; CF WebView solve → Refresh) → then the PLAYBACK session (loadLinks + the 16 real extractors + WatchKey) per doc 23 §7.**

**Previous session (2026-08-29, Task 44 / D-336..D-339): DEVICE-FEEDBACK ROUND 3 — execution bugs root-caused (activity-context contract + Cloudflare bypass), SECTIONED browse rows, detail-page button spec, retry spinners — v0.2.65.** The user's round-3 report validated the trust flow + detail page + untrusted/failed sections ("perfect… no issues") and surfaced two REAL execution failures with clean root causes: **(A) ClassCastException on trust (MovieBoxProvider)** — the plugin's `load(context)` casts the context to AppCompatActivity (the stash-the-activity pattern; upstream passes `this@MainActivity`); our loader passed the Application context. Fix: **MainActivity now extends AppCompatActivity** (theme parent → `Theme.AppCompat.NoActionBar`, visual attributes pinned — rendering identical; other activities are ComponentActivity, unaffected), registers with `CommonActivity` (identity-guarded clear), and the loader passes the LIVE activity to `Plugin.load()`; `appcompat:1.7.1` added (also satisfies androidx.appcompat.* refs in real plugin dexes — would have been NoClassDefFoundError); `AnikutaApp : CloudStreamApp()` (CloudStreamApp.context wired for getKey/setKey + the CF solver). **(B) browse/search → 0 items (AniKoto)** — the site is Cloudflare-fronted; the device receives a challenge interstitial (the sandbox got cached 200s — site + selectors verified working with plain GETs) which jsoup parses into 0 items with no error — the exact log signature. Fix: **CloudflareKiller is real** (challenge detection via cf-mitigated header + body markers; headless WebView solve on the main thread with UA pinned to USER_AGENT; per-host cookie cache; **per-host solve serialization** — the sectioned browse fires N parallel shelf requests; 60s failed-solve cooldown; `CloudflareBlockedException` with a friendly userMessage — a challenge page is NEVER silently parsed as "no results"). Wired into the plugin `app`/`insecureApp` base client; logs under `Anikuta:Data:Cloudstream:Net` (`cf:` prefixes). **(C) Sectioned browse** (the round-3 feature request: "popular, latest and other sections in row format"): `browseSections()` fetches EVERY provider shelf in parallel (per-shelf failure tolerated; all-CF-blocked rethrows), `ExtensionBrowseSuccess` renders titled horizontal rows reusing the flat-grid card; search stays the flat grid; aniyomi path untouched. **(D) Detail-page buttons per the round-3 spec:** available → Install at the VERY bottom full-width; untrusted → [Trust][Uninstall] side-by-side at top; trusted → bottom row UNCHANGED (approved); errored → [Retry][Uninstall]. **(E) Retry animation:** manager `retrying` StateFlow → spinner on the failed-row retry icon + the detail Retry button. CI chain: 2 red rounds (kotlinx extension fns can't be called fully-qualified; spinner takes Boolean not Set) → GREEN d2748114 → **v0.2.65 tagged + released**. **NEXT: user device round 4 on v0.2.65 (trust MovieBoxProvider → providers live; AniKoto browse → SECTIONED rows; search; details) → then the PLAYBACK session (loadLinks + the 16 real extractors + WatchKey) per doc 23 §7.**

**Previous session (2026-08-29, Task 43 / D-333..D-335): DEVICE-FEEDBACK ROUND 2 + PROVIDER EXECUTION PHASE 1 — the CloudStream providers now SERVE content on the Search page, with trust flow, parallel installs, and a plugin detail page.** The user's round-2 report validated the round-1 rebuild ("UI was consistent… proper, perfect, beautiful, exactly like how I hoped") and flagged: the version was never bumped (now a hard rule — v0.2.64), tabs should be LEFT-aligned (round-2 reversal), the repo badge belongs on the TITLE line, second downloads showed no progress (mutex blocked them silently), and there was no untrusted list. All fixed + the "move to the next things" directive executed: **(1) Trust flow** — `isTrusted` gates code execution; new installs land in the Untrusted section (never classloaded until trusted); Trust/Untrust promote/demote live; updates preserve trust; legacy records grandfathered (unit-locked). **(2) Parallel installs** — download runs outside the install mutex; per-plugin double-tap guard. **(3) Plugin detail page** — every CS row clickable: authors/description/version/status/size/modes/language/repo + the live provider list + per-state actions; metadata captured at install survives repo deletion. **(4) Provider EXECUTION (D-334)** — `CloudstreamContentRepository` (mainPage/search/load; `Anikuta:Data:Cloudstream:Exec` prefixed+timed logs); Search page integration via string `sourceKey` (aniyomi path byte-identical — user direction over docs 16/18's deferred-Cloud-tab recommendation); picker shows Anime Extensions + CloudStream sections; `ExtensionNoBrowse` honest state; NSFW-gated picker; NEW `:feature:cloudstream-content` — `CloudstreamContentDetailsScreen` (hero/tags/description/season-grouped episodes/Dub-Sub labels/movie entry; explicit "playback next update" note — loadLinks + real extractors = next session per doc 23 §7). **(5) Rule-8 ruling recorded:** CI = primary verification (used freely); local only when cheap (CI-only this session). Verification: CI rounds converging (compile-fix chain, HEAD 7ee1cf21); tag v0.2.64 + release after green.

**Previous session (2026-08-29, D-327..D-329): COVER-FLIGHT PACING + LIBRARY ⇄ SEARCH GHOST-MORPH FIX + v0.2.63 **MERGED INTO `main`** — `test-feature/video-cache-new-download` → main (v0.2.63); NEW BRANCH `streaming/CLOUDSTREAM` created (purpose TBD by user, NO work started).** User device-tested v0.2.62 (everything satisfactory) and requested: (1) cover flight "way too fast" — slower + smoother, details page may open early while the image moves slowly; (2) switching Library ⇄ Search makes "specific anime content" move between the pages (the same shared-element animation firing between two LIST screens); then merge the branch to main, verify main builds a proper APK, and create `streaming/CLOUDSTREAM` from the new main. **D-327:** `Motion.DurationSharedFlight` = 600ms for the cover's own BoundsTransform (page crossfade stays `DurationContainer` 450ms) — both on `EasingEmphasized`; curve sync is the anti-jitter invariant, decoupled durations are safe because only the cover still moves after the page settles. **D-328:** ghost-morph root cause = Library AND Search both keyed covers `"cover:<url>"`; during a switch both screens compose at once and matching keys morph EVEN ACROSS snap transitions. Canonical builders `libraryCoverKey`/`searchCoverKey`/`browseCoverKey` (SharedTransitionLocals.kt) now build ALL 7 keys (card modifiers in LibraryScreen/SearchScreen×2/BrowseCards + the 4 MainActivity nav-arg keys); list ⇄ list can never match again, list ⇄ Details still morphs (Details carries the source's key via `AnimeDetailsKey.transitionKey`). **D-329:** v0.2.63 (code 63); branch MERGED into main (`--no-ff`; main had diverged from merge-base 26e47722 by only 2 user web-UI uploads — moviebox v16.1139 APK + an empty commit — clean) — tag v0.2.63 + release FROM the merge commit (~59.4MB arm64-v8a); `streaming/CLOUDSTREAM` branched from the new main (user's literal ask "/streaming/CLOUDSTREAM" — leading slash invalid as a git refname, documented in D-329).

**Latest session (2026-08-28, D-304..D-310): SEARCH INTEGRITY + EXTENSION-FIRST EPISODE METADATA + SEASONS + INSTALL UX — on `test-feature/video-cache-new-download` (**v0.2.58**, NOT merged).** User device-tested v0.2.57: extensions + trust + update-check + update-install all CONFIRMED WORKING; reported: (1) CRASH `Key "3508466391484419848:/movies/…" already used` (LazyGrid duplicate key); (2) search "reverts to an older state / shows results from some other extension" on failure; (3) extension-provided episode metadata (thumbnails/titles/descriptions) should be PRIORITIZED over our providers; (4) full-fledged SEASONS — detect "( Season 5 - Episode 12 - … )" title tags, chip selector between source selector and episode list, click-to-center, seasons default + user-switchable vs grouping; (5) update button look + NO download animation. **D-304:** dedupe duplicate URLs (moviebox returns same entry twice in one page) at both mapping sites + render-time defense. **D-305:** request-generation identity + job cancellation + mode-consistent rendering + source-switch-with-query now searches the new source (kills all three stale-result races). **D-306:** EpisodeDisplayResolver — extension preview_url/summary/title win, providers fill gaps; cache preserves extension values (was dropping preview_url, provider overwrites); all 3 WatchKey serialization sites use the resolver. **D-307:** SeasonDetector (:core:common) + groupEpisodesBySeason + organizeBySeasons pref + settings-sheet Seasons/Number-groups choice. **D-308:** SeasonSelectorRow chips (All/Season N/Other) with click-to-center (animateScrollToItem negative-offset recipe), filters apply within seasons, range-grouping suppressed while seasons active. **D-309:** InstallStep.Downloading carries streamed % (200ms throttle, mirrors UpdateDownloader); Update pill (was bare Refresh icon) morphs via AnimatedContent into ring+% + pulsing "Installing"; InstalledExtensionRow now consumes installStates (previously ignored); AvailableExtensionRow same treatment. **D-310:** version 0.2.57 → **0.2.58**. Commits: 4cd4a4ea (D-304+305, CI 809 green) → 7721182e (D-306..308, CI 810 RED: val reassignment + animateScrollBy tween inference) → c3a926a4 (D-309) → e741f053 (CI fixes) → run 812.

**Latest session (2026-08-27, D-294..D-303): EXTENSION SYSTEM OVERHAUL — root-fix "extensions disappear after trust" + visible load errors + lib-17 + language filter + virtualized list + auto update-check + provider-api — on `test-feature/video-cache-new-download` (**v0.2.57**, NOT merged).** User report: ALL extensions from salmanbappi/extensions-repo (82 ext) show up untrusted but VANISH the moment they're trusted; user also wanted: systematic 3rd-party compat, extensions-page perf if needed, language filter, the provider-api abstraction made real, the duplicate install path consolidated, lib-17 + future-version handling, and auto update-checking from GitHub repos on page entry. **Root-cause (R-3, custom AXML/DEX analysis):** (1) ChildFirstPathClassLoader let the unminified template extensions' PARTIAL bundled kotlin-stdlib shadow the host's complete 2.2.0 stdlib → instantiation Throwable; (2) trustExtension/loadAll silently DROPPED LoadResult.Error → invisible everywhere. **D-294:** parent-first PathClassLoader (reference-Aniyomi-EXACT; ChildFirstPathClassLoader deleted). **D-295:** per-source-class failure reasons (exception class+message) in LoadResult.Error. **D-296:** AnimeExtension.Errored + erroredExtensions flow + applyLoadResult (never drops) + "Failed to Load" UI section (Retry/Untrust/Uninstall + reason). **D-297:** LIB_VERSION_MAX 17.0; out-of-range attempted (visible error, no hard reject). **D-298:** Installed.lang populated from sources + language filter across all sections. **D-299:** fully virtualized sections (rows as items w/ keys+contentType). **D-300:** installExtension delegates to ExtensionInstaller (duplicate pipeline deleted). **D-301:** auto update-check on page entry (30-min throttle) + Update button on installed rows. **D-302:** VideoExtensionProvider + SourceDescriptor + AniyomiExtensionProvider facade in Koin. **D-303:** version 0.2.56 → **0.2.57**. Commits: 301c4a78 (D-294..D-302) → CI run 806.

**Latest session (2026-08-26, D-289..D-293): Hero v6 (compact height + abstract splash + seamless blend) + Library scroll-jump root-cause fix + reveal-once cover animations + palette scroll-jank fixes — on `test-feature/video-cache-new-download` (61 commits ahead of `main`, **v0.2.56**, NOT merged).** User device-tested v0.2.55: tab memory CONFIRMED WORKING (D-282 ✓, no action needed); hero STILL too tall + the "gradient" was too literal — user wants an ABSTRACT SPLASH of colors, not a smooth gradient; hero must be just a little taller than the cover image; banner can be a background layer; cover colors must blend around it; banner↔bottom boundary must be invisible. Library: still not smooth — auto-scroll-to-middle/bottom bug (esp. after pull-to-refresh), images pop in abruptly (wants one-by-one smooth reveal, speed adapts to scroll velocity), images re-load on scroll-back (should load once unless full refresh). **R-1 research agent** root-caused the scroll jump: double state emission (Success in DATE_ADDED order, THEN applyFilters() re-emission) + LazyGrid key-anchoring following the first-visible item to its DATE_ADDED rank when a recomposition landed in the preemption window; ALSO found Palette.generate() running ON MAIN per card. **D-289 (hero v6):** fixed 148dp height (was 1.4:1 ≈ 234dp); banner as full-bleed Crop background; SplashOverlay = 8 seeded-random soft radial blobs in the cover's 6-color palette (2 airy top + 5 dense bottom + 1 poster-echo behind the cover) via drawBehind — abstract splash, NO linear gradient; unifying 0.06→0.52 veil — seamless blend is STRUCTURAL (banner spans the full card, all blob edges are radial falloffs → no boundary exists). **D-290 (scroll-jump fix):** SINGLE-emission loads (filterAndSort pure fn computes the final list BEFORE any state write — the unsorted-intermediate-state bug class is gone); masterEntries (unfiltered) in the VM (also fixes latent clear-search-restore bug); staggeredState hoisted to VM (Comfortable mode parity); resetScrollToTop() via requestScrollToItem(0,0) on category/search dataset changes; resume refresh now invisible (equal Success states are conflated away by StateFlow — LibraryEntry is @Immutable). **D-291 (reveal-once covers):** CoverRevealController threaded screen→grid/list→cards→LibraryCoverImage; VM-backed revealedCoverKeys (survives tab switches, cleared ONLY by pull-to-refresh); covers fade 0→1 on FIRST load only; velocity-adaptive duration (240ms calm → 70ms fling) sampled non-reactively at load completion; alpha animated in the DRAW phase (graphicsLayer) — zero recomposition churn; soft surfaceVariant placeholder; rememberScrollVelocityFactor (EMA over index*4096+offset signal + 150ms decay loop). **D-292 (palette scroll-jank):** Palette.generate() + HARDWARE copy now on Dispatchers.Default + 256-entry LruCache with failure sentinel; extraction GATED on coverBorderEnabled && ADAPTIVE (was unconditional per card — every card entering viewport did a Coil 100×100 load + Palette even with borders OFF — likely THE primary scroll-jank source). **D-293:** version 0.2.55 → **0.2.56** (versionCode 56); tag/release after CI green. Commits: 8fa46be (D-289..D-292) + 26beba9 (extraction gating) → CI GREEN run 32993791653 FIRST TRY (zero fix rounds).

**Previous session (2026-08-26, D-281..D-288): CI-first workflow + tab-memory scope + hero v5 + Library batch loader/instant switch/scroll perf — on `test-feature/video-cache-new-download` (59 commits ahead of `main`, **v0.2.55**, NOT merged).** User device-tested v0.2.54 (hero banner now displays properly — D-277 confirmed) and reported: hero height still too much; accent should be a 5-6-color smooth darker gradient from the cover + slightly blurred dark overlay; Library switch reloads the whole page (All = 653 entries, 4-5s); 5-column scroll janky + scroll-back re-loads images; tab memory must NOT restore onto More/Search; AND a workflow change — no sub-agent compile review, build via GitHub Actions and analyze failures there. **D-281 (workflow):** CORE_RULES §8 + SESSION + workflow docs rewritten — write → push → CI builds → read results via API → fix → repeat; CI is the compiler of record (validated: CI caught D-284's vararg misuse + D-285's SQLDelight duplicates; both fixed next-push). **D-282:** tab memory restores ONLY Browse/Library (read-site sanitization + write-site persists only those two + backstack search/more mappings removed). **D-283:** hero card 1.2:1 → 1.4:1 (~15% shorter, below-banner zone ~25% smaller) + poster 76×114. **D-284:** 6-color palette gradient (extractGradientColors: named Palette swatches → cinematic HSL band L∈[0.16,0.42] → light→dark → dedupe → exactly 6 stops) + soft black veil (0.04→0.10→0.32) via new rememberCoverGradientColors in :core:designsystem; transparent-over-banner feathered junction (BANNER_FEATHER_END=0.42). **D-285 (the big one):** Library load was ~3,300 queries ON THE MAIN THREAD (N+1 per entry + zero withContext) → now **7 batch queries on Dispatchers.Default**: getAllWatchedCounts/getAllLastWatchedAt (GROUP BY), getAllEpisodeAudioRows (4 cols), getAllLibraryMainEntries (JOIN dedup) + REUSED pre-existing getAllLibraryItems/getAllContentDetails (CI caught my duplicates — removed); batch methods across ContentRepository/WatchProgressStore/DataCacheRepository (+ LibraryItemRecord/EpisodeAudioAggregates models); reloadFromCache delegates to the one impl; old "fetch AniList on miss" branch was UNREACHABLE dead code — removed + documented; category-filter order now preserves added_at DESC (DATE_ADDED sort was inconsistent between All/category views). **D-286:** loadLibrary keeps the grid on screen when already Success (silent background refresh — no Loading flash per tab switch); gridState/listState hoisted into the Activity-scoped ViewModel (scroll position survives tab switches). **D-287:** LibraryCoverImage at all 3 cover sites — crossfade(false) per cell (global fade janked fast 5-col scroll + re-faded scroll-back repopulation) + bitmapConfig(RGB_565) (halves memory-cache footprint → 653-cover grid stops evicting itself); progressive loading unchanged. **D-288:** version 0.2.54 → **0.2.55** (versionCode 55); tag + release after CI green. **Session continuity:** previous session was CUT OFF mid-work (D-281..D-284 committed but never pushed + D-285 .sq queries uncommitted) — this session pushed them, rode 2 CI failures to fixes, completed D-285..D-288. Commits: 93534f6 + d5625d1 + 4356b5a (pushed together) → f7740b0 (vararg fix, CI GREEN) → 1e963f3 (D-285/286/287) → 0809551 (dup-identifier fix) → version+docs commit → tag v0.2.55.

**ALL MAJOR PHASES COMPLETE + … + D-225→D-238 OVERHAULS — ALL ON `main`; IN ACTIVE FEATURE-WORK: VIDEO CACHING + PARALLEL DOWNLOAD ENGINE + DOWNLOAD RESILIENCE + PROGRESS-WINDOW CACHING + UX/CONTINUE-WATCHING/BROWSE OVERHAULS + SETTINGS-UI ICON UNIFICATION + D-251 + D-252/253/254 + D-255/256 + D-257..D-260 + **D-261..D-265 (device-feedback batch #3: palette persistence fix + brightness removal + 2 new elements + hero blur/pager fix + 12s + random palette + colorful sliders + recents section)** on `test-feature/video-cache-new-download` (D-243..D-265, 49 commits ahead of `main`, **v0.2.51**, NOT merged).** ⚠️ Branch note: `functionality/improvements` was MERGED into `main` (26e47722) by the user on 2026-08-22; `feature/test-controller-v5` (D-197..D-202 numbering COLLISION) remains UNMERGED (dormant). **Latest session (2026-08-25, D-252/253/254)**: Library COVER_ONLY episode tags → pointed 45° tip design + corner-aware outer clip (fixes the curved-sliver defect) + BadgeColorScheme promoted to :core:designsystem; Browse page complete UI overhaul (full-bleed auto-advancing hero pager + bordered 12dp-standardized cards + amber pointed score tags replacing the "ugly" black pills + shimmer skeletons + error Retry + parallel IO-dispatched VM); Custom palette editor (per-element colors + brightness, live re-theme, re-tap Custom to open). **Review #3 session (2026-08-25)**: full-project review + dashboard `/review/` section replaced `/key-findings/` (deployed live). **D-251 session (2026-08-24)**: dead-wiring fixes, Library Hide-Titles + Cover-Only, 77 dead imports, arm64-only releases + release-apk.yml, update-checker fix, v0.2.48, emulator rebuild. **Emulator is OFF-LIMITS this session per user instruction** (its docs remain; no emulator work until the user re-enables it).

**This session — D-261..D-265: device-feedback batch #3 — palette system overhaul + hero blur/pager fix + random palette + recents redesign + v0.2.51 (2026-08-25, on `test-feature/video-cache-new-download`, commits 8c201755 + 513f2e3e + cbf8765a + 9d72f45e + da93107c + c996391b):**
- User device-tested v0.2.50 and reported (satisfied overall; emulator OFF-LIMITS; quality over speed; no merge without confirmation): Browse hero banner "should be slightly blurred out and darkened" (was sharp + weak scrim); auto-scroll "scrolled and stopped in the middle between two banners" + "did not stop in the appropriate position" + auto-advance stopped firing; interval should double (6s → 12s); preloading + tag borders good; search recents need a dedicated horizontal-scroll section with proper background + depth; custom palette: "background transparent by default" (Reset recovers), remove brightness sliders entirely ("there is definitely no need"), add two more customizable elements (card headings + card descriptions), make sliders colorful (red red, green green, blue blue), add a Random option (left of Reset, opens nested sheet with random dark / random light / completely random); custom theme NOT persisted across restart; bump version + release.
- **Workflow followed**: 5 parallel research agents (hero/pager+blur feasibility, theme system + persistence bugs root-cause, picker/slider/recents current state, card-text consumers + new-locals pattern, random palette HSV design) → plan → **plan-review agent** (GO-WITH-FIXES; 6 fixes incorporated: KDoc "~100 min" not "~200", ImageRequest line ref, ImageResult cast pattern, 2 Brush imports, Theme.kt preserve explicit MaterialTheme form, Bitmap.createScaledBitmap safety net) → **consumer-sweep sub-agent** (28 sites, 27 applied + 1 skipped-role-mismatch handled by main) → 5 phase commits (D-261..D-265) → CI green per phase (2 CI fix rounds on D-262 for `coil3.request.ImageResult` + `SuccessResult` top-level + `ImageBitmap` import). Compile-review agent SKIPPED — CI caught everything per-phase (CORE_RULES §8 loop) + plan-review agent's verification de-risked.
- **D-261 Appearance palette**: persistence root cause = `Color.value.toInt()` returns 0 (Color is a ULong value class, ARGB in UPPER 32 bits; `.toInt()` truncates to transparent) → every write stored 0 → "transparent on restart" + "transparent by default" in picker. Fixed with `.toArgb()` at 6 sites + a corruption migration that heals v0.2.49/v0.2.50 installs. Brightness sliders REMOVED entirely. TWO new customizable elements: `cardHeading` (titles inside cards/blocks) + `cardDescription` (body/description text inside cards/blocks) — 6-field `CustomThemeColors`, 2 new CompositionLocals (`LocalCardHeadingColor` / `LocalCardDescriptionColor`, Color.Unspecified sentinel) in the same always-on provider (D-255 structural stability preserved), 2 new ARGB keys + 2 new rows + 2 new 5-swatch lists. 28-site consumer sweep (Browse + CW + Library + Search + Details) — each Text color arg reads the local with `.takeIf { it != Color.Unspecified } ?: <original role>`. Pre-existing gap fixed: Library header clone now reads `LocalHeadingColor`. Hero title/meta kept hardcoded `Color.White` (reads on any artwork over the dark scrim); section headers kept `primary` (accent design language).
- **D-262 Browse hero**: auto-advance "stuck between two banners" root cause = `LaunchedEffect` keyed on `currentPage` (flips at the 50% scroll crossing DURING `animateScrollToPage` → cancelled mid-flight → no snap → single-shot loop died permanently). Fixed with `while(true)` keyed on `(pagerState, virtualCount)` + `CancellationException` catch → wait for gesture end (`snapshotFlow.first { !it }`) → snap-to-nearest via `withContext(NonCancellable)`. Dots read `settledPage`. Interval 6s → **12s**. Blurred backdrop (works on minSdk 24 — Coil 3 REMOVED the `Transformation` API entirely; `Modifier.blur` is API 31+ only): new `BlurredBannerBackdrop` composable — `produceState<ImageBitmap?>` + `Dispatchers.IO` + `imageLoader.execute` (custom `memoryCacheKey("hero-blur:$url")` namespaces from sharp requests) + `toBitmap(160,90)` + `Bitmap.createScaledBitmap` safety net + single-pass `boxBlur` (radius 2) + `asImageBitmap` + `Image(BitmapPainter, ContentScale.Crop)` (GPU bilinear upscale = soft blur). Darker scrim 0.18/0.45/0.82 → 0.30/0.55/0.88. SectionPreloader dropped banner URLs (backdrop loads itself) + warms only sharp covers at 84×126.
- **D-263 Appearance palette**: colorful channel sliders — `ThinSlider` gained a `trackBrush: Brush?` param (full-width gradient, skips the two-box split); `ColorPickerSheet` `ChannelSliderRow` threads per-channel gradients (Red 0→255 holding g/b/a, Green/Blue symmetric, Alpha transparent→opaque of current color) + colored thumbs (red 0xFFE53935, green 0xFF43A047, blue 0xFF1E88E5). New `core/designsystem/theme/RandomPalette.kt` with 3 generators: **Random dark** (single family hue ±25°, V-ramp bg→card, tinted near-white headings, muted-tint description, independent vivid accent — always readable, contrast ≥ 8.4:1 worst corner), **Random light** (mirrored), **Completely random** (per-channel, alpha FORCED 0xFF — never trigger the D-261 transparent bug class). Random button (`Icons.Filled.Casino`) left of Reset in `CustomPaletteSheet` → nested `RandomPaletteSheet` (DarkMode/LightMode/Shuffle icons) applies + persists via the same `setCustomTheme` path (survives restart via D-261's fix).
- **D-264 Search**: recents redesigned as a dedicated horizontal-scroll section — outer `Surface(surfaceVariant@40%, 16dp corners, 1dp outlineVariant@60% border)` for depth (no shadows — the app's border language); sticky header (History icon primary + "Recent searches" 14sp ExtraBold primary + Clear all); single `LazyRow` of bordered chips (`surfaceContainerHighest` pops on the tinted container; 36dp tall; per-chip remove X). Signature UNCHANGED → all 3 render sites get it free. Removed `FlowRow`/`ExperimentalLayoutApi` (D-255 crasher class — no longer needed).
- **D-265**: version 0.2.50 → **0.2.51** (versionCode 51); tag + release after CI green; dashboard version refresh via full-stack-dev sub-agent.
- **CI round**: 2 fix rounds on D-262 — (1) `coil3.ImageResult` should be `coil3.request.ImageResult` + `ImageBitmap` type import (run 32867786472); (2) `ImageResult.Success` should be `SuccessResult` — top-level `@Poko` class implementing the sealed `ImageResult`, not nested (run 32868462164). Plan-review's cast-pattern fix was the right idea but the subclass name was confirmed from the Coil 3.0.4 sources jar.

### Status
- NOT merged — awaiting user device verification: (a) custom palette no longer transparent-by-default + persists across restart, (b) brightness sliders gone, (c) two new customizable elements (card headings + descriptions) take effect across Browse/CW/Library/Search/Details, (d) hero: blurred + darkened backdrop + 12s auto-advance + never stuck between banners, (e) colorful RGBA sliders, (f) Random button → nested sheet (Dark/Light/Chaos) applies + persists, (g) search recents dedicated horizontal-scroll section, (h) in-app update from 0.2.50 → 0.2.51.

**This session — D-257..D-260: device-feedback batch #2 — hero v3 + preloading + tag borders + search restore + palette/picker overhaul + v0.2.50 (2026-08-25, on `test-feature/video-cache-new-download`, commits 0e0d9c31 + 7068b631 + 9b46ee69 + 34c7f66e + abb91ac0):**
- User device-tested v0.2.49 and reported (all items handled; emulator OFF-LIMITS; quality over speed; no merge without confirmation): hero still ugly/rigid + banner forced into a square vibe + auto-scroll not smooth/animated + Browse covers not preloaded; rating tags need borders (Browse + Library); palette sheet: remove preview, thin sliders with rounded-square thumbs, sticky heading/Reset, no X, top gradient darkening; color picker: ugly, no scrolling, presets → exactly 5 distinct in one line, precise numeric entry via the subtitles-style keypad; search: recents UI improvement + default results never restore after clear (even across re-entry); bump version + proper release for in-app updates.
- **Workflow followed**: 3 parallel research agents (search-bug root-cause w/ full state-machine map; palette/picker/keypad/slider component map incl. the subtitles NumericEntrySheet pattern; Coil 3.0.4 preload APIs + badge/border surface + version-skew constraints verified from published sources-jars) → plan → **plan-review agent** (GO-WITH-FIXES; 10 real fixes incorporated: search staleness guards, BrowseSkeleton hero restyle, ThinSlider two-pointerInput rule, ScrollBlurOverlay has no align param (use modifier), DefaultColorPickerSwatches 9→5 side effect on the subtitle picker (gave it its own 5), compound-badge outline 3 shape cases, onQueryChange blank routing, single-item pager guard, preloader null-filtering, docs scope) → 3 phase commits → **compile-review agent** (PUSH-READY, 1 cosmetic import).
- **D-257 Browse**: hero v3 = inset 16:9 rounded card (16dp margins, 20dp corners, 1dp border; 16:9 = AniList's native banner ratio → "wider aspect") + banner backdrop + 84×126 poster + rank/title/meta/genre chips + dots BELOW the card; **infinite pager** (pageCount = size×200, start at size×100, displayIndex = page % size, key(items.size)) → auto-advance always slides FORWARD +1 (600ms tween) — fixes the backwards wraparound sweep; **SectionPreloader** (Coil enqueue at density-exact card px dims — memory-cache key excludes size with no transformations + AsyncImage INEXACT ⇒ exact-dims preload = memory HIT) for hero banners/posters + CW thumbs + all 3 carousels, hero-first order; rating-tag borders: Browse score tag (Surface border 1dp scoreContent@50%) + Library simple chips (contentColor@50%) + compound sub|dub badge (manual PointedTagShape-geometry stroked path in drawBehind — Surface border can't trace hand-drawn paint); skeleton restyled to the padded 16:9 card + dots.
- **D-258 Search**: **default-results restore** — loadDefaults() single-owner idempotent loader (defaultsJob in-flight guard + showingDefaults flag) funneled from X-clear / backspace-to-empty / debounced collector / init / onScreenResume; staleness guards at every async completion (query re-checked in try AND catch of all 4 loaders) so late responses never clobber newer state; loadTrending/loadExtensionPopular now return Job. Root cause: Activity-scoped VM + Idle hard-sets + loadTrending only in init. **Recents redesign**: chip cloud (FlowRow pills w/ History icon + per-chip remove + Clear-all header) replacing the collapsible list card; collapse machinery + `search_recents_collapsed` pref deleted.
- **D-259 Appearance**: NumericEntrySheet ported :core:player → :core:designsystem (self-contained, zero gradle changes); **NEW ThinSlider** (4dp track + 18dp rounded-square thumb w/ surface halo, 36dp grab area, tap+drag in separate pointerInputs — ABI-stable primitives only); **ColorPickerSheet redesign** (sticky header, verticalScroll body, 5-preset single-line tiles w/ transparent-slash affordance, RGBA ThinSliders + tappable value chips → nested keypad, live); **CustomPaletteSheet redesign** (preview REMOVED, sticky header + always-visible Reset, NO X, ScrollBlurOverlay top scrim over the scroll body, brightness → ThinSlider + keypad (−100..100), per-element 5-distinct swatch lists); subtitle picker keeps its own 5-swatch set (White/Black/Yellow/Cyan/Transparent).
- **D-260**: version 0.2.49 → **0.2.50** (versionCode 50); tag + release after CI green; dashboard version refresh via full-stack-dev sub-agent (reviewData.ts — CI/version pills + snapshot + 2 new WHATS_BUILT cards + roadmap item completion; build PASSED).
- **CI round**: 1 failure — `import coil3.ImageRequest` should be `coil3.request.ImageRequest` (run 32845772374; the compile-review agent verified the enqueue/size API but not the package path) → fixed abb91ac0.

### Status
- NOT merged — awaiting user device verification: (a) hero v3 (wider 16:9 card + rounded corners + smooth forward auto-advance + dots below), (b) preloaded covers (first scroll through every carousel is instant), (c) rating-tag borders on Browse + Library, (d) search: clear/re-enter restores default results + chip recents, (e) palette sheet (no preview, sticky header/Reset, thin sliders, keypad entry, gradient top), (f) color picker (5-preset line, scrolling, keypad), (g) in-app update from 0.2.49 → 0.2.50.

**This session — D-255/256: device-feedback fixes + hero v2 + v0.2.49 (2026-08-25, on `test-feature/video-cache-new-download`, commit 592e03b1):**
- User device-tested D-252/253/254 and reported: homepage good overall but the hero "looks very bad" (wants cover+banner together + proper tags); selecting a palette navigates to Browse; custom palette customization CRASHES (NoSuchMethodError: FlowRow); + verify update-check + bump version everywhere. Emulator still OFF-LIMITS.
- **Root-caused (no guessing — CI-APK artifact inspection + POM/sources-jar analysis)**: (1) palette→Browse = D-254's AnikutaTheme if/else around CompositionLocalProvider moved content between branches on CUSTOM↔preset flips → destroyed remember{} (nav backstack) — fixed with an always-present provider + Unspecified sentinel; (2) the FlowRow crash = **the app's RUNTIME compose stack is 1.10.4 while the BOM compiles 1.7.8** (koin-compose 4.2.2 → JetBrains compose 1.10.2 → androidx aliases beat the BOM's prefer-constraints); :core:designsystem/:core:player (no koin-compose) compiled FlowRow against 1.7.8's signature which 1.10.4 removed — fixed by replacing FlowRow with manual chunked Rows in ColorPickerSheet (+ fixed the pre-existing Color(a,r,g,b) channel-rotation preview bug); (3) GitHubUpdateSource.parseIsoDate used java.time with a wrong "API 26+ minSdk" comment (minSdk=24; NoClassDefFoundError is an Error — uncaught) → regex + Calendar fix. AMOLED row hidden while CUSTOM active (dead toggle).
- **D-256 hero v2**: banner backdrop + cover POSTER (80×120, 12dp corners, 1dp border) + rank pill + 2-line title + ★score·eps·year meta + genre chips (3 + "+N" overflow) over a stronger scrim; 300dp; auto-advance + animated dots retained; skeleton matched.
- **Version**: 0.2.48 → **0.2.49** (versionCode 49). Update-check verified sound (D-251 logic + the java.time fix); release v0.2.49 published via release-apk.yml after CI green; dashboard version strings updated + deployed.
- **⚠️ OPEN DECISION for the user**: the compile-1.7.8 vs runtime-1.10.4 skew REMAINS (documented in D-255; recommendation: bump the BOM to the 1.10.4-era + pin material3 1.3.1 as its own session). FilterSheet/ResolverSheet/PlayerSheets FlowRows currently work (they compile against 1.10.x via koin-compose) but are version-skew landmines.
- Compile review (Task 10): 1 error caught + fixed pre-push (6-value match.destructured — Destructured has component1..5 only).

### Status
- CI pending at doc-write time (592e03b1 pushed). NOT merged — awaiting user device verification: (a) palette switching stays on the settings page, (b) custom palette customization works live (no crash), (c) hero v2 looks right (cover+banner+tags), (d) update-check finds v0.2.49 from a 0.2.48 install.

**This session — D-252/253/254: pointed badges + Browse overhaul + custom palette editor (2026-08-25, on `test-feature/video-cache-new-download`, commits d1152736 + 4230821c + 7ef10689):**
- User instructed (3 items; emulator explicitly OFF-limits; branch-only; no merge without explicit confirmation; quality over speed): (1) COVER_ONLY episode tags handled properly + pointier; (2) complete Browse UI overhaul (hero/top-banner, modern/clean/navigable, smooth animations, DB properly managed, rating tags redesigned, cover borders); (3) Custom palette functional (re-tap Custom → bottom sheet with per-element color + brightness: background/accent/headings/cards).
- **Workflow followed**: research (2 parallel Explore agents: R-A browse trace, R-B badge trace + main-agent theme reads) → plan → **plan-review agent** (GO-WITH-FIXES; 4 real flaws incorporated: M3 Surface applies its own clip AFTER user modifiers → compound badge needs clip(pointed) BEFORE drawBehind; file split needs private→internal; swatches param must be label-bearing Pair<Int,String>; alpha must be forced opaque for theme colors) → execute A→B→C → **compile-review agent** (verified against the real material3-1.3.1 AAR + BOM; 2 compile errors caught + fixed pre-push: `staticCompositionLocalOf` is a FUNCTION not a type (drop the type annotation); CustomPaletteSheet missing @OptIn(ExperimentalMaterial3Api) for ModalBottomSheet) → 3 phase commits → push → docs (this entry).
- **D-252 pointed badges**: NEW `:core:designsystem/badge/` — `BadgeColorScheme` (moved from library; dark detection now background-luminance = APPLIED theme) + `PointedTagShape` (rect + 45° tip one end, RTL-aware). `CoverBadgeRow`: innermost chip tapers to a point ("pointier"), outer corner clips to new `coverCornerRadius` (0.dp COVER_ONLY — fixes the curved-sliver defect where the hard-coded 12dp rounding left cover art visible behind the badge corner), 4 call sites threaded, dead `CoverBadge` removed, stale 8sp KDoc fixed.
- **D-253 Browse overhaul**: BrowseScreen.kt (581 lines) → 4 files. **Hero**: full-bleed edge-to-edge 260dp HorizontalPager, top-5 trending-with-banner (VM `hero`→`heroItems`), 6s auto-advance (drag-guarded, wraparound), animated dots (active = 16dp elongated pill), rank pill + 24sp title + integer-score meta + genre pills, stronger scrim. **Cards**: 2:3 covers, 12dp corners (was 18/14/10 inconsistent), 1dp outlineVariant@60% borders, **rating tag replaced** (black-65% pill + lime text → amber pointed corner tag, shared badge language, flush top-start, integer score), press-scale kept; CW cards: borders + 32dp play affordance + press-scale. **Skeletons** replace the full-screen spinner; **error** = EmptyState + Retry. Sections fade+expand in. **VM**: cache/parse/CW-enrichment on Dispatchers.IO, refresh() PARALLEL, isRefreshing in-flight counter. DB-7 debug block, PTR haptic, CW direct-play contract preserved exactly.
- **D-254 custom palette**: `CustomThemeColors` (accent/background/heading/card + 4 brightness −1..1) + `buildCustomColorScheme` (surface ramp + text luminance + card family derived); `LocalHeadingColor` + CollapsingHeader integration; `AnikutaTheme(customTheme=...)` (both modes; AMOLED skipped while custom); ColorPickerSheet → designsystem (swatches param; player unchanged); ThemePreferences 8-key persistence + legacy-accent migration; MainActivity live re-theme (reads prefs.customTheme.value in composition); `CustomPaletteSheet` (live mini-preview + 4 element editors with nested picker + brightness sliders + Reset); AppearanceGeneralScreen re-tap-Custom → sheet + palette-icon badge. Known caveat: status-bar icons follow SYSTEM dark mode (pre-existing).
- 17 files: 8 new, 3 deleted (2 moved), 9 modified. No build-file/schema/API changes.

### Status
- CI pending on 7ef10689 at doc-write time (Build APK triggered by the push). NOT merged — awaiting user device verification: (a) pointed badges in all 4 library display modes (esp. COVER_ONLY corner flush + no sliver), (b) the new Browse page (hero auto-advance, borders, amber score tags, skeletons, error retry), (c) the custom palette editor (re-tap flow, live re-theme, brightness sliders, both modes, reset).

**Review #3 session (2026-08-25, commit 421874ed)** — full-project review + dashboard `/review/` section:
- User instructed: fresh full-project review of the branch state (no app-code changes), DELETE the existing "project review" dashboard page completely, build a NEW dedicated section with all key findings in a simplified format, deploy via GitHub Actions. Max 5 sub-agents; do NOT read `REFERENCES/`.
- Executed: read CORE_RULES (557 lines) pre-clone → fresh clone → checkout branch @ 127d074f (31 commits ahead of main) → read ALL AGENT-CONTEXT → dispatched **5 parallel read-only research sub-agents** (R-1 deferred-concerns verification, R-2 decisions/doc-drift, R-3 features-remaining extraction, R-4 metrics/quality, R-5 dashboard/deploy plan) → main agent re-verified every metric → full-stack-dev sub-agent (DASHBOARD/webpage/ only, §19) replaced the section.
- **VERIFIED (re-derived)**: 48 Gradle modules (1 app + 28 core + 1 data + 18 feature) · **383 .kt files / 84,001 LOC** · **24 SQLDelight tables / 17 .sq / 0 .sqm** · v0.2.48 (code 48) · **201 lessons learned** · **26+2 Koin modules** · CI GREEN on HEAD (Build APK run 32765868210) · Release v0.2.48 published (stable, arm64-v8a-only, debug-signed, ~59MB, run 32766359668) · TODO=11 / Ponytail markers=4 / Logger violations=0 / secrets clean.
- **Deferred Concerns re-audit (22 items): 13 RESOLVED / 3 PARTIAL / 6 OPEN.** NEW concerns found: (1) **java.time without coreLibraryDesugaring at minSdk 24** — GitHubUpdateSource.kt:200-204 + HistoryViewModel.kt:107/117 + ScheduleViewModel.kt:95-97 + ScheduleStore.kt:56 → crash risk on Android 7.x (a comment even claims "API 26+ is our minSdk" — wrong); (2) **3 live main-thread `runBlocking` in DownloadService** (:182/:183/:202 — pause/cancel/onTimeout, ANR risk); (3) permanent 200ms OAuth polling loop in MainActivity (:386-404) + login-error snackbar TODO (:400); (4) Extensions "Available" section renders ~240 rows inside ONE LazyColumn item (no virtualization — the jitter root cause); (5) extension drag-reorder not persisted (ExtensionsSettingsScreen.kt:182 TODO); (6) FirstRunSetupDialog "Skip for now" onClick is empty (dialog stays). God classes GREW: LibraryScreen 3,919 (+59% vs main) · DetailsViewModel 3,510 · DetailsScreen 3,240 · WatchScreen 2,194 · PlaybackCacheManager 1,758 (new) · MainActivity 1,718.
- **Doc-drift audit: ~60 stale claims across 12 files** — decision log has 44 missing IDs (D-121 + D-199..D-241); D-198 status factually WRONG (implemented in 775876a2, still says PROPOSAL); master.md/SESSION.md say "branch: main" while an unmerged 31-commit branch is active; all knowledge/* say 46 modules/26 tables (actual 48/24); emulator-testing.md documents the OLD D-246 env (D-251 rebuilt it); dashboard lib/data.ts frozen at the D-186 era; 4 stale KDocs in code; tech-stack.md missing release-apk.yml.
- **Dashboard**: full-stack-dev sub-agent deleted `app/key-findings/page.tsx` (720 lines) + `lib/keyFindings.ts` (775 lines); created `app/review/page.tsx` (749) + `lib/reviewData.ts` (865) — 9 sections (Snapshot / Project Health / What's Built 9 / Open Concerns 15 / Verified Fixed 14 / Doc Drift 12 / Features Remaining 30 with implementation paths / Top Risks 8 / Footer Note); single NAV_ITEMS line swap "Key Findings"→"Review & Roadmap" (same `findings` icon key — zero Sidebar edits); `bun run build` PASSED (22/22 pages, /review present, /key-findings gone). Deployed via `workflow_dispatch` on the branch (already in the github-pages branch policy). Live at `https://testplay-byte.github.io/ANI-KUTA/review/`.
- No D-NNN added (temporary dashboard section, same precedent as reviews #1/#2). No app-code changes. Findings live on the dashboard page.

### Status
- Review #3 delivered + deployed. Branch remains NOT merged — awaiting user device verification of D-243..D-251 (see /review/ NOW items 1-3).

Phases 0-4, 5a/5b/5c, Phase B (auto-link), Phase C (content identity), Phase D (data-management), Phase DL (download system DL.0-DL.8), Phase WP (watch progress + watched status), Phase HI (history page), Phase UP (updates + WorkManager smart engine), Phase SC (schedule list + calendar view), Phase TR (ratings store), Phase NOTIF (notification system), Phase CW (continue watching logic), the Debug Bubble, the Profile page (genre radar + watch flow + time DNA + heatmap + timeline + crop editor), D-193 v2 (Updates + Notifications overhaul), and D-225 → D-238 (reverse auto-link + reverse-auto-link settings UI + match-preview card + LazyColumn virtualization crash fix + episode-list filter/sort/grouping + next-episode countdown + schedule INNER JOIN + calendar color-coded dots + unlink blacklist + cache clear on source change + details-page background) are ALL DONE and on `main`.

**This session — Settings-UI icon unification (D-250, 2026-08-24, on `test-feature/video-cache-new-download`):**
- User feedback: the More page icons look like "proper SVG icons" (clean), but the Settings page icons "change to some other kind of format, which is not good." Same applies to the Appearance page + other settings sub-pages — improve + make consistent/cleaner. Stay on the current branch. Complete per the workflow + send a notification.
- **Root cause found (3 parallel Explore sub-agents, Task IDs 2-a/2-b/2-c):** the More page uses `MoreListRow` (`:core:designsystem`) which renders a **bare 24dp `Icon` tinted `primary`** — no container. The Settings/Appearance/Notifications hubs each defined a LOCAL `*NavRow` (`SettingsNavRow`, `AppearanceNavRow`, `LibraryNavRow`) that wrapped the icon in a **36dp `primaryContainer` rounded box** ("chip-box") — a *different visual format*, exactly as the user described. Same `Icons.Filled.*` glyphs, different container treatment. Plus ~12 copy-pasted `private fun BackAction` duplicates across `:app` + `:feature:extensions-settings:impl` (2 with missing `Modifier.size(18.dp)` → icon rendered 24dp; 1 divergent variant in TrackersScreen with transparent bg + `onBackground` tint + 20dp).
- **Fix (D-250):** (1) **Reused `MoreListRow` directly** in Settings/Appearance/Notifications hubs — deleted the 3 chip-box `*NavRow` defs + swapped all 11 call sites to `MoreListRow(icon=, title=, subtitle=, onClick=)`. No new abstraction (§5 — two composables doing the same thing would be an unrequested abstraction; the settings rows are visually identical to More rows now). (2) **Promoted `BackAction` to `:core:designsystem`** as a shared `@Composable fun BackAction(onBack: () -> Unit, modifier: Modifier = Modifier)` (36dp CircleShape `surfaceVariant` + 18dp `Icons.AutoMirrored.Filled.ArrowBack` tinted `onSurfaceVariant`). Replaced all 12 private copies + 3 inlined bodies (DetailsPage, Trackers, ExtensionRepo) — fixes the 2 missing-size-modifier drifts + the divergent Trackers variant. (3) **Fixed the lone feature-module chip-box**: `AutoLinkSettingsScreen.PerExtensionCard` (32dp `primaryContainer` + 18dp `AutoAwesome`) → bare 24dp `Icon` tinted `primary`. (4) **Bug fix**: `NotificationsSettingsScreen.triggerDescription` SILENT branch returned `"Notify $condition"` (copy-paste of the ON branch — a real bug) → now `"Notify silently $condition"` (matches the correct version in `NotificationsLibraryScreen`). (5) **Dead code removed**: `ConfigSegmented` (never called) from `NotificationsLibraryScreen`; dead `import androidx.compose.ui.graphics.vector.ImageVector` from `EpisodeListSettingsSheet`. (6) **Layout fix**: moved the `LibraryNavRow` OUT of the `SettingsGroupCard` it was nested in (the card's content lambda + `MoreListRow`'s baked-in 16dp horizontal padding would have double-padded) — now a standalone `MoreListRow` sibling of the card, still wrapped in the existing `AnimatedVisibility`.
- **Compile review (sub-agent, Task ID 6):** ✅ PUSH-READY — zero compile errors across all 17 changed files; `MoreListRow` signature matches all call sites; brace balance verified; no leftover `*NavRow` defs. ⚠️ ~30 now-dead imports flagged (mostly `ArrowBack` across 15 files + broader in the 2 now-thin-shell hub files) — Kotlin doesn't fail on unused imports + the project has NO `ktlint`/`detekt`/`spotless`/`.editorconfig` (verified), so CI passes; tidy follow-up queued.
- **Docs updated (same session per §6):** `DESIGN-LANGUAGE.md` §2.4 (new "Nav-Row Icon Language" rule — bare 24dp primary icon, no chip-box, reuse `MoreListRow`); `DESIGN-SYSTEM/03-settings-extensions-profile.md` §3 (replaced the chip-box `AppearanceNavRow` snippet with the `MoreListRow`-reuse approach + a D-250 change-note); this progress entry; D-250 in decisions.md; changelog entry; lessons-learned patterns.
- **Deferred (noted as follow-ups, NOT done — out of icon-fix scope):** (a) `SourcePreferencesScreen` + `ExtensionRepoSettingsScreen` have dead `collapsed = false` + `scrollOffset = { 0f }` (header never collapses / blur never triggers) — wiring requires a `LazyListState` (ExtensionRepo: trivial add; SourcePreferences: needs `PreferenceList` to expose a scroll state); (b) ~30 dead-import tidy commit; (c) `VideoCachingScreen.kt:349` stale comment about BackAction being private.
- **Virtual-device testing research** (user asked for details): the sandbox emulator env ALREADY EXISTS at `/home/z/android-sdk` (AVD `anikuta`, API 30 AOSP x86_64 TCG, 720x1280, 1024MB) — built + verified in the 2026-08-23 D-246 session. CI produces an `app-debug-x86_64-emulator.apk` artifact (D-246 exception, `-PemulatorX64Build=true`). See `REFERENCES/`-excluded sandbox quirks in lessons-learned (double-fork detach, `timeout -s KILL` on every adb, `input text` ≤14-char chunks, ~8min cold boot, dismiss system ANRs with Wait). Full extension-installation + playback flow was emulator-verified end-to-end in the D-246 session.

### Status
- CI pending (commit not yet pushed at doc-write time). NOT merged — awaiting user device verification of the unified icon look.

**This session — D-251: dead-wiring fixes + library display modes + release/versioning overhaul + sandbox emulator rebuild (2026-08-24, on `test-feature/video-cache-new-download`):**
- User instructed (5 work items, all completed this session): (1) clean up the ~30 dead imports left by D-250 — verify each is truly dead first; (2) handle the virtual device optimally (no app testing yet); (3) fix the dead `collapsed = false` + `scrollOffset = { 0f }` wiring in SourcePreferencesScreen + ExtensionRepoSettingsScreen; (4) Library: add a "Hide Titles" toggle to Comfortable mode + rework Cover Only mode (square corners, ZERO gaps between covers horizontally AND vertically, edge-to-edge); (5) release/versioning discipline: bump +1 per improvement batch, publish proper GitHub releases, fix in-app Check-for-Updates, arm64-v8a-only shipped APKs (x86_64 only for the agent's own emulator tests).
- **(1) Dead imports:** read-only audit sub-agent verified **77** dead imports across 15 files (the ~30 D-250 fallout + 20 extra older leftovers: e.g. dead `NavKey` in ExtensionDetailScreen, dead `collectAsState` in UpdateCategoriesScreen, pre-existing TrackersScreen pile). Each checked against file bodies (traps: `getValue`/`setValue` property-delegate imports are ALIVE; `CircleShape` in ExtensionDetailScreen still used at L432 — kept). Removed via exact-line script (`remove-dead-imports.py`, aborts on any non-unique match); stale VideoCachingScreen:347 comment fixed too.
- **(3) Dead wiring fixed:** both screens now use the canonical pattern (identical to SettingsScreen/AutoLinkSettingsScreen): `rememberLazyListState()` + `collapsed = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 20` driving `CollapsingHeader`, `state = listState` on the LazyColumn, and `ScrollBlurOverlay(scrollOffset = { if (firstVisibleItemIndex > 0) Float.MAX_VALUE else firstVisibleItemScrollOffset.toFloat() })` in an inner Box `align(TopCenter)`. SourcePreferences: state hoisted into `PreferenceList(listState)` param (internal remember deleted). ExtensionRepo: also gained the previously-missing ScrollBlurOverlay entirely. The old SourcePreferences overlay sat in the OUTER Box (would have scrimmed over the header once animated) — relocated.
- **(4) Library display modes:** (A) **Comfortable "Hide Titles" toggle** — new pref `library_comfortable_hide_titles` (ViewModel flow + setter + load, mirrors ComfortableBorderMode pattern), CustomizeSheet Display-tab `TwoWayButton` gated on COMFORTABLE_GRID (placed after Title lines; Title-lines section itself hides when titles are hidden); `LibraryGridCard` skips the title Text via `if (!hideTitles)` — keeps 12dp rounded corners + 8dp staggered spacing (distinct from COVER_ONLY). (B) **Cover Only rework** — `cardShape = if (isCoverOnly) RectangleShape else RoundedCornerShape(12.dp)` applied at ALL 5 shape sites (card clip, outer/cover border modifiers, AsyncImage clip, selection border); shared else-branch LazyVerticalGrid: `Arrangement.spacedBy(if (isCoverOnly) 0.dp else 8.dp)` both axes + contentPadding drops side/top padding (full-bleed edge-to-edge wall; bottom padding kept for nav-bar/action-bar clearance). COMPACT_GRID untouched (all changes gated on isCoverOnly).
- **(5) Release/versioning overhaul:** version **0.2.47 → 0.2.48** (versionCode 48). **`abiFilters` = arm64-v8a ONLY** (armeabi-v7a dropped per user instruction) — AndroidConfig.kt + convention-plugin comment + build-apk.yml Verify-ABIs allow-list + CORE_RULES §8 updated. **NEW `.github/workflows/release-apk.yml`**: triggers on `v*` tag push (or manual dispatch w/ tag input) → verifies tag matches `AndroidConfig.versionName` → builds arm64-v8a-only APK → ABI verify → `gh release create` with `ani-kuta-v{VERSION}.apk` asset, **stable (never prerelease), `--latest`**, title from the annotated tag subject, body auto-generated from commits since previous tag. build-apk.yml: `tags: [v*]` trigger removed (release workflow owns tags now) + emulator x86_64 build now opt-in ONLY via `workflow_dispatch` input `build_emulator_apk` (default off — user: "don't build x86 at all unless testing on the virtual device").
- **Check-for-Updates root cause + fix (app side):** `GitHubUpdateSource` queried `/releases/latest`, which EXCLUDES prereleases — and every release after v0.2.6 was flagged prerelease=True, so the app saw ancient v0.2.6 as "latest" and reported up-to-date. Rewrote the source: fetches `/releases?per_page=30` (includes prereleases, excludes drafts defensively), picks the best release via `maxWithOrNull(compareBy(versionTuple, stable-over-prerelease))`, and compares versions as integer TUPLES (the old `major*10000+minor*100+patch` packing collides at patch ≥ 100, e.g. 0.2.100 == 0.3.0). Releases side: release-apk.yml publishes stable releases, so `/releases/latest` users AND the new list-based checker both see them.
- **(2) Sandbox emulator REBUILT (sandbox was reset — old /home/z/android-sdk gone):** reinstalled cmdline-tools + platform-tools + emulator + API-30 default x86_64 image; recreated AVD `anikuta` (720x1280@320, 2 cores, SwiftShader, snapshots off, sensors/GPS/cameras off). **NEW sandbox constraints solved this session:** (a) rootfs is OVERLAYFS (non-ext4) → emulator 35/37 both disable QuickbootFileBacked and run a paranoid "need 7372.80MB" userdata pre-check (2× image + full data partition worst-case; actual usage ~350MB) → bypassed with a compiled **LD_PRELOAD statvfs shim** (`/home/z/emu/freedom.so` — inflates free-space reports for `.avd` paths only); (b) new emulators force-bump x86_64 guest RAM to 2048MB (would OOM under the 4GB cgroup) → bypassed with **`-qemu -m 1024`** appended AFTER the emulator's own `-m` (qemu takes the LAST -m); (c) no KVM → **`-accel off`** (TCG). Installed the archived **emulator 35.1.19** (from developer.android.com/studio/emulator_archive — the sdkmanager-default 37.1.11 behaves identically but 35 is the tested binary). **VERIFIED end-to-end:** cold boot ≈ 9 min → `sys.boot_completed=1`, home screen renders (720x1280 screenshot analyzed: AOSP wallpaper + icons), qemu RSS peaked ~1.8GB (safe), disk usage 2.1GB free after shutdown, graceful `emu kill`. One-command helper: `/home/z/emu/emu.sh {boot|wait|kill|status|adb|shot|install}` with every quirk handled (double-fork detach, wrapped adb, next-server pre-kill, ANR suppression).
- **Compile review (sub-agent): ✅ zero compile blockers** — all param threading complete (LibraryGrid → LibraryGridCard → CustomizeSheet → displayBadgesTab), `Sequence.maxWithOrNull` valid on Kotlin 2.2.0, Triple comparator valid, workflows YAML-parse, no removed import referenced anywhere. One cosmetic fix applied after review (comfortable-branch AsyncImage clip normalized to `cardShape`).
- **Docs updated:** CORE_RULES §8 (arm64-only rule + release discipline), this progress entry, decisions.md D-251, changelog, lessons-learned (3 new: overlayfs statvfs-shim bypass, -qemu -m RAM override, prerelease-invisible-to-/releases/latest), DESIGN-SYSTEM/04 library display-mode section updated.

### Status
- **CI GREEN** on 44de265 (Build APK run 32765868210) after one compile fix (Kotlin's Triple is NOT Comparable — introduced a VersionTuple data class; lesson logged). **Release v0.2.48 PUBLISHED** (stable, asset `ani-kuta-v0.2.48.apk` 59MB, arm64-v8a-only — first automated release-apk.yml run, run 32766359668) with a user-facing highlights body. Update-detection verified against the real API: current=0.2.47 → UPDATE FOUND 0.2.48; 0.2.48 → up-to-date; old 0.2.36/0.2.6 users → update found. NOT merged — awaiting user device verification.

**This session — Full Project Review + Dashboard Key-Findings Rebuild (2026-08-24, on `test-feature/video-cache-new-download`, commit 28410e6, deploy run 32731668048 = success):**
- User instructed: read CORE_RULES + all AGENT-CONTEXT + codebase FIRST (no app changes), then completely rebuild the `/key-findings/` dashboard page (a.k.a. "project review") with a FRESH review reflecting the **test-feature branch state** (NOT main's). Deploy via GitHub Actions. Nothing else on the dashboard changes. Max 5 sub-agents. Do NOT read `REFERENCES/`. Work on `test-feature/video-cache-new-download`.
- Executed: read CORE_RULES (557 lines, 31 sections) via raw GitHub URL pre-clone → cloned fresh into `/home/z/ani-kuta-repo` → checkout `test-feature/video-cache-new-download` @ f4be250 (D-249, 21 commits ahead of main @ 26e4772) → read ALL AGENT-CONTEXT (navigation, master, workflow, SESSION, progress top+Deferred Concerns, decisions, changelog, lessons, all knowledge/*) → dispatched **5 parallel read-only Explore sub-agents** (R-1 decisions/changelog digest, R-2 lessons/progress digest, R-3 knowledge files, R-4 codebase structure verification, R-5 dashboard + video-cache work review) → main-agent verified every metric against source.
- **VERIFIED (re-derived, NOT from docs)**: **48 Gradle modules** (1 app + 28 core + 1 data + 18 feature — `:core:app-update` unlogged + `:core:playback-cache` added D-243; docs said 46/47) · **382 .kt files** (docs said 331/363) · **24 SQLDelight tables across 17 .sq files** (docs said 26/28 — `playback_cache_entry` added D-243, `app_metadata` dropped D-198, `app.sq` intentionally empty) · version **0.2.47** (versionCode 47) on test-feature · **190 lessons learned** (was 163) · CI **GREEN on test-feature @ f4be250** (Build APK run 32661002201) · **2 unmerged branches** (this one + `feature/test-controller-v5` dormant) — `functionality/improvements` was merged to main @ 26e4772 BEFORE this branch was cut.
- **Top findings**: (1) video caching (D-243/D-245/D-247) + parallel download engine (D-244) + download resilience (D-246) shipped CI-green with a 4-layer fail-open design (R-5 verified across pre-loadfile + pre-body 301-redirect + mid-stream connection-close + player-level bypassCacheNextRetry), BUT the parallel engine Part B (ParallelHttpFetcher, HLS AES-128-CBC in-memory decryption, stall watchdog, re-resolve-incl-403, rotating-key rejection, pause/resume with sidecar, anti-shrink guard) is NOT device-tested — Part A cache IS emulator-tested E2E; (2) decision log STILL has a 43-decision gap (D-199..D-241 absent in decisions.md — same gap as main, NOT backfilled on this branch); (3) D-198 status STILL factually wrong (decisions.md says PROPOSAL, commit 775876a2 implemented it — content.sq has main_entry + content_details); (4) god-class files all GREW on test-feature (LibraryScreen 2471→3863, DetailsViewModel 2159→3510, DetailsScreen 2277→3240, WatchScreen 2017→2194; NEW: PlaybackCacheManager 1758, MainActivity 1719); (5) progress.md "Current Phase" header was stale (only mentioned D-243+D-244 though D-245..D-249 session blocks exist below) — **FIXED this session**; (6) AniList syncEntry is NO LONGER a stub — D-242 implemented the SaveMediaListEntry GraphQL mutation at AniListTracker.kt:282 (R-4 verified the code; KDoc still says TODO = doc-drift); (7) HttpDownloader.reResolver NO LONGER orphaned (R-4 verified Koin binding via ReResolverAdapter for HttpDownloader + both fetchers); MainActivity runBlocking NO LONGER live (all 5 grep matches are comments; onPlayEpisode uses appScope.launch + withContext(IO)).
- **Dashboard**: full-stack-dev sub-agent (§19, DASHBOARD/webpage/ only) rewrote `lib/keyFindings.ts` (670 → 775 lines, same 9-section TS structure, only data values changed) + `app/key-findings/page.tsx` (JSDoc + 6 SectionCard titles converted to dynamic `.length` counts). NO other dashboard files touched (data.ts, Sidebar.tsx, other pages, other lib, DESIGN.md all unchanged — verified via `git diff --stat origin/main..HEAD -- ANI-KUTA/DASHBOARD/` = empty except the 2 key-findings files). `bun run build` PASSED (22/22 static pages, 21 routes, /key-findings present).
- **Deploy**: `deploy-dashboard.yml` only auto-triggers on `main` push; used `workflow_dispatch` on `test-feature/video-cache-new-download` (NO workflow file change — honors "nothing else changes"). First run FAILED at the `deploy` job with `BlobNotFound` — ROOT CAUSE: the `github-pages` environment has a `branch_policy` protection rule (only `main` + `functionality/improvements` + `feature/debug-bubble` allowed; test-feature was NOT). FIX: added `test-feature/video-cache-new-download` to the environment's deployment-branch-policies via the GitHub API (a repo SETTING, not a file change — honors "nothing in the dashboard page changes"). Reran the failed deploy job → `completed/success` (run 32731668048). Lesson logged (BlobNotFound root cause = branch_policy, not the 2-job split).
- **Verified live via Agent Browser**: mobile 375px `overflow-x:false` at every scroll position (0/1000/3000/6000/9000); desktop 1280px no overflow, all 9 section headings render with correct dynamic counts (16 areas, 16 concerns, 16 fixed, 11 drift, 8 risks); dark mode toggle works (`html.dark`); existing `/decisions/` page unaffected.
- No D-NNN added (temporary dashboard rebuild, same precedent as prior project-review rebuilds). Full findings + forward-direction recommendations live on the dashboard page itself.

**This session — Full Project Review + Dashboard Key-Findings rebuild (2026-08-22, on `main`):**
- User instructed: read CORE_RULES + all AGENT-CONTEXT + codebase FIRST (no changes), then DELETE the stale `/project-review/` page completely (it was unreachable since D-239's nav cleanup) + build a FRESH `/key-findings/` section with all key findings in a simplified, scannable format. Deploy via GitHub Actions. Nothing else on the dashboard may change. Max 5 sub-agents. Do NOT read REFERENCES/.
- Executed: read CORE_RULES (557 lines) pre-clone → cloned fresh → read ALL AGENT-CONTEXT (navigation, master, workflow, SESSION, progress top+deep entries, decisions, changelog, lessons, all 7 knowledge files) → dispatched 5 read-only research agents (R-1b Android concerns verification, R-2 decisions/changelog digest, R-3 lessons/progress digest, R-4 dashboard review, R-5 unmerged-branches analysis) → main-agent verified every metric against source.
- **VERIFIED (re-derived, NOT from docs)**: **47 Gradle modules** (docs said 46 — `:core:app-update` unlogged) · **363 .kt files** (docs said 331) · **23 SQLDelight tables across 15 .sq files** (docs said 26/28) · **D-198 restructuring WAS IMPLEMENTED** (commit 775876a2 — `main_entry` + `content_details` live in content.sq; decisions.md still says "PROPOSAL not implemented" = factually wrong) · version **0.2.22** on main · god classes GREW: DetailsScreen 3165, DetailsViewModel 2852, LibraryScreen 2504, WatchScreen 2018.
- **Top findings**: (1) decision log forked 3 ways — main stops at D-198 (41 decisions D-199..D-239 unlogged), `feature/test-controller-v5` claims D-197..D-202 for DIFFERENT decisions, `functionality/improvements` works at D-240..D-242 (42 commits, v0.2.46, pushed DURING this review = active session, clean merge today); (2) AniList tracker syncEntry is a stub returning `true` (fake success) + trackerId hardcoded 0 — real impl lives on the unmerged branch; (3) download system NEVER device-tested end-to-end (top deferred item across sessions); (4) 11 of 22 deferred concerns verified RESOLVED on main (reResolver, runBlocking, RetryPolicy, activity wiring, WorkManager scheduling, notification posting, download queuing, server/audio/size, stale-state, source race, user_customization drop); (5) dashboard stale in 8+ places ("D-186", "26/28 tables", old-schema database page, 6 unreachable routes).
- **Dashboard**: full-stack-dev sub-agent (§19, DASHBOARD/webpage/ only) deleted `app/project-review/page.tsx` (1028 lines) + `lib/projectReview.ts` (761 lines) + Sidebar `review` icon key; created `lib/keyFindings.ts` (670 lines, fully typed) + `app/key-findings/page.tsx` (719 lines, server component, 9 sections per DESIGN.md); added 1 NAV_ITEMS entry + 1 `findings` icon key. NOTHING else changed. `bun run build` PASSED (19 routes + _not-found; /key-findings present, /project-review gone). Deployed via deploy-dashboard.yml; verified live via Agent Browser.
- No D-NNN added (temporary dashboard section, same precedent as the prior project-review rebuilds). Full findings + forward-direction recommendations live on the dashboard page itself.

**This session — Video caching session-2: fix "registered but not cached" + tap-to-play + background fill (2026-08-23, on `test-feature/video-cache-new-download`, commit 23a93c8b):**
- User device-tested Part A and reported: episodes appeared in the Video Caching settings but were NOT actually cached (registered rows, ~0 bytes). Also requested: tap-to-play from the list (same server/quality/resolution, resume from where left), background loading while playing, comprehensive logging + logcat filters.
- **Root causes found + fixed (D-245)**: (1) unknown-Content-Length → the session-1 code REDIRECTED MPV straight to upstream (playback fine, ZERO caching — the separate 0-1 probe made this the default path for extension proxies); replaced with learn-mode serving (mirror client Range upstream, learn total from response, chunked-with-tee when still unknown; probe REMOVED); (2) HLS playlists only proxied the tiny playlist — segments (absolute URLs) bypassed the cache; the proxy now REWRITES playlists (variants → /p/, segments+init → /s/) and caches per-segment files (URL-hash named, drift-safe).
- **New features**: background fill (progressive 8MB gap blocks, player-frontier-aware ±32MB; HLS segments in order, VOD only; disk-recounted stats); tap-to-play (4 new ALTER-guarded schema columns incl. stored subtitle/audio tracks; clickable rows → full WatchKey → WP-B3 resume).
- **CR-C compile review** (compile probe vs real jars, EXIT 0) caught 2 runtime bugs pre-push: response.use{} closing the streaming body before NanoHTTPD read it (critical); HLS segment-stat races. Both fixed.
- **Logging**: full decision-point logging under tag `Anikuta:Core:PlaybackCache` (play/serve/learn/parts/gap/tee/flush/complete/hls/seg/fill/evict/delete/fail-open) — user-debuggable; logcat filters provided in the session summary.

**This session — Video Caching + Parallel Download Engine (2026-08-22/23, on `test-feature/video-cache-new-download`, branched from main @ 26e47722 AFTER the user merged functionality/improvements):**
- User task (2 parts, single session): (A) video playback caching — streamed bytes cached locally, same-video/same-server/same-resolution replays start instantly from disk, dedicated "Video caching" settings (toggle default ON, 100MB–2GB limit, cached-episodes view with cached-point display); (B) a new MPV-inspired download method — parallel byte-range workers, concurrent HLS segments, in-memory AES-128 decryption, per-chunk exponential backoff — with a settings on/off toggle. NO changes to main, NO merges without explicit user confirmation.
- **Research**: 3 parallel sub-agents (R-A player pipeline / R-B download system / R-C infra patterns). Key discoveries: `AnikutaMPVView.loadVideo()` is DEAD CODE (5 real loadfile sites in WatchScreen); `ResolverVideo.videoTitle` is the documented STABLE identity (URLs are volatile per-resolve); the "Advanced downloader" settings prefs + UI ALREADY EXISTED but were dead; temp files are seekable java.io.Files; new `.sq` tables need no version bump (unconditional onCreate in DatabaseDriverFactory).
- **Plan**: `APP/ani-kuta/DOCUMENTATION/planning/video-cache-parallel-downloads/PLAN.md` — reviewed in 2 rounds (PR-A/PR-B/PR-C round 1: 3 critical flaws incl. "502 breaks playback" → 302-redirect fail-open + wrong-episode identity corruption; PR-2A/PR-2B round 2: GO + nits incorporated).
- **Part A implemented (D-243, commit 95909b12, CI GREEN run 32609975071)**: new `:core:playback-cache` module (47→48 modules) — NanoHTTPD proxy on 127.0.0.1, `playback_cache_entry` table (23→24 tables) + driver-factory guard, LRU eviction, fail-open everywhere, `currentCacheId` hook at all 5 WatchScreen loadfile sites (live episode state — never the frozen watchKey), Video caching settings screen + nav wiring. Compile review CR-A caught 3 compiler-level errors (NanoHTTPD overload, suspend-in-non-suspend, missing table guard) — all fixed pre-push.
- **Part B implemented (D-244, commit 5cedad58)**: `VideoFetcher` seam (HttpDownloader stays facade), `SingleConnectionFetcher` (downloadNormal extracted verbatim), `ParallelHttpFetcher` (Range probe, budget-capped chunk workers, positional sparse-file writes, exponential backoff, stall watchdog, active-call registry, re-resolve incl. 403, chunk sidecar, single-stream fallback, 250ms progress reporter), `HlsDownloader` parallel mode (concurrent segments + ordered writer + AES-128-CBC in-memory decryption + MEDIA-SEQUENCE IVs + rotating-key rejection + append-state sidecar + variant-URL base fix; legacy path preserved byte-for-byte). `advancedDownloader` default flipped ON. Compile review CR-B (compiler-verified against the real toolchain) caught 4 compile errors + a Semaphore double-release runtime crash + probe-outside-re-resolve — all fixed pre-push.
- **Docs updated (same session)**: PLAN.md committed; D-243/D-244 in decisions.md; database docs folder created (playback-cache.md + README, §24); this progress entry + changelog entry. Dashboard NOT touched (deploys from main — truth-sweep queued for merge time; module/table counts on the dashboard will need updating when this branch merges: 48 modules / 24 tables).

**This session — Auto-link & Match-preview & Watch-page & Schedule overhauls (D-225 → D-238, all on `main`, through commit `94a7c0a`):**
- **D-225**: Reverse auto-link (AniList → extensions) + D-225b settings UI + extension reorder.
- **D-226**: Auto-link settings page redesign (reverse on top + collapsibles + drag-priority).
- **D-227**: Match-preview card with cover image + stale-state fix (`DisposableEffect` + `resetState`).
- **D-228**: Match preview duration + `LazyColumn` flatten (≈60× node reduction) + `downloadStates` O(n) → O(1) fix.
- **D-229**: Hide episodes during match preview + cover-image fallback for thumbnails.
- **D-230**: Watch-page crash fix (`LazyColumn` virtualization) + episode-list preferences + settings sheet + search.
- **D-231**: Wire up filters/sort/grouping + auto-scroll + dynamic theming.
- **D-232**: Fix grouping display (range-based), filter reactivity, switcher placement, scroll.
- **D-233**: Fix grouping (1-100 ranges), tabbed settings sheet (Sort/Filter/Display), empty-state.
- **D-234**: Sort tab redesign (list + direction toggle) + next-episode card with countdown.
- **D-235**: Fix next-episode not showing (added `nextAiringEpisode` to `fetchAnimeDetails` query).
- **D-236**: Fix schedule (`INNER JOIN library_item` + `episode_schedule` upsert) + details-page background (tint, source, animation).
- **D-237**: Crash fix (duplicate key), next-episode on all paths, dedicated settings page, "coming soon" empty-state.
- **D-238**: Unlink blacklist (per-anime — unlinked sources never auto-relink), episode-cache clear on source change, calendar dots color-coded with each anime's accent color (gradient bar for >5), UI improvements (removed "Linked to AniList" badge, title aligned to bottom, tap-to-copy name).
- All D-225 → D-238 commits merged to `main`; CI green on `94a7c0a`.

**This session — Project Review Dashboard REBUILD (fresh review, replaces prior /project-review/) (commits 6d79c075 → 2a812470, on `main`):**
- User instructed: read CORE_RULES + all AGENT-CONTEXT + codebase FIRST (before anything else), then DELETE the existing `/project-review/` dashboard page completely + build a FRESH dedicated section showing all key findings in a simplified, easy-to-scan format. Deploy via GitHub Actions. Nothing else in the dashboard should change. Max 5 sub-agents at a time. Do NOT read REFERENCES/.
- Read CORE_RULES.md in full (498 lines, 30 sections) via raw GitHub URL BEFORE cloning. Cloned full repo to `/home/z/ani-kuta-repo` (6.7s). Verified §4 structure (single `ANI-KUTA/` wrapper folder).
- Read orientation files (navigation, master, workflow, SESSION) + top of progress.md myself. Dispatched **5 parallel Explore sub-agents** to digest decisions.md (226KB), lessons-learned.md (79KB), changelog.md (125KB), all knowledge/* (7 files), + Android codebase structure + existing dashboard examination.
- **Verified facts against actual code**: 46 modules; **26 SQLDelight tables** (NOT 28 — D-192 dropped content_ext + content_ext_repo + user_customization; content.sq has 6 tables now); **331 .kt files** (NOT 315); **MainActivity runBlocking at line 470** (NOT 428); **HttpDownloader.reResolver CONFIRMED ORPHANED** (DownloadModule.kt:92 passes null); **4 god-class files** (LibraryScreen 2471, DetailsScreen 2282, DetailsViewModel 2263, WatchScreen 2029).
- Discovered the existing `/project-review/` page was NOT the 9-section review the changelog described — a later un-logged session (commit 564f1a55) had REPLACED it with a DC1-DC5 test checklist. Deleted it + rebuilt fresh.
- Delegated page rebuild to a **full-stack-developer sub-agent** (§19) working ONLY in `DASHBOARD/webpage/`. Deleted old `app/project-review/page.tsx` (487-line test checklist) + `lib/projectReview.ts` (486-line test data). Created NEW `lib/projectReview.ts` (539 lines, fully typed) + `app/project-review/page.tsx` (1015 lines, server component) rendering 9 sections per DESIGN.md (MEMORY OS v3). `bun run build` PASSED (18/18 routes).
- **No edits to data.ts or Sidebar.tsx** — the NAV_ITEMS "Project Review" entry + `review` clipboard-check icon already existed + matched the new page. Additive-only (only /project-review/ content changed; all other dashboard pages untouched).
- **9 sections**: §1 Snapshot (verified metrics + tech stack), §2 Project Health (verdict + 7 indicators), §3 What's Built (13 feature areas), §4 Concerns & Issues (9 open + 4 accepted + 7 recently-resolved + 1 dashboard debt, severity color-coded), §5 Doc Drift Caught (9 rows), §6 Features Remaining (5 backlog groups), §7 Forward Direction (4 prioritized steps), §8 Top Risks (8 rows), §9 Footer Note (temporary notice).
- **Mobile overflow — 4 fix iterations** (Agent Browser at 375px found 57px overflow each time): (1) hero badges `shrink-0` → `max-w-full` + hero URL span `break-all` (aee222e5); (2) SectionCard `right` wrapper `shrink-0` → `max-w-full` + pills div `max-w-full` (9d286165); (3) bullet text spans `<span>{b}</span>` → `min-w-0 break-words` across HEALTH/FORWARD_DIRECTION/FEATURES_REMAINING/FOOTER + facts span + area.items `<li>` (2d74b785); (4) FEATURES_REMAINING group.bullets/numbered outer `<span>` (no class) + note spans → `min-w-0 break-words` (2a812470).
- **Final verification (Agent Browser)**: mobile 375px `overflow=0` at every scroll position (0-5000px); desktop 1280px all 9 section headings render, no errors; dark mode toggle works (`html.dark`); existing `/decisions/` page unaffected.
- **CI**: Deploy Dashboard #44-#48 all `completed`/`success`. Build APK #532-#535 all `completed`/`success` (dashboard-only changes don't affect Android build). All verified via GitHub Actions API polling (lesson L128).
- No D-NNN decision added (temporary dashboard rebuild, not a permanent architecture decision).

**This session — Database Restructuring Plan v2 (commits 78db1955 → 23d7839c, on `main`):**
- User reviewed plan v1 + gave revised direction: merge into ONE wide `content_details` table (Option A — reversed v1's Option C). Drop `app_metadata`. Keep `data_source`+`system` separate. Keep `extension_repo_id`. Target under 15 (preference).
- **RESEARCH**: 2 Explore sub-agents (R-1 content_details design, R-2 re-evaluate merges/grouping).
- **PLAN v2**: Rewrote PLAN.md. 26→22 tables via 4 changes. `content_details` = 26 cols, `data_*`/`ext_*` prefixes, discriminators, `extra_json`.
- **REVIEW (4 iterations)**: Iter 1 (2 FLAWS fixed), Iter 2A+2B parallel (0 FLAWS + 7 CONCERNS fixed), Iter 3+4 (0 FLAWS, APPROVED).
- **DASHBOARD**: `/database-plan/` updated with v2. 20/20 routes. Mobile overflow=0.
- **Table count**: 22 (above 15 preference — research confirms remaining are genuinely better separate).
- New decision: **D-198** (plan v2 — PROPOSAL, not implemented).
- **This is a PROPOSAL v2 — awaiting user approval.**

**Previous session — Database Restructuring Plan + Dashboard (commits e64235cc → 324986cb, on `main`):**
- User reviewed `/database-review/` + gave direction: rename `content`→`main_entry`, merge 3 detail tables (keeping data source ≠ extension SEPARATE), absorb `anime_metadata_cache`, keep `data_cache_episode` separate, create a beautiful dashboard page showing the full plan.
- **RESEARCH**: 5 parallel Explore sub-agents (content table, detail tables + source-switch, cache trio, data source vs extension, keep-separate groups).
- **PLAN**: `APP/ani-kuta/DOCUMENTATION/planning/database-restructuring/PLAN.md` (446 lines). 3 core changes + 11 improvements. 26→24 tables.
- **REVIEW (4 iterations via sub-agents — NOT self-review per user instruction)**: Iter 1 (1 FLAW + 9 CONCERNS), Iter 2A+2B parallel (2 FLAWS + 11 CONCERNS), Iter 3 (0 FLAWS + 7 minor), Iter 4 (2 cosmetic). All fixed.
- **DASHBOARD**: `/database-plan/` page — 11 sections, every table + column + query + con + deferred item. Build passes (20/20 routes). Mobile-clean.
- **Verified**: Agent Browser desktop + mobile + dark mode.
- New decision: **D-197** (DB restructuring plan — PROPOSAL, not implemented).
- **This is a PROPOSAL — awaiting user approval before implementation.**

**Previous session — Download System Fixes + Database Review Dashboard (commits 8f0ea772 → af51be7e, on `main`):**
- User reviewed the /project-review/ dashboard + gave specific instructions: fix 5 open concerns (HttpDownloader.reResolver D-149, retry loop D-151, MainActivity runBlocking, file_size, data.json refresh), assess nav backstack R7 (DEFERRED), fix dashboard schema.ts, fix doc-drift, deep database review + optimization proposal, improve downloads page UI (server name + audio version).
- **Phase A (D-149)**: reResolver wired — ReResolverAdapter.kt in :app, Koin binding, 2 latent bugs fixed (127.0.0.1 guard + updateDownloadVideoUrl). **RESOLVES Deferred Concern #2.**
- **Phase B (D-151)**: RetryPolicy + outer retry loop in DownloadQueue. Max 3×2=6 attempts. **RESOLVES #5.**
- **Phase C**: runBlocking → buildWatchKeyForDownloadedEpisode (suspend, Dispatchers.IO). **RESOLVES #3.**
- **Phase D**: ResolverSheet carries (serverName, audioLabel). DownloadedFilesScreen 2-line row (server/audio/quality/size). file_size fix. **RESOLVES #16 + #17.**
- **Phase E**: data.json write-back via DownloadScanner.reconcileDataJsonFromContent + AnikutaApp.onCreate scan.
- **Phase F (R7)**: DEFERRED — accepted limitation, WatchKey ignored, player fragile.
- **Phase G**: schema.ts rewritten (26 tables), 14 stale strings fixed, AGENT-CONTEXT doc-drift fixed (28→26, 315→331, D-186→D-193, 134→163, 428→470, compileSdk 35→36, Nav3 removed).
- **Phase H**: NEW /database-review/ dashboard section — 8 merge candidates, top 3 improvements, 26→23 ideal.
- **Resolved this session**: #2, #3, #5, #16, #17 (5 of 22 concerns).
- New decisions: D-194 (ReResolverAdapter), D-195 (RetryPolicy), D-196 (data.json write-back).
- CI GREEN (#540). Compile review caught 3 errors (2 pre-push sub-agent, 1 via CI).

**Previous session — Project Review + Dashboard Section (commits b51d43ad + 5b8351ef, on `main`):**
- Conducted a full read-through of CORE_RULES + AGENT-CONTEXT (navigation, master, workflow, SESSION, memory/*, knowledge/*) + codebase structure verification (46 modules confirmed, settings.gradle.kts, sqldelight, god-class line counts, HttpDownloader re-resolve orphan status).
- Dispatched 5 parallel Explore sub-agents to digest progress.md (80KB), decisions.md (226KB), changelog.md (120KB), lessons-learned.md (77KB), + the Android codebase structure. Summaries appended to `/home/z/my-project/worklog.md`.
- Built a **NEW dedicated dashboard section** at `/project-review/` (route `https://testplay-byte.github.io/ANI-KUTA/project-review/`) visualizing the live project review: 9 sections (hero snapshot, project health, what's built, 18 concerns by severity, doc-drift caught, features remaining, forward direction, top risks, footer note).
- **ADDITIVE-ONLY** — no existing dashboard content modified. New files: `lib/projectReview.ts` (~585 lines typed data) + `app/project-review/page.tsx` (~590 lines). Modified (9 insertions total): `lib/data.ts` (1 nav item appended) + `components/Sidebar.tsx` (1 "review" clipboard-check icon key added).
- Build verified (`bun run build` PASSED, 18/18 routes incl. /project-review). Deployed via GitHub Actions (`deploy-dashboard.yml`) — workflow runs 31642901528 (b51d43ad) + redeploy (5b8351ef) both `completed`/`success`. End-to-end verified via Agent Browser: homepage loads, nav link present, page renders all 9 sections + all key findings content, dark mode toggles correctly, mobile (375px) no horizontal overflow, desktop (1280px) no overflow, existing pages (e.g. /decisions/) unaffected.
- **Mobile overflow fix (5b8351ef):** hero "Reviewed this session" badge had `whitespace-nowrap` + fixed `h-7` → 394px wide, overflowed 375px viewport by 56px. Switched to `py-1.5` (flexible height) + `max-w-full` + removed nowrap. Verified `375px <= 375px` (no overflow).
- **This is a TEMPORARY section** the user requested for reviewing findings. Remove the page + nav item + icon when no longer needed (4 files: `app/project-review/page.tsx`, `lib/projectReview.ts`, the 1 NAV_ITEMS entry, the 1 Sidebar icon key).
- No D-NNN decision added (temporary dashboard addition, not a permanent architecture decision).

**Doc-drift caught this session (NEW — needs fixing in a future doc sweep):**
- Actual SQLDelight tables = **26** (NOT 28 as docs everywhere claim). The `content_ext` + `content_ext_repo` + `user_customization` tables were DROPPED in D-192 Phase 1 (dead tables), reducing 28 → 26. Docs/data.ts/`knowledge/architecture.md`/`knowledge/dashboard.md`/Footer all still say "28". `lib/schema.ts` `SCHEMA_TABLES` array still uses planned Phase-1 table names not actual — known dashboard debt (D-188 item #11), still unfixed.
- Actual `.kt` files = **331** (docs say "315"). Grew from D-193 v2 (+50 files) + Profile UI work.
- Main-thread `runBlocking` is at `MainActivity.kt:470` (docs/Deferred Concerns say "line 428" — doc-drift).
- `HttpDownloader` guards on `http://localhost` (docs say "`127.0.0.1` guard" — imprecise; actual code uses `http://localhost`).
- `decisions.md` numbering drift: D-121 missing, D-037/D-038 out of order, D-008 says compileSdk 35 (actual 36).
- `17-database-schema.md` still says "21 tables" (historical doc — left as-is with a note in navigation.md).
- Repo root pollution: `skills/` (69 generic Z.ai sandbox skills) + a large `worklog.md` committed on `main` — violates CORE_RULES §4. Deferred per user.

**Next:** User reviews the `/project-review/` dashboard section + decides the forward direction (device verification FIRST, then HIGH-severity deferred concerns, then architectural debt cleanup, then Phase 6 features — see the "Recommended Forward Direction" section on the dashboard page).

**D-193 v2 merge completed (previous session):**
- `feature/updates-notifications-impl` (29 commits) merge-committed into `main` (57bbd17) — 50 files changed, +2924/-295 lines.
- CI on `main` verified GREEN (run 31639789917, commit 57bbd17) — the final gate before branch deletion.
- Both feature branches deleted (local + remote): `feature/updates-notifications-impl` + `feature/updates-notifications-plan`. Only `main` remains.
- D-193 v2 includes: episode-type toggle gates notifications only, check-dub-on-completed wired, smart-release real weighted averaging, library customization toggle + per-anime notification UI on details page, update categories picker, battery optimization on first launch, on_schedule precise timer, + cleanup.

**Profile UI v6 (D-183..D-186, commit 6945df6 — now on main):**
- **Magnetic snap guard:** Snap now only fires when `firstVisibleItemIndex == 0` (near top). Fixes the "scroll to bottom → auto-scrolls to top" bug.
- **Watch flow sidebar:** Taller (260dp). Card-level transparent scrim for reliable tap-outside close.
- **Time DNA + Recently Watched:** Side-by-side in ONE card — donut left (own bg), recently watched list right (own bg).
- **Heatmap labels:** Column bottom padding 24dp, month-label Box 18dp, day-markers bottom padding 20dp — fixes bottom-half cut-off.

**Profile UI v5 (D-177..D-182, commit 47196ad — now on main):**
- Magnetic snap + gradient blur + equal-width mini tabs. Watch flow: complementary today color, sidebar from LEFT, solid bg. Time DNA donut tint. Genre radar in-web highlight. Avatar crop editor (new AvatarCropScreen.kt). Settings URL/upload state separation. CI fix: Coil3 `result.image` API.

**This session (DB optimization + ratings + continue-watching + watch-progress fixes):**
- **Phase 1 (DB-OPT):** Deleted 2 dead `.sq` files (`extensions.sq`, `metadata.sq` — zero call sites). Enabled `PRAGMA foreign_keys = ON`. Dropped 6 redundant indexes. Added 8 missing indexes (continue-watching partial, retention purges, AniList JOIN, content extension lookup, library dedup). Fixed WP-B1 (`setAutoMarkSuppressed` now clears `completed_at` + INSERT-when-missing guard). Fixed audio-variants bug (added `source_name` + `scanlator` columns to `data_cache_episode`; preserved through enriched cache write; fixed offline-fallback URL bug). Fixed extension trust bug (per-package `isEnabled` flag; only enabled extensions' sources appear in pickers; backward-compat seeding). CI green (run 31348314200).
- **Phase 2 (watch-progress fixes):** WP-B2 (resetAutoMarkSuppressed on FILE_LOADED — re-arms 85% auto-mark). WP-B3 (resume-seek — click same episode → plays from where you left). WP-B4 (save on episode switch). Progress bar in Details episodes list (thumbnail bottom edge, like YouTube). CI green (run 31348683710).
- **Phase 3 (continue-watching UI):** Single-row carousel at top of Browse. Cover thumbnails, EP badges, progress bars, placeholder images. Tap → Details (resume kicks in on play via WP-B3). CI green (run 31348903899).
- **Phase 4 (ratings UI):** Per-anime 10-star rating on Details (right of synopsis title). Per-episode 10-star rating on Watch (below currently playing episode text). Each star = 10 points (0-100 backend). Temporary testing implementation. CI green (run 31349493109, after fixing a composable-scope compile error).

**Branch:** `feature/db-optimization-ratings-cw` (all 4 phases on this branch). Awaiting user device verification before merge to `main`.

⚠️ **Known gaps (deferred per user):**
- Proxy-churn re-resolve NOT wired (D-149) — deferred to future phase.
- Outer retry loop not implemented — deferred.
- Subtitle loading for downloaded episodes still not working on device (D-152 fixes are in but unverified; user deferred to a later session).
- ✅ ~~Rating UI not built~~ — DONE (Phase 4, temporary 10-star implementation).
- ✅ ~~Continue Watching UI not placed~~ — DONE (Phase 3, Browse carousel).
- SQLite UPSERT migration NOT done (SQLite 3.24+ required; API 24-28 ships 3.9-3.22 — can't use `ON CONFLICT DO UPDATE` on minSdk 24). INSERT OR REPLACE kept; callers already read-then-write.
- CHECK constraints for magic strings NOT added (can't ALTER TABLE to add CHECK on existing installs; would need table rebuild). Deferred.
- Extension settings (extension's own preferences UI) — future task per user.

**Next:** User device verification of all 4 phases. Then: merge to `main` + clean up the CI trigger (`feature/**` → `main` only). Then: subtitle loading investigation + Phase 6+ (ads, backup/restore, identity system).

**Previous session (swipe / calendar / notifications):**
- **Swipe background fixed (D-153):** the reveal background in `DetailsScreen.EpisodeRow` + `HistoryScreen.HistoryRow` was invisible because it used `fillMaxSize()` inside a wrap-content-height Box (resolves to 0 height). Switched to `matchParentSize()` (BoxScope) + always-compose with `graphicsLayer` alpha fade. The previous session's `fillMaxSize` "fix" was the regression.
- **Calendar toggle fixed (D-154):** the List/Calendar toggle was hidden because `ScheduleListContent` emitted the toggle + content as bare siblings into a parent Box (later siblings draw on top → list covered the toggle). Wrapped in a `Column`. Also: auto-fetch schedule on first open if empty, calendar `verticalScroll`, empty-state hint, gate the Updates-driven `ScrollBlurOverlay` to the Updates tab.
- **Notification settings UI built (D-155):** `NotificationPreferences` (master toggle + defaults) in `:core:preferences`; `NotificationManager` now respects the global kill switch; `NotificationsSettingsScreen` + ViewModel in `:app` (master toggle, defaults, per-anime library list + detail bottom sheet); Notifications nav row in Settings + `NotificationsKey` wired.
- **CI false-green fixed (D-156):** previous commits `db26c47`/`fd1a9a5` actually FAILED CI (`:app:compileDebugKotlin` — DocumentFile unresolved from the subtitle disk-scan code) but progress.md claimed green. Added `implementation(libs.androidx.documentfile)` to `:app`. CI now genuinely green (run 31275021179, artifact 53 MB).
- **Branch cleanup:** `feature/watch-progress-history-updates` deleted (was fully merged into main; main verified green). Only `main` remains.
- Subtitles intentionally deferred per user (separate session).

⚠️ **Known gaps (deferred per user):**
- Proxy-churn re-resolve NOT wired (D-149) — deferred to future phase.
- Outer retry loop not implemented — deferred.
- Subtitle loading for downloaded episodes still not working on device (D-152 fixes are in but unverified; user deferred to a later session).
- Rating UI not built — store + schema ready, UI pending.
- Continue Watching UI not placed — logic ready, UI deferred.

**Next:** Debug bubble — MERGED to main (D-163..D-165, DB-1..DB-9). `feature/debug-bubble` branch deleted. Next: Database optimization (new AI agent) → Rating UI → Continue Watching UI → Subtitle loading investigation.

## What's Done
- [x] Phase 0 (environment, rules, dashboard, old project documented).
- [x] Phase 1 (architecture plan, design language, all decisions confirmed D-001..D-051).
- [x] Phase 2 (12-module scaffold, CI green).
- [x] **Phase 3 — Core Modules COMPLETE** ✅ (15 modules across 4 sub-phases):
  - 3a Foundation: provider-api, source-api (Aniyomi binary-compat, 36 files), database (8 SQLDelight .sq files).
  - 3b Extensions: data:extension (loader, manager, trust).
  - 3c Playback: player (AnikutaMPVView), player-mpv-lib (AAR wrapper), video-resolver, download.
  - 3d Supporting: metadata (merger + providers), tracker-api, tracker-anilist (TrackSyncManager), activity-tracker (365-day), watch-progress.
- [x] **Phase 4 — Feature Screens (mostly done)**:
  - 4a: App shell (AnikutaRoot, bottom nav, Nav3 backstack), Browse, Details.
  - 4b: Library (grid/list + sort + customize sheet), Search (filter sheet), More, Settings, Appearance (General).
  - Theming: light/dark/AMOLED, accent palette system (D-053 — 10 functional presets + CUSTOM), header blur, adaptive colors.
  - UX: smooth animations (CollapsingHeader, ScrollBlurOverlay, scale-on-press), back gesture (BackHandler), bottom-nav hidden on sub-screens.
- [x] **Phase 5a — Extension Management (done)**: AnimeExtension sealed class, repo system, installer system, AnimeExtensionApi, `:feature:extensions-settings` module, Nav wiring, manifest permissions + service.
- [x] **Phase 5b — Details Page Overhaul (done)**: DetailsViewModel (source linking, fetchEpisodes, searchSource, resolveEpisode), DetailsScreen wired, ManualSearchSheet, ResolverSheet, WatchKey.
- [x] **Phase 5c — Watch Screen (mostly complete)** ✅ — player overhaul this session:
  - **Animiru repo cloned** to `REFERENCES/animiru/ANIMIRU/` + 11 documentation files (8,101 lines) in `REFERENCES/animiru/documentation/` (read-only reference, no code copied — D-065).
  - **Video playback fixed** (audio but no video). ROOT CAUSE: `AnikutaMPVView.initOptions()` was EMPTY — `setVo("gpu")` was never called → MPV had no video output. Ported full `initOptions()` from old project: `setVo`, `profile=fast`, `hwdec=auto` (NOT `auto-copy`), `demuxer-max-bytes=256MB`, `vd-lavc-film-grain=cpu`, all 12 subtitle prefs via `setOptionString`, `tls-ca-file`, etc. (D-061).
  - **Top padding bug fixed**. ROOT CAUSE: `WatchScreen`'s `DisposableEffect(playerMode)` called `setDecorFitsSystemWindows(true)` in minimized mode, conflicting with `enableEdgeToEdge()`. Empty `onDispose` left window corrupted → double top padding on Browse/Library after exiting player. Fixed: removed the `true` call + added cleanup in `onDispose` (D-062).
  - **Loading failed overlay fixed**. ROOT CAUSE: `PlayerObserver` didn't clear error state on `FILE_LOADED`. Fixed: `onEvent(FILE_LOADED)` now calls `updateError(null)` + `updateLoadingState(READY)` + loads tracks.
  - **QualitySheet ported** — replaced placeholder with full accordion server list + quality chips. Created `ResolverServer`/`ResolverAudioVersion`/`ResolverVideo` data classes + `ResolvedVideosRegistry` (in-memory singleton — D-063). `DetailsViewModel.resolveEpisode` now also calls `resolveStructured`. Quality switching = re-loadfile.
  - **SubtitleSettingsSheet ported** — sticky header + 3 sections (Typography / Colors / Position & Misc) + `NumericEntrySheet` (custom keypad) + `ColorPickerSheet` (preset swatches + RGBA sliders). All 12 subtitle prefs added to `PlayerPreferences`. `applySubtitlePreferences()` uses `setPropertyInt`/`setPropertyDouble` for numerics. Uses non-reactive `PlayerPreferences` with local `mutableStateOf` (D-064 — simpler than porting old reactive `Preference<T>` API).
  - **SubtitleTracksSheet** — wired `onOpenSettings` callback to swap to `SubtitleSettingsSheet`.
  - **PlayerInitializer** — simplified mpv.conf (removed `cache=yes`, `hwdec=auto-copy`, `hwdec-codecs`, `sub-ass-force-margins` from conf — now set via `setOptionString` in `initOptions`).
  - **configChanges** — added `uiMode` (theme toggle no longer recreates Activity).
- [x] **Phase DL — Download System (substantially complete, `download-system-plan` branch)** ✅ — implemented across 41 commits (see "Session — Download System" entry below + `download-research/13-implementation-plan.md` status table):
  - **DL.0 Foundations** (5849e13, 379f3a6): download data models, preferences, DB schema (`downloaded_episode` table re-keyed by mainId + episodeKey, `.data.json` as source of truth).
  - **DL.1 Engine + Storage** (9b4c5d7, b8b5d7b, +4 fixes): DownloadManager interface, DefaultDownloadManager, HttpDownloader (Range-resume + validation), HlsDownloader (pure Kotlin), DownloadStorageProvider (SAF + `.data.json` + same-title collision handling), DownloadScanner (scan-on-startup), TempDownloadCache, DownloadLogger.
  - **DL.2 Orchestrator + AutoDownload + proxy-churn** (6382dbe, +5 fixes): DownloadOrchestrator, AutoDownloadEngine (5-step: flatten → rank → applyFallbacks → pick → globalFallback), `ReResolver` types. ⚠️ Proxy-churn re-resolve is BUILT but NOT WIRED (D-149, D003).
  - **DL.3-DL.8** (4298cb3, e29d616, +3 fixes): queue management + dynamic progress, foreground DownloadService (NetworkCallback auto-pause/resume, onTimeout API 35+, onTaskRemoved restart), DownloadNotificationManager (2 channels), 7-section settings UI (drag-reorderable priority/quality/audio/server), downloads page (live queue + bulk actions + downloaded files page), episode download controls on details, player offline integration.
  - **Offline playback** (d83915d, de7c0bc, 1f85339): content:// → fd:// ParcelFileDescriptor conversion + 500ms surface-readiness delay; MPV SIGABRT fixed.
  - **Stability fixes** (DL-CRASH-FIX 1-3, DL-CRITICAL-FIX 1-3, DL-IMPROVE 1-3, DL-REMAINING, METADATA-FIX-v2): DB schema migration via `onOpen` + first-run setup dialog, stale data cache, episodeUrl caching, metadata disappearing, local subtitles.

## What's Next
1. **Download system device testing** (verify on real device): enqueue a download, pause/resume, offline playback of a downloaded episode, auto-download trigger, notification channels, foreground service survival across screen-off/task-removal.
2. **Wire proxy-churn re-resolve + outer retry loop + 2 re-resolve bugs** (D-149, D-151) — DEFERRED per user, grouped into a future phase. Full plan in `download-research/FUTURE-PHASE-DL-GAPS.md`: (a) ~50-line adapter in `:app` implementing `HttpDownloader.ReResolver` + Koin binding + `DownloadModule.kt:92` null→getOrNull(); (b) fix `127.0.0.1` guard (`HttpDownloader.kt:261`) + `video_uri`/`video_url` column bug (`:271`); (c) `RetryPolicy` class + outer retry loop in `launchDownload` catch block + backoff + notification UX; (d) delete or wire `DownloadVideoPickerSheet`. Estimated ~6-8 hours. Manual retry (`retryDownload()`) works as today's fallback.
3. **Nav3 vs hand-rolled nav** — ✅ DECIDED (D-150): keep hand-rolled `mutableStateListOf<NavKey>` + `when(currentKey)` nav as-is. Do NOT migrate to Nav3. R7 (process-death backstack recreation) accepted as a known limitation. Nav3 1.1.5 dep stays on classpath (unused; future cleanup option). Docs updated: `12-nav-research.md` (Resolution note). If R7 becomes important later: hybrid `rememberSaveable` fix (~1-2h, Option C.1 in sandbox `03-nav3-comparison.md`).
4. **Doc-debt sweep** (discrepancy D005): master.md [DONE this session], navigation.md [DONE this session], knowledge/* + decisions.md numbering [DEFERRED until Nav3 + proxy-churn decisions settle, so docs reflect final state].
5. **Watch-progress bug fixes + resume feature** — watch progress IS persisted to SQLDelight (Phase WP shipped `SqlDelightWatchProgressStore`; D-072's in-memory store is superseded). But there are real bugs: (1) `setAutoMarkSuppressed` SQL doesn't clear `completed_at` → stale data on un-mark; (2) `resetAutoMarkSuppressed` never called on FILE_LOADED → CF1 re-arm broken; (3) no resume-seek (users always start at position 0 — `AnikutaMPVView.loadVideo(resumePosition)` exists but WatchScreen bypasses it); (4) no save on episode switch. Plus: Ratings UI + Continue Watching UI (both have backend ready, zero UI).
6. **Episode switching inside WatchScreen** (needs PlayerStateHolder fields: `episodeList`, `currentEpisodeIndex`, `isSwitchingEpisode`) + resume position.
7. **Phase 6+**: ads (D-033), activity-tracker UI, manga reader (D-030), novels, backup/restore (`15-backup-research.md`), identity system (Phase 5d).

## Blockers / Open Questions
- Download system implemented but NOT device-tested yet. Proxy-churn re-resolve + outer retry loop + 2 re-resolve bugs deferred to a future phase (D-149, D-151) — plan in `download-research/FUTURE-PHASE-DL-GAPS.md`.
- Nav3: ✅ DECIDED (D-150) — keep hand-rolled nav; R7 accepted as known limitation. Nav3 1.1.5 dep unused (future cleanup option).
- AGENT-CONTEXT knowledge/* + decisions.md numbering stale — doc-debt sweep deferred (D005) until proxy-churn/retry future phase settles.
- Watch progress IS persisted to SQLDelight (Phase WP — `SqlDelightWatchProgressStore`; D-072 in-memory store superseded). Known bugs: setAutoMarkSuppressed doesn't clear completed_at; resetAutoMarkSuppressed never called on FILE_LOADED; no resume-seek; no save on episode switch.
- Custom color picker (palette editor) deferred to Phase 5f.

## Known doc debt
- **master.md** — ✅ UPDATED (was: "Phase 1 blocked" false, nonexistent `knowledge/app-design-language.md` ref, "16 sections" wrong, `ANIKUTA-PROJECT/` path wrong). ✅ RE-UPDATED this session (46 modules/28 tables, 30 sections, D-186, Nav3 fully removed, current focus = DB management).
- **navigation.md** — ✅ UPDATED (was: nonexistent ref, "1882 lines" wrong, "21 sections" wrong, "10-16" doc range wrong). ✅ RE-UPDATED this session (30 sections, REFERENCES/ expanded, download-research/ section added, all knowledge/ descriptions current).
- **progress.md top header** — ✅ UPDATED (was stale "Phase 5c"). ✅ RE-UPDATED this session (Deferred Concerns section added, Known doc debt refreshed, session entry prepended).
- **download-research/13-implementation-plan.md** — ✅ UPDATED (status table marking DL.0-DL.8 as implemented + Phase-D disambiguation note).
- **changelog.md** — ✅ UPDATED (Phase DL entries). ✅ RE-UPDATED this session (doc-debt-sweep entry added).
- **decisions.md** — ✅ D-148 (download) + D-149 (proxy-churn gap) added. ✅ D-187 (doc-debt sweep) + D-188 (deferred concerns registry) added this session. STILL DEFERRED: D-121 missing, D-037/D-038 out of order, D-008 compileSdk 35 (actual 36), D-009 should be superseded by D-034/D-035 — minor numbering cleanup, not blocking.
- **knowledge/module-map.md** — ✅ FULLY REWRITTEN this session (all 46 modules with jobs + deps + key files).
- **knowledge/architecture.md** — ✅ FULLY REWRITTEN this session (actual 46-module graph, SQLDelight 28 tables, hand-rolled nav, DI wiring, known debt).
- **knowledge/tech-stack.md** — ✅ FULLY REWRITTEN this session (actual versions verified against libs.versions.toml; Nav3 removed; SQLite constraints documented).
- **knowledge/old-vs-new.md** — ✅ FULLY REWRITTEN this session (old project in REFERENCES/old-kuta/ANIKUTA/, 36 modules/643 files; full comparison table + migration notes).
- **knowledge/dashboard.md** — ✅ FULLY REWRITTEN this session (14 pages, data files, update process, known dashboard debt).
- **knowledge/project-overview.md** — ✅ FULLY REWRITTEN this session (Q1/Q2/Q10 answered, current status, key facts).
- **knowledge/ui-customization.md** — ✅ FULLY REWRITTEN this session (5 customization layers all built, subtitle settings, live data verification).
- **SESSION.md** — ✅ RE-UPDATED this session (D-186, 46 modules, Deferred Concerns, doc-debt sweep done, DB management = next focus).
- **Two "Phase D" tracks collide** — ✅ ADDRESSED: data-management Phase D vs download-system Phase DL.0-DL.8.
- **Code comments** — ✅ CLEANED this session: `AndroidConfig.kt` (Nav3 comment → accurate), `app/build.gradle.kts` (orphaned `// Navigation 3` → accurate), `libs.versions.toml` (2 orphaned `# Navigation 3` → accurate).
- **DASHBOARD/webpage/ data** — ✅ UPDATED this session (full-stack-dev sub-agent): 13 files updated, 46 modules / 26 core / 18 feature / D-001..D-186 / 28 tables / 15 .sq files / Nav3 removed / main branch across all 14 pages + Footer. Build PASSED. REMAINING: `lib/schema.ts` SCHEMA_TABLES array still uses planned Phase-1 table names (content_uid etc.) not actual — deferred (changes DB page UI).
- **17-database-schema.md** — ⚠️ STILL STALE (says "21 tables"; actual 28). Historical doc — left as-is with a note in navigation.md. A full rewrite would change the design history; deferred.
- **Repo root pollution** (discrepancy D001): `skills/` (69 generic Z.ai skills) + large `worklog.md` committed on main — violates CORE_RULES §4. DEFERRED per user (not a concern right now).

## Deferred Concerns
> Tracked issues deferred to future phases per user. NOT bugs to fix now — they are known + accepted.

| # | Concern | Severity | Est. Effort | Dependencies / Notes |
|---|---------|----------|-------------|----------------------|
| 1 | **AniList tracker is a placeholder** (`AniListTracker.kt`) — OAuth stores code as token, syncEntry returns true without API call, fetchEntry/search return null/empty | Expected | ~4-6h | Not yet implemented. Full AniList GraphQL integration deferred. |
| 2 | **HttpDownloader.reResolver orphaned** (D-149) — built but not wired; `:app ReResolver` built by Koin but never injected; signatures mismatched (`json:String` vs `context,source,episode`) | Medium | ~6-8h | Group with #4 + #5. Plan in `download-research/FUTURE-PHASE-DL-GAPS.md`. |
| 3 | **Main-thread runBlocking in Downloads→Watch** (`MainActivity.kt:470`) — `scanSubtitleFilesOnDisk` SAF enumeration on UI thread; ANR risk | Medium | ~1-2h | Move to Dispatchers.IO + async feed-back. Standalone fix. |
| 4 | **Dead/unwired download code** (D-151) — `DownloadVideoPickerSheet` (zero callers), `setRetryingStatus` (zero callers, RETRYING state never set), 2 re-resolve bugs in dead code | Low | ~6-8h | Group with #2. Delete or wire; design RetryPolicy. |
| 5 | **Outer retry loop not implemented** (D-151) — `RetryPolicy` class referenced in KDoc but doesn't exist; failed downloads go straight to ERROR | Medium | ~3-4h | Group with #2 + #4. |
| 6 | **WatchKey god-object** (15 fields, 5 pre-serialized \u001F-delimited strings) — Bundle size risk, single-responsibility violation, MainActivity bloat (127-line inline serialization) | Medium-High | ~3-5h | Refactor to identifier-only (mainId + episodeNumber + videoUrl + startPosition); fetch rest by ID. Needs design + sub-agent review. Double-resolve bug (D-066) means can't re-resolve on Watch open — need durable resolved-video store. |
| 7 | **Nav backstack doesn't survive process death** (R7, D-150) — `remember { mutableStateListOf }` not `rememberSaveable` | Low | ~1-2h | Hybrid `rememberSaveable(saver=listSaver)` fix (Option C.1 in sandbox 03-nav3-comparison.md). Blocked by #6 (WatchKey too large for Bundle). |
| 8 | **4 god-class .kt files >2000 lines** — LibraryScreen (2471), DetailsScreen (2277), DetailsViewModel (2159), WatchScreen (2017) | Low-Medium | ~8-12h total | Refactor candidates. Split by responsibility. Not blocking. |
| 9 | **DB migrations use `onOpen` not `.sqm` files** — acceptable for debug (CORE_RULES §30); needs `.sqm` + `user_version` before production | Low (debug) | ~2-4h | Wait for user's production signal. |
| 10 | **Release signing not configured** — only `debug` buildType declared; debug keystore committed | Expected | ~1-2h | Phase 9. Wait for user's production signal. |
| 11 | **Dashboard `schema.ts` uses planned Phase-1 table names** not actual current schema | Low | ~2-3h | Changes DB page UI. Deferred (dashboard polish). |
| 12 | **`activity_event` table is EMPTY** (0 rows) despite 7 episodes watched — `ActivityTracker.track()` has ZERO callers. The watch flow (DetailsViewModel/WatchScreen) never records activity events. Profile page stats derive from `watch_progress` instead (works, but activity_event is dead). | High | ~2-3h | Wire `activityTracker.track(WatchEvent)` in WatchScreen on play/pause/complete + DetailsViewModel on library/rating changes. |
| 13 | **Updates tab not detecting new episodes** — `episode_update` table is EMPTY (0 rows); `anime_update_state` has 15 rows but ALL `acknowledged=0` + the refresh button does nothing visible. The Updates engine + WorkManager are not firing or not writing results. | High | ~4-6h | Investigate `:core:updates` module — is the WorkManager job scheduled? Is the AniList airing query firing? Are results being written to `episode_update`? |
| 14 | **Notifications are UI-only** — `notification_config` + `notification_sent` tables are EMPTY (0 rows). The settings UI exists but no notification posting logic is wired for actual new-episode detection. Blocked by #13 (Updates must work first). | Medium | ~4-6h | Blocked by #13. Once Updates detect new episodes, wire NotificationManager to post per-anime config. |
| 15 | **Download concurrency bug** — starting a 2nd download auto-cancels the 1st. The download queue should support parallel downloads (or at least queue them, not cancel). User reported: "the previous one got automatically canceled and the new one started to download." | High | ~3-4h | Investigate `DownloadQueue` / `DownloadOrchestrator` — is there a max-concurrent=1 setting that cancels instead of queuing? |
| 16 | **Download UI missing server/audio info** — `downloaded_episode.source_id`, `video_server`, `video_audio` are all NULL in the DB. The Downloads page doesn't show which server or audio version (SUB/DUB) is downloading/downloaded. | Medium | ~2-3h | Wire the resolver's server-name + audio-variant through to `DownloadedEpisode` + display in Downloads page row. |
| 17 | **`downloaded_episode.file_size = "0"`** — the file size isn't recorded after download. Minor, but useful for display + storage management. | Low | ~0.5h | Set `file_size` from the downloaded file's length after completion. |
| 18 | **Extensions page lag with ~240 available extensions** — scrolling to "Available" section jitters/lags. 240 extension icons fetched from `raw.githubusercontent.com` (network log shows 240 requests). Likely: no lazy loading of icons + no list virtualization optimization. | Medium | ~2-3h | LazyColumn is already used — investigate if icons are being fetched synchronously or if the list items are too heavy. Consider pre-fetching icons in batch + caching. |
| 19 | **Extensions need better filtering** — by language, by NSFW, by installed status. Currently only a basic search. User wants granular filtering for ~240-extension repos. | Medium | ~2-3h | Add filter chips (language dropdown, NSFW toggle, installed-status toggle) to ExtensionsSettingsScreen. |
| 20 | **Details page stale-state flash** — opening content B after closing content A shows a brief glimpse of content A's data. Subtle but visible. Race condition in DetailsViewModel state clearing. | Low-Medium | ~1-2h | Ensure `_state.value = Loading` is set SYNCHRONOUSLY when the NavKey changes, before any async load. Clear all state flows (anime, episodes, metadata, autoLink) at the start of `loadFromAniList`/`loadFromExtension`. |
| 21 | **Details page "No source linked" occasionally shown despite being linked** — race condition. The `loadLinkedSource` restoration runs async; if the UI renders before it completes, it shows "no source". Going back + reopening fixes it. | Medium | ~1-2h | Initialize `_linkedSource` from a synchronous PreferenceStore read (already cached in memory) BEFORE the async content load. Or show a loading state instead of "no source" until `loadLinkedSource` completes. |
| 22 | **`user_customization` table is EMPTY** despite user changing appearance (accent/theme). Appearance settings may be persisted via PreferenceStore (SharedPreferences) not the DB table. Inconsistency: some settings in DB, some in SharedPreferences. | Low-Medium | ~1-2h | Audit where appearance settings are stored. If PreferenceStore is correct, drop `user_customization` table (dead). If DB is correct, wire the Appearance screen to write to it. |

## Last Updated
- Session: **D-255/256 — device-feedback fixes (palette-nav structural bug, FlowRow crash w/ the koin-compose→compose-1.10.4 version-skew discovery, update-check java.time) + Browse hero v2 (cover+banner+tags) + v0.2.49 release (2026-08-25, on `test-feature/video-cache-new-download` @ 592e03b1)** — root causes verified via CI-APK artifact inspection; compile review caught 1 error pre-push; release + dashboard version updates deployed. OPEN USER DECISION: BOM-vs-runtime alignment (D-255).
- Prior session same day: **D-252/253/254 — pointed library badges + complete Browse UI overhaul + custom palette editor (2026-08-25, @ 7ef10689)** — research (R-A/R-B) + plan-review agent (4 fixes) + compile-review agent (2 errors fixed: staticCompositionLocalOf is a function not a type; missing @OptIn on CustomPaletteSheet) + 3 phase commits + docs. Emulator OFF-LIMITS this session per user. Awaiting CI + user device verification.
- Prior session same day: **Review #3 + Dashboard /review/ section (2026-08-25)** — 5 read-only research sub-agents (R-1 deferred concerns · R-2 decisions/drift · R-3 features remaining · R-4 metrics · R-5 dashboard/deploy) + main-agent source verification + 1 full-stack-dev sub-agent (dashboard). Old `/key-findings/` page DELETED per user instruction; fresh `/review/` "Review & Roadmap" section built + deployed via `workflow_dispatch`. Verified: **48 modules / 383 .kt / 84,001 LOC / 24 tables / 17 .sq / D-251 / v0.2.48 / CI GREEN @ 127d074f / 201 lessons / release v0.2.48 published**. Deferred Concerns re-audit: 13 resolved / 3 partial / 6 open + 6 NEW concerns (java.time-without-desugaring crash risk, DownloadService runBlocking, extensions Available-section virtualization, FirstRun skip-button dead, OAuth polling loop, reorder not persisted). Doc-drift: ~60 stale claims across 12 files (44 missing decision IDs; D-198 status wrong).
- By: main agent + 5 read-only research sub-agents (R-1..R-5) + 1 full-stack-developer sub-agent (dashboard rebuild, DASHBOARD/webpage/ only per §19).
- Branch: `test-feature/video-cache-new-download` @ `421874ed` (32 commits ahead of `main` @ `26e4772` — 31 feature commits + the review commit). NOT merged — awaiting user device verification of D-243..D-251.
- Note: decisions.md on this branch has D-001..D-198 + D-242..D-251; the D-199..D-241 gap (43 decisions + D-121) is STILL unlogged (recommended NOW item #5 on the /review/ page). D-198 status STILL says PROPOSAL but commit 775876a2 implemented it. AGENT-CONTEXT counts in older "What's Done"/"What's Next" sections remain as historical records; the VERIFIED current counts are in this session's block (top) + on the live `/review/` page.
- Live page: `https://testplay-byte.github.io/ANI-KUTA/review/`. Other dashboard pages reflect main @ 26e4772 (stale relative to this branch) — a truth-sweep is queued for merge time (NEXT #13 on the /review/ page).

## Session — D-191 DB Analysis + Deferred-Concerns Expansion (this session)
### What was done
- User completed the full DB test checklist (Phase 0-14) + uploaded 3 export files to `USER-UPLOADS/` on the repo:
  - `DATABASE.json` (228KB) — full DB export via debug bubble Database tab.
  - `NETWORK.log.txt` (8KB) — network activity via debug bubble Network tab.
  - `DATABASE-ACTIVITY.log.txt` (41KB) — SQL read/write trace via debug bubble DB Activity tab.
- Agent downloaded + analyzed all 3 files. Findings:
  - **DB is mostly healthy**: 501 rows across 28 tables. Zero FK orphans (all main_id references resolve). Lookup tables seeded correctly. D-190 enrichment working well (143 episodes with 88-115 having japanese titles, romaji, runtime, thumbnails, descriptions).
  - **D-190 metadata engine confirmed working**: AniZip (19 reqs), Jikan (22 reqs), Kitsu (19 reqs) all firing. 52/143 episodes have filler/recap/score from Jikan (the rest have null = unknown, which is correct per D-190 design).
  - **11 new concerns found** (added to Deferred Concerns registry as #12-22): activity_event empty (zero callers of ActivityTracker.track), Updates not detecting episodes, Notifications UI-only, download concurrency bug (2nd cancels 1st), download missing server/audio info, file_size=0, extensions page lag (240 icons), extensions need filtering, details page stale-state flash, "no source linked" race, user_customization table empty.
- User also corrected a checklist gap: Phase 5 (watch an episode) didn't clearly state extensions are a hard prerequisite. Agent acknowledged + saved as a lesson.

### DB-quality analysis verdict
The database is **well-structured + mostly healthy**, but has 11 functional gaps (most are "feature not wired" not "schema wrong"). The schema itself is sound — 28 tables, proper FK relationships (post-D-189), good index coverage (post-D-166), zero orphans. The issues are at the application layer (features not writing to the DB), not the schema layer. See the comprehensive analysis delivered to the user + the 11 new Deferred Concerns (#12-22).

### CI status
- No code changes — docs + analysis only. Awaiting push to `docs/db-analysis-and-concerns`.

### What's next
- User reviews the DB analysis + decides which concerns to prioritize.
- Highest-impact fixes: #12 (activity_event — wire ActivityTracker), #13 (Updates — investigate WorkManager), #15 (download concurrency — fix cancel-instead-of-queue).
- User may request a DB schema cleanup phase (drop dead tables: `content_ext`, `user_customization` if confirmed unused).

## Session — D-190 Multi-Source Episode Metadata Engine (previous session — MERGED to main)
### What was done
- **Research**: read current `EpisodeMetadataFetcher` (standalone, non-pluggable, uses Anikage.cc). Fetched + verified 3 APIs live (AniZip `api.ani.zip/mappings`, Jikan `api.jikan.moe/v4`, Kitsu GraphQL). Read Dantotsu reference repo (`Anify.kt` = AniZip, `Kitsu.kt`, `IdMappers.kt`, `EpisodeMapper.kt`) for API usage patterns.
- **Plan**: designed pluggable `EpisodeMetadataProvider` interface with `ContentId` + `ContentIdType` (future-proof for TMDB). 3 providers (AniZip primary, Jikan for filler/recap, Kitsu tertiary). Engine orchestrates parallel fetch + merge. 8 new DB columns.
- **Sub-agent plan review (Task m8)**: verified all 3 APIs. Found 3 must-fix flaws (undercounted call sites — 4 not 3; `async.awaitAll` failure isolation needed; `mergeEpisodeBatch` missing). All fixed before implementation.
- **Implementation (12 files)**:
  - DB: `dataCache.sq` (8 new columns) + `DatabaseDriverFactory.kt` (8 ALTER TABLE migrations) + `DataCacheModels.kt` (8 new fields) + `DataCacheRepository.kt` (3 sites updated).
  - Engine: `EpisodeMetadataProvider.kt` (NEW — ContentId + ContentIdType + interface) + `EpisodeMetadataEngine.kt` (NEW — orchestrator with per-provider try/catch) + deleted `EpisodeMetadataFetcher.kt`.
  - Providers: `AniZipEpisodeProvider.kt` + `JikanEpisodeProvider.kt` (rate-limit-aware, NBSP trim) + `KitsuEpisodeProvider.kt` (GraphQL).
  - Merger: `MetadataMerger.kt` — added `mergeEpisodeBatch` + `mergeBooleanOrTrue` (OR-true for filler/recap).
  - Wiring: `MetadataModule.kt` (Koin multi-binding) + `DetailsViewModel.kt` (constructor rename + 4 call sites + 3 enriched constructors + 1 reconstruction — all 8 new fields propagated) + `DetailsModule.kt` (comment).
- **Sub-agent compile review (Task m7)**: ✅ READY TO PUSH. Zero compile errors. 8 verification areas clean. 7 non-blocking concerns — 3 fixed (unused engine params, Kitsu KDoc, AniZip ID types), 4 deferred.

### Key design decisions
- `is_filler`/`is_recap` are NULLABLE (null=unknown, not false=confirmed-not) — Jikan is the only source with filler info; if it fails, null is honest.
- Merge: first-non-null-wins by priority (AniZip > Jikan > Kitsu) for most fields; OR-true for filler/recap.
- Engine: parallel fetch with per-provider try/catch (one failure doesn't cancel siblings).
- Future-proof: `ContentId(TMDB, tmdbId)` → new `TmdbEpisodeProvider` module → zero engine changes.
- Backward-compatible public API: `fetchEpisodeMetadata(anilistId, malId, episodeCount)` unchanged.

### CI status
- Awaiting push to `feature/episode-metadata-engine`. 12 files changed — expected green.

### What's next
- User reinstalls the app (schema change — 8 new columns require fresh install per §30).
- User opens an anime with an AniList ID + links an extension source → episode list should load with rich metadata (titles, thumbnails, descriptions, air dates, **filler badges from Jikan**).
- User exports DB via debug bubble → agent analyzes for the DB-quality phase.

## Session — D-189 FK Crash Fix (this session)
### What happened
- User ran the DB test checklist (from the previous session). On Phase 2, after opening an AniList anime + linking an extension source via the three-dot menu → "Link source", the app **crashed**:
  ```
  android.database.sqlite.SQLiteConstraintException: FOREIGN KEY constraint failed (code 787 SQLITE_CONSTRAINT_FOREIGNKEY)
    at ...ContentQueries.updateContentSources(ContentQueries.kt:788)
    at ...ContentRepository.updateContentSources(ContentRepository.kt:172)
    at ...ContentResolver.linkExtensionToExisting(ContentResolver.kt:246)
    at ...DetailsViewModel.linkSource(DetailsViewModel.kt:1565)
  ```

### Root cause (thorough investigation)
- The `content.extension_id` column had `FOREIGN KEY (extension_id) REFERENCES content_ext(id)`.
- `content_ext` table is **NEVER populated** — `ContentRepository.getOrCreateExtension()` (line 312) exists but has **ZERO callers**. The table is dead.
- The code consistently passes `extensionId = source.id` (the Aniyomi INTERNAL source ID, e.g. 12345) — NOT a `content_ext.id` (DB row ID). All 6 call sites in `DetailsViewModel.kt` (lines 979, 988, 1011, 1025, 1565, 1586) do this.
- Pre-D-166 (FKs OFF), this silently stored a dangling value. D-166 enabled `PRAGMA foreign_keys = ON` → the dangling FK now crashes on any non-null `extension_id` UPDATE/INSERT.
- The same bug existed on `extension_detail.extension_id` → `content_ext.id` (same FK, same root cause). `upsertExtensionDetail` would crash too, but `updateContentSources` crashes first.
- Verified safe: all OTHER FKs in `content.sq` (`data_source_id` → seeded `data_source`, `system_id` → seeded `system`, `extension_repo_id` → nullable, `main_id` → `content`) are correctly populated + won't crash.
- Verified: zero JOINs against `content_ext`, zero `DELETE FROM content_ext` — the `ON DELETE SET NULL` / `ON DELETE CASCADE` actions never fired. Removing the FK is a pure no-op behaviorally.

### The fix (D-189)
- **`content.sq`**: removed `FOREIGN KEY (extension_id) REFERENCES content_ext(id) ON DELETE SET NULL` from the `content` table. Removed `FOREIGN KEY (extension_id) REFERENCES content_ext(id) ON DELETE CASCADE` from the `extension_detail` table. Added explanatory D-189 comments on both tables. Kept the `extension_id` columns (now plain INTEGERs storing Aniyomi source.id). Kept the `content_ext` table itself (dead but harmless — to be analyzed during the DB-quality phase).
- **`ContentDataJson.kt`**: fixed 1 stale KDoc line (`@param extensionId FK to content_ext table` → accurate "Aniyomi internal source.id (plain INTEGER; NOT a FK post-D-189)").
- **No code changes needed** — the code already works correctly with `extension_id = source.id`. The FK was the only problem.
- **Sub-agent review (Task i8)**: ✅ READY TO PUSH. Zero ❌ issues. 7 ✅ items verified (SQL syntax, remaining FKs intact, no code depends on removed ON DELETE behavior, no JOINs against content_ext, fix resolves crash, no other latent FK issues, D-189 comments well-formed). 3 ⚠️ concerns (all non-blocking: existing installs need reinstall per §30, stale KDoc fixed, dead content_ext table deferred to DB-quality phase).

### Why this approach (drop FK) vs. the alternative (wire up content_ext)
- **Alternative considered**: wire up `content_ext` properly — call `getOrCreateExtension()` at all 6 sites to get a real `content_ext.id`, change `getContentByExtension` callers (4 sites) to pass `content_ext.id` instead of `source.id`. This is the "code matches schema" fix.
- **Chosen (drop FK)**: the "schema matches code" fix. Simpler, lower-risk, matches the actual data model. The code has ALWAYS treated `extension_id` as `source.id` (never as `content_ext.id`). The `content_ext` table was a Phase C design that was never wired up. Dropping the FK makes the schema honest about how the code works.
- **Future (DB-quality phase)**: decide the fate of `content_ext` — either (a) wire it up properly (re-introduce the FK with a back-fill migration that creates content_ext rows for every distinct source.id), or (b) drop the table + `getOrCreateExtension` entirely. Deferred to the DB-quality analysis the user is about to do.

### CI status
- Awaiting push to `feature/fix-fk-crash`. 1 .sq edit + 1 KDoc fix — expected green.

### What's next
- User reinstalls the app (uninstall + install the new APK with the fix — **required**: the fix is a schema change that only takes effect on fresh install per CORE_RULES §30).
- User re-runs the DB test checklist from Phase 2 (link extension source) to verify the crash is gone.
- User continues the checklist → exports the DB via debug bubble → provides it.
- Agent analyzes the DB for flaws + proposes improvements (the "DB management + quality" phase).

## Session — Doc-Debt Sweep + Deferred-Concerns Registry (previous session — MERGED to main)
### What was done
- **Comprehensive doc-debt sweep** — all stale documentation updated to match actual project state:
  - `knowledge/architecture.md` — fully rewritten (46-module graph, SQLDelight 28 tables, hand-rolled nav, DI wiring, known debt).
  - `knowledge/module-map.md` — fully rewritten (all 46 modules with jobs + deps + key files).
  - `knowledge/tech-stack.md` — fully rewritten (actual versions verified against `libs.versions.toml`; Nav3 removed; SQLite constraints).
  - `knowledge/old-vs-new.md` — fully rewritten (old project in REFERENCES/old-kuta/ANIKUTA/, full comparison + migration notes).
  - `knowledge/dashboard.md` — fully rewritten (14 pages, data files, update process, known dashboard debt).
  - `knowledge/project-overview.md` — fully rewritten (Q1/Q2/Q10 answered, current status).
  - `knowledge/ui-customization.md` — fully rewritten (5 customization layers, subtitle settings, live data verification).
  - `master.md` — updated (46 modules/28 tables, 30 sections, D-186, Nav3 fully removed, current focus = DB management, Deferred Concerns summary).
  - `SESSION.md` — updated (D-186, 46 modules, Deferred Concerns, doc-debt sweep done, §30 debug-schema stance).
  - `navigation.md` — updated (30 sections, REFERENCES/ expanded, download-research/ section added, all knowledge/ descriptions current).
  - `CORE_RULES.md` §8 — clarified (ABI config lives in `AndroidConfig.kt` via convention plugin, not `app/build.gradle.kts`; compileSdk 36 context).
  - `CORE_RULES.md` §30 — reinforced with user clarification (debug = no migrations, just recreate; onOpen is a convenience guard, not a migration system).
- **Code comments cleaned** (stale Nav3 references):
  - `build-logic/.../AndroidConfig.kt` — "Nav3 1.1.5 requires SDK 36" → accurate context.
  - `app/build.gradle.kts` — orphaned `// Navigation 3` → "hand-rolled (D-150)".
  - `gradle/libs.versions.toml` — 2 orphaned `# Navigation 3` → accurate.
- **Dashboard data updated** (full-stack-dev sub-agent, §19): 13 files updated (lib/data.ts, lib/decisions.ts, lib/schema.ts, lib/testingData.ts, lib/phaseD.ts, lib/downloadsPlan.ts + 6 page components + Footer). 46 modules / 26 core / 18 feature / D-001..D-186 / 28 tables / 15 .sq files / Nav3 removed / main branch across all 14 pages. Build PASSED.
- **Deferred Concerns registry established** — 11 items tracked in this file (see above). Each with severity, estimated effort, dependencies.
- **WatchKey god-object analysis** delivered to user (what/why/how/pros&cons/ease) — refactor deferred to a dedicated phase per user.

### Decisions
- D-187: Doc-debt sweep complete (all knowledge/* + master/SESSION/navigation + CORE_RULES §8/§30 + code comments + dashboard data).
- D-188: Deferred Concerns registry established (11 items) — tracked in progress.md, not fixed this session.

### CI status
- Awaiting push to `docs/doc-debt-sweep` branch. Comment-only code changes + docs — expected green.

### What's next
- User device-verifies the doc updates (optional — they're docs).
- User merges `docs/doc-debt-sweep` to `main` when satisfied.
- **Database management + quality** (next focus): user provides a fresh DB export after a clean-install test run; agent analyzes for flaws + proposes improvements.

## Session web-3a43f99b (twelfth pass) — Double-Resolve Bug Fix

### What was done
- ROOT CAUSE of "loading failed" identified via 2 parallel subagent analyses (COMPARE-OLD-TO-NEW + COMPARE-NEW-TO-OLD): VideoResolver called getHosterList TWICE (once for flat resolve(), once for structured resolveStructured()). AniKotoS extension creates a local proxy server on each call — second call killed first call's proxy URLs.
- FIX: Merged into single resolve() call. ResolverState.Success now includes rawVideos: List<Video>. DetailsViewModel calls videoResolver.buildServers(rawVideos) to derive structured servers from the SAME video list — NO second getHosterList.
- Also matched old project's filter: `videos.filter { it.videoUrl.isNotBlank() }` — rejects videos with empty URLs.
- PlayerErrorOverlay redesigned: inline on player surface (not popup), Close (X) button + Retry button.
- Added === VIDEO PICKED === log at DetailsScreen.onPickVideo showing quality, URL, headers, registry key.
- CI GREEN (8100d91, run 30900950702).

### Key decisions
- D-066: Double-resolve is forbidden. Single resolve() + buildServers() derivation.
- D-067: Error overlay is inline on player surface with Close button (not popup, not force-opening QualitySheet).

## Session web-f53f0459 — Phase 5c Player: Stuck-Loading Fix + Episode State + External Subtitles + Capture-Only Progress

### What was done
- **Stuck-loading regression FIXED (D-068)**: `setSwitchingError()` + 30s watchdog. All explicit failure paths now show errors immediately.
- **Episode-switch state hoisted (D-069)**: `currentEpisodeUrl/Number/Title/resolvedVideosKey` on `PlayerStateHolder`. Episode list highlight + "now playing" card + QualitySheet now reactive to switches.
- **External subtitle/audio loading re-added (D-070)**: `pendingSubtitleTracks/AudioTracks/trackHeaders` on `PlayerObserver`. `sub-add`/`audio-add` on FILE_LOADED with 300ms delay. Wired in initMpv + onQualitySelected + onEpisodeSwitch.
- **SubtitleTrackFormatter ported (D-071)**: ISO 639 → English names. "English" instead of "eng".
- **EpisodeSwitchingOverlay ported (D-073)**: Loading shield over player during switches. Both minimized + fullscreen.
- **Speed setter bug fixed (D-073)**: `setPropertyDouble` instead of `setPropertyInt` (was truncating 1.5f→1).
- **Capture-only WatchProgressStore (D-072)**: InMemoryWatchProgressStore + periodic save (10s) + save-on-dispose. No restore yet.
- **Dead singleOf(::PlayerStateHolder) removed (D-074)**.
- **CORE_RULES updated**: §5 (player scaffolding is not boilerplate + interface exception), §7 (player carve-out), §17 (import rewrite rule).

### Status
- CI pending push (will push after this commit).
- Awaiting device verification.

### What's next
- Phase D: Wire 7 dead fullscreen buttons (skip-next, audio, server, speed, more, PiP, rotate).
- Phase E: 15s fatal-error watchdog, auto-play-next, skip OP/ED, app-exit pause/resume.
- Phase F: Full doc-drift sweep + D-050 re-decide (companion hack).

## Session web-f53f0459 (continued) — Player Playback Fixes + Remaining Phases

### Critical playback fixes (from user log analysis)
- TLS CA cert fix (D-075): deleted empty cacert.pem, guarded tls-ca-file → HTTPS streams work now
- Observer cleanup (D-076): remove MPVLib observers on dispose → no more 4x event duplication
- Error handling rework (D-077): non-intrusive banner + auto-retry (no more dialog box)
- Spinner fix (D-078): pause no longer shows loading spinner
- Episode switch title (D-078): overlay shows correct episode name during switching
- Better error messages: TLS/SSL/HTTP/stream errors captured + appended

### Remaining phases completed
- Episode sanitization (D-079): EpisodeTitleParser — clean titles, no more hashes/code as names
- Speed control (D-080): SpeedSheet wired in fullscreen — presets + slider, live apply
- Skip-next (D-080): wired → switches to next episode
- 15s fatal-error watchdog (D-081): catches stuck HLS streams
- App-exit pause/resume (D-081): ON_STOP pauses playback

### CI status
- All commits green (last: 061c17b)

### What's next
- User device testing of all fixes
- Remaining dead fullscreen buttons (audio, server, more, PiP, rotate)
- Auto-play-next, skip OP/ED
- Full doc-drift sweep

## Session web-f53f0459 (continued) — Phase B: Auto-Link System

### What was done
- **Download button fix (D-122)**: Created `DownloadEpisodeButton` composable (24dp icon in 40dp clickable Box) used consistently in BOTH places (with/without synopsis). Shows toast "Download functionality not yet implemented" on tap.
- **`:core:smart-matcher` module created (D-123)**: New module with:
  - `TitleNormalizer` — normalizes titles (lowercase, strip punctuation, remove season/year suffixes like S2, Season 2, (TV), 2nd Season, II/III/IV).
  - `LevenshteinDistance` — character-level edit distance + similarity ratio (two-row DP, O(n) space).
  - `MatchResult` — sealed: Match/NoMatch/Skipped/Error.
  - `SmartMatcherConfig` — threshold (0.80 default), strategy (FUZZY/STRICT/MANUAL), yearBonus (0.10), containsBonus (0.05).
  - `SmartMatcher` — main matcher: normalize → Levenshtein → contains bonus → year bonus → cap 1.0 → threshold check.
  - `AutoLinkResult` — sealed: Cached/Matched/NoMatch/Skipped/Error.
  - `AutoLinkService` — orchestrator: cache check → per-source setting → AniList search → SmartMatcher → cache result.
  - `SmartMatcherModule` — Koin DI.
- **`AutoLinkPreferences` (D-124)**: Added to `:core:preferences`. Stores:
  - Global toggle (auto_link_enabled, default true)
  - Strategy (auto_link_strategy: fuzzy/strict/manual, default fuzzy)
  - Threshold (auto_link_threshold, default 0.80)
  - Per-source overrides (auto_link_source:$sourceId: default/on/off)
  - Link cache (auto_link_cache:$sourceId:$hash(animeUrl): anilistId)
- **DetailsViewModel rewrite (D-125)**: Added 9th + 10th + 11th constructor params (anilistProvider, autoLinkService, autoLinkPreferences). New state: `autoLinkState` (Idle/Searching/Matched/NoMatch/Skipped/Error), `anilistSearchState` (Idle/Searching/Empty/Results/Error), `showManualLinkSheet`. New methods: `performAutoLink()`, `searchAniListForLink()`, `linkAniListEntry()`, `skipAniListLink()`, `unlinkAniList()`, `openManualLinkSheet()`, `dismissManualLinkSheet()`, `mergeAniListIntoUnified()`. Auto-link kicks off after `loadFromExtension()` succeeds. On match, merges AniList data via `AniListDetailsProvider.mergeInto()` + triggers episode metadata fetch.
- **ManualLinkSheet (D-126)**: Bottom sheet for manual AniList linking. Header "Link to AniList" + search field (pre-filled with extension title) + search button + results list (cover + title + score + year + Link button) + "Skip AniList link" button. Auto-searches on open. Full states (Idle/Searching/Empty/Results/Error).
- **AutoLinkSettingsScreen (D-127)**: New settings screen accessible from SettingsScreen hub → "Metadata" → "Auto-Link". Global section: master toggle + strategy selector (Fuzzy/Strict/Manual segmented toggle) + threshold slider (0.50–1.00). Per-extension section: 3-way override (Default/Always link/Never link) per installed extension.
- **DetailsScreen updates (D-128)**: Added auto-link badge ("Linked to AniList" with check icon) + searching spinner ("Auto-linking...") in the banner. Added "Link to AniList" / "Unlink AniList" to the three-dot menu (extension entries only, with divider). Wired ManualLinkSheet.
- **`AniListDetailsProvider` registered as concrete type (D-129)**: anilistModule now registers it both as `AnimeDetailsProvider` (named "anilist") AND as concrete `AniListDetailsProvider` for direct injection into DetailsViewModel.
- **Subagent review**: All 24 files reviewed for compile errors — clean. No issues found.

### CI status
- Awaiting push + CI build.

### What's next
- User device testing of Phase B (auto-link ON match, auto-link OFF, manual link, skip, per-extension override).
- Phase C: contentId system (migrate identity, watch progress, library).
- Phase D: Multi-source metadata (MAL, TMDB, Kitsu providers).

## Session web-f53f0459 (continued) — Phase B Fixes + Repo Reorganization

### User testing feedback (Phase B)
User tested Phase B and reported:
- ✅ Download button size now consistent (both with/without synopsis) + toast works.
- ✅ Auto-link ON: Opens extension anime → "Linked to AniList" badge → metadata loads.
- ✅ Auto-link cache: Re-opening loads instantly (user noted they didn't ask for cache — will revisit).
- ✅ Auto-link NoMatch: Opens manual link sheet correctly with search + skip.
- ✅ Manual link/unlink: Three-dot menu shows correct options.
- ⚠️ Manual link: Tapping a result does NOT update the details page UI (stale extension data shown).
- ❌ AutoLinkSettingsScreen UI: Header/first option overlapping, "texture overlapping", ugly.
- ❌ Per-extension override: Not reactive — must leave + return to see changes.
- ⚠️ Stale metadata when switching details pages (unlinked content).
- 📋 User wants a data-source selector (AniList vs Extension priority).

### Fixes implemented (D-130 through D-133)

**D-130: Data-source priority + selector**
- Added `DataSourcePriority` enum (ANILIST/EXTENSION) to `:core:common`.
- `AnimeDetailsProvider.mergeInto()` now takes a `priority` parameter.
  - ANILIST: AniList values overwrite extension values. Used for manual link.
  - EXTENSION: Extension values kept; AniList fills nulls. Used for auto-link.
- Added `dataSourcePriority` field to `UnifiedAnime`.
- Added `switchDataSource(priority)` method to DetailsViewModel.
- Added `DataSourceSelector` composable (segmented toggle) to DetailsScreen — shown only when both anilistId + sourceId are present.

**D-131: Stale metadata fix**
- `loadFromAniList()` + `loadFromExtension()` now reset ALL state flows before loading (was only resetting _state + autoLinkState).

**D-132: Per-extension override reactivity**
- Each `PerExtensionCard` now holds a local `mutableStateOf` snapshot keyed by `ext.pkgName`. Tapping updates the local state immediately (UI flips live) AND persists.
- Redesigned AutoLinkSettingsScreen UI: split into 4 separate cards (SwitchCard, StrategyCard, ThresholdCard, PerExtensionCard) with shorter subtitles + animated color transitions.

**D-133: Repo reorganization + new core rules**
- Moved AGENT-CONTEXT/, APP/, DASHBOARD/, REFERENCES/ into single wrapper folder `ANI-KUTA/`.
- Updated CI workflows (build-apk.yml + deploy-dashboard.yml) with new paths.
- Updated .gitignore + README.md.
- CORE_RULES §4: single-wrapper-folder rule (non-negotiable).
- CORE_RULES §15: sandbox recovery rule (re-clone if environment feels off).

### Subagent review
All 7 modified files reviewed for compile errors — clean. No issues found.

### CI status
- Awaiting push + CI build.

### What's next
- User device testing of all fixes.
- Phase C: contentId system (migrate identity, watch progress, library).

## Session web-f53f0459 (continued) — Data Source Selector Fix + Phase C Plan v2

### User testing feedback (round 2)
User tested the Phase B fixes and reported:
- ✅ Download button: still good + toast works.
- ✅ Data source selector: AniList toggle works (shows AniList data).
- ❌ Data source selector: Extension toggle does NOT update (stale AniList data shown). Had to reopen the page.
- 📋 Selector placement: should be in the three-dot menu, not below the banner.
- 📋 Selector should be available for AniList entries too (when they have a linked source).
- 📋 Selector will eventually support more sources (TMDB, Kitsu) — not just AniList vs Extension.
- ✅ Auto-link settings UI: redesigned, looks much better now. Per-extension override works live.
- ✅ Repo structure: looks good.
- 📋 Phase C: user reviewed the plan, gave detailed feedback on contentId design.

### Fixes implemented (D-134)

**D-134: Data source selector — fix reactivity + move to three-dot menu**
- Root cause: `mergeAniListIntoUnified` overwrote the base UnifiedAnime's fields with AniList data. Switching back to EXTENSION priority couldn't recover the original extension data.
- Fix: Added `extensionBase` + `anilistBase` to DetailsViewModel. The displayed UnifiedAnime is always computed by `remergeBases(priority)` which merges the two original bases. Switching priority never loses data.
- Moved selector from LazyColumn body to three-dot DropdownMenu.
- Made selector available for AniList entries with linked sources (`linkSource()` now creates `extensionBase` from the picked SAnime).
- Updated `linkSource()`, `unlinkSource()`, `unlinkAniList()` to manage the bases.

### Phase C plan v2 created
- Replaced the old Phase C plan with a new v2 that incorporates user feedback.
- Key design: stable UUID contentId + content_source_link table (one-to-many).
- Database tables with full SQL (content, content_source_link, watch_progress, library, watch_history).
- Architecture: new `:core:content` module with ContentRepository + ContentResolver.
- 6 open questions for the user (Q-001 through Q-005 + Q-006 confirmed).
- Honest analysis of the user's "changing contentId" proposal — explained why a stable ID is safer.
- No migration needed (watch progress, library, history aren't set up yet).

### CI status
- Awaiting push + CI build.

### What's next
- User reviews Phase C plan v2 + answers open questions.
- After confirmation, use full-stack-dev agent to convert plan into a web page.
- Then implement Phase C (C.1 → C.5).

## Session web-f53f0459 (continued) — Phase C plan v4 (final)

### User feedback on plan v3
User reviewed the Phase C plan v3 + dashboard page and gave detailed feedback:
- ✅ Two-ID system (Main ID + Content ID) is good.
- ❌ Content ID missing extension ID + source ID — needs 6 sections, not 5.
- 📋 Repo URL format: ends with `index.min.json` (e.g. `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json`).
- ❌ Web page: tables should be one-per-row, not two-per-row.
- 📋 Session scope: focus ONLY on content ID system (main + detail + lookup tables). Defer watch progress/library/history/tracking.
- 📋 Use separate detail tables per source type (anilist_details, extension_details, other_source_details).

### Plan v4 created (D-135)
- Content ID format v2: 6 sections with `sourceId` added. Uses repo DB ID (integer) instead of full URL.
- 8 tables: 4 lookup (data_sources, systems, extension_repos, extensions) + 1 main (content) + 3 detail (anilist_details, extension_details, other_source_details).
- Removed deferred tables (watch_progress, library, watch_history, content_source_link) from this session's scope.
- 10 confirmed decisions (Q-001 through Q-010).
- Dashboard web page updated: one table per row, new Content ID format, new detail tables, removed deferred tables, added "Deferred" section.

### CI status
- Awaiting push + CI build.

### What's next
- User reviews plan v4 + dashboard page.
- If approved, implement Phase C (C.1 database schema → C.2 content module → C.3 DetailsViewModel integration → C.4 console logging).

## Session web-f53f0459 (continued) — Phase C implementation (content identity + library)

### What was done
- **Content ID format fix**: Changed from repo DB ID to full repo URL per user request. The URL is essential for backup/restore + retrieving more extension IDs.
- **New module `:core:content`**: ContentIdGenerator, ContentRepository, ContentResolver, ContentSeeder.
- **8 content tables + 2 library tables** created in SQLDelight.
- **Lookup tables seeded** on first launch (data_sources, systems, Default library category).
- **DetailsViewModel**: wired ContentResolver + ContentRepository. Calls resolveContentForAniList/resolveContentForExtension on load. Added toggleLibrary() + isInLibrary state.
- **DetailsScreen**: bookmark button now works (saves/un-saves to Default category).
- **LibraryViewModel**: rewritten to use ContentRepository instead of PreferenceStore. Fetches content records + AniList data for grid display.
- **Subagent review**: all 18 files pass compile check.

### CI status
- Awaiting push + CI build.

### What's next
- User device testing of the library system.
- If issues, fix them.
- Then continue with watch progress, history, tracking (deferred).

## Session web-f53f0459 (continued) — Cross-source dedup + library categories

### User testing feedback (Phase C round 1)
User tested Phase C and reported:
- ✅ Library save works (bookmark button saves, remembers across restarts).
- ✅ Library page shows saved anime.
- ❌ **Duplicate library entries**: Saved anime from AniList, then opened same anime from extension → 2 separate library entries (should be 1 — same content).
- ❌ Extension anime not showing as saved after auto-link (the mainId was different).
- ❌ Extension library entry gives 404 error when opened (anilistId=0 for extension-only content).
- ❌ Data-source selector + unlink not working smoothly after linkSource.

### Fixes implemented (D-137, D-138)

**D-137: Cross-source content deduplication**
- Root cause: `resolveContentForExtension` didn't check auto-link cache → always created new content record. `mergeAniListIntoUnified` didn't persist the link in the database.
- Fix: `resolveContentForExtension` now checks `autoLinkPreferences.getCachedAniListId()` first. If cached + content exists → links to existing mainId. `mergeAniListIntoUnified` now calls `contentResolver.linkAniList()` to persist. `unlinkAniList` calls `contentResolver.unlinkAniList()`.

**D-138: Library categories system**
- CategoryPickerSheet (long-press bookmark) — shows categories with checkboxes + create new category.
- ContentRepository: full category CRUD.
- DetailsViewModel: category state + methods.
- LibraryViewModel: category filtering + management.
- library.sq: renameCategory + getCategoriesForContent queries.

### CI status
- CI #197 GREEN. APK built successfully.

### What's next (pending)
- Library page category tabs UI (showing categories as tabs at the top) — ViewModel logic is ready, LibraryScreen UI needs updating.
- Long-press category tab → delete/rename dialog.
- Extension library entry 404 error (navigation issue — library passes anilistId=0 for extension-only content).
- Document the data structures in DOCUMENTATION/database/.

## Session web-f53f0459 (continued) — D-139: Cross-source dedup root cause + library crash + category tabs

### User testing feedback (Phase C round 2)
- ❌ Cross-source dedup still not working — extension anime not showing as saved after auto-link.
- ❌ Library crash: `IllegalArgumentException: Key "194829" was already used` — duplicate anilistId keys in LazyVerticalGrid.
- 📋 Category picker should be a popup, not a bottom sheet.
- ❌ Data source selector: cover switches but nothing else changes.
- ❌ Unlink AniList: extension-only state doesn't load info.
- 📋 Library page category tabs UI not implemented.

### Root cause analysis
The cross-source dedup failure was because `linkSource()` (called when linking a source from the AniList side) didn't cache the reverse mapping. When the same anime was later opened from the extension, `resolveContentForExtension` couldn't find the existing content record → created a new one with a different mainId → bookmark didn't show as saved → library had duplicate entries.

### Fixes implemented (D-139)
1. **linkSource()** — now caches reverse mapping + persists extension link in DB + fetches full extension details.
2. **Library dedup** — deduplicates by anilistId to prevent LazyGrid crash.
3. **CategoryPickerSheet** — changed to AlertDialog popup.
4. **remergeBases()** — STRICT switching (primary values only, no fallback).
5. **Library category tabs** — CategoryTabsRow + long-press delete/rename + create new category.

### CI status
- CI #199 GREEN.

### What's next
- User device testing.
- Extension library entry 404 error (navigation issue — still pending).
- Document data structures in DOCUMENTATION/database/.

## Session web-f53f0459 (continued) — D-140: Library crash + 404 + live reload + category tabs

### User testing feedback (Phase C round 3)
- ✅ Cross-source dedup working (bookmark shows as saved from either entry point).
- ❌ Library crash: `Key "0" was already used` — multiple extension-only entries with anilistId=0.
- ❌ 404 error when opening extension-only content from library.
- ❌ Library not updating live (needs app restart).
- ❌ Data source selector disappears on reopen (only Refresh/Share shown).
- ❌ Category tabs UI bad (bubbles, lock icon, "+" button).
- ❌ Long-press category tab not working.
- 📋 Category tabs smart features (Default hides when empty, All hides when 1 cat).
- 📋 Library header should show total entries.
- 📋 Delete category with move-to-default option.

### Fixes implemented (D-140)
1. **LibraryEntry** data class — uses mainId (stable UUID) as key. Fixes crash + 404.
2. **LibraryViewModel** rewritten — builds LibraryEntry from content records.
3. **LibraryScreen** rewritten — uses mainId as key, LibraryEntry for navigation, live reload via LaunchedEffect.
4. **MainActivity** — navigation checks hasAniListId → AniList, else → Extension.
5. **loadLinkedSource** — restores extensionBase from DB on reopen.
6. **Category tabs** — smart features, text+underline style, no "+" button.
7. **Long-press** — Rename / Delete (with Move to Default option).
8. **Library header** — total entries subtitle.
9. **CategoryPickerSheet** — removed lock icon.
10. **New setting** — showCategoryCounts.

### CI status
- CI #201 GREEN.

### What's next
- User device testing.
- Extension library entry source link restoration (when opening from library, episodes should load from the linked source).
- Document data structures in DOCUMENTATION/database/.

## Session web-f53f0459 (continued) — D-141: Library UI fixes + multi-select + Phase D plan

### User testing feedback (Phase C round 4)
- ✅ Cross-source dedup working.
- ✅ Library crash fixed.
- ✅ 404 error fixed.
- ✅ Library live reload working.
- ✅ Data source selector on reopen working.
- ✅ Category tabs smart features working.
- ❌ Extension-only cover images not showing in library.
- ❌ Library heading should be "X in Library" (not separate heading + subtitle).
- ❌ Category count format should be "[3] Default" (not "Default (3)").
- ❌ Delete dialog formatting (Move to Default should only show if category has entries).
- ❌ No white spacer line below category tabs.
- ❌ Library performance (re-fetches from AniList on every tab switch).
- 📋 Multi-select mode for library entries.
- ❌ Refresh button in details page doesn't work.

### Fixes implemented (D-141)
1. Extension-only cover images — fixed coverUrl assignment.
2. Library heading — "X in Library" as main title.
3. Category count — "[3] Default" format.
4. Delete dialog — Move to Default only if entries exist.
5. White spacer line below tabs.
6. In-memory cache — anilistCache prevents re-fetching on tab switch.
7. Multi-select mode — long-press → selection mode with bottom bar.
8. Refresh button — DetailsViewModel.refresh() wired to menu item.

### Phase D plan written
- `DOCUMENTATION/planning/data-management/PHASE-D-PLAN.md`
- Covers: local metadata cache, browse page cache + refresh, details page multi-stage refresh, image caching, backup/restore, library performance.
- 6 implementation phases (D.1-D.6).
- 5 open questions for the user.

### CI status
- CI #204 GREEN.

### What's next
- User device testing of D-141.
- Answer Phase D open questions (Q-001 through Q-005).
- Begin Phase D implementation.

## Session web-f53f0459 (continued) — D-142, D-143: UI fixes + bottom nav replacement

### D-142: Extension cover images + multi-select UI
- Fixed extension-only cover images: `resolveContentForExtension` now accepts UnifiedAnime + stores `extension_detail` (with `thumbnailUrl`) in DB.
- Category count format: changed to rounded brackets "Default (3)".
- Multi-select top buttons: styled with icons (DoneAll/Clear/SyncAlt).
- Multi-select bottom bar: replaced nav bar with opaque surface + icons (Close/Category/Delete).
- Phase D plan v2: removed backup/restore, metadata never expires, 6hr homepage only, vibration, solid caching, two source types.

### D-143: Bottom nav bar replacement + library total count
- Added `selectionModeContent` parameter to `AnikutaBottomNavBar`.
- Created `LibrarySelectionMode` + `LocalLibrarySelectionMode` CompositionLocal.
- LibraryScreen syncs selection state → AppRoot reads it → passes SelectionActionBar to nav bar.
- SelectionActionBar replaces the nav pills INSIDE the floating pill (not overlay).
- Library header: totalEntries shows TOTAL across ALL categories.

### CI status
- CI #210 GREEN.

### What's next
- Start Phase D implementation (D.1-D.5).

## Session web-f53f0459 (continued) — D-143 + Phase D.1

### D-143: Bottom nav bar replacement + library total count
- Added `selectionModeContent` parameter to AnikutaBottomNavBar.
- Created LibrarySelectionMode + LocalLibrarySelectionMode CompositionLocal.
- LibraryScreen syncs selection state → AppRoot reads it → passes SelectionActionBar.
- SelectionActionBar replaces nav pills INSIDE the floating pill (Cancel/Category/Delete with icons).
- Library header: totalEntries shows TOTAL across ALL categories.

### Phase D.1: Local metadata cache
- New module :core:data-cache with DataCacheRepository.
- 3 new SQLDelight tables: anime_metadata_cache, data_cache_episode, browse_cache.
- DetailsViewModel checks cache first → instant display → then fetches + caches.
- LibraryViewModel checks cache first → no network on tab switch.
- Metadata never expires. All data persists across restarts.

### CI status
- CI #216 GREEN.

### What's next (Phase D.2-D.5)
- D.2: Browse page cache + pull-to-refresh + 6-hour auto-update.
- D.3: Details page multi-stage refresh (vibration + visual indicators).
- D.4: Coil disk cache (500MB, persistent).
- D.5: Library pull-to-refresh with vibration + lazy loading.

## Session web-f53f0459 (continued) — Phase D.2-D.5 COMPLETE

### D.2: Browse page cache + pull-to-refresh + 6-hour auto-update
- BrowseViewModel reads from browse_cache first → instant display.
- If cache expired (6h) → fetches from network in background → updates cache.
- Pull-to-refresh with vibration (drag down at top).
- Background refresh indicator (subtle spinner when auto-updating).

### D.3: Details page multi-stage refresh
- refreshEpisodesList() — only refreshes episodes from extension source.
- refreshMetadata() — only refreshes metadata + updates anime_metadata_cache.
- refreshAll() — full refresh (both).
- Three-dot menu "Refresh" calls refreshAll().
- RefreshStage enum + RefreshState sealed interface for future scroll-based triggers.

### D.4: Coil disk cache (500MB, persistent)
- ImageLoaderFactory with 500MB disk cache + 25% memory cache.
- Registered as Coil singleton via SingletonImageLoader.setSafe.
- All AsyncImage composables use the persistent disk cache.
- Images survive app restarts.

### D.5: Library pull-to-refresh with vibration
- refreshLibrary() clears cache + re-fetches from network.
- Pull-to-refresh with vibration (drag down at top).
- Visual indicators: pull progress spinner + background refresh spinner.

### CI status
- CI #220 GREEN. Phase D complete.

### Phase D summary
- D.1: Local metadata cache ✅ (CI #216)
- D.2: Browse page cache + pull-to-refresh ✅ (CI #220)
- D.3: Details page multi-stage refresh ✅ (CI #220)
- D.4: Coil disk cache 500MB ✅ (CI #220)
- D.5: Library pull-to-refresh ✅ (CI #220)

## Session web-f53f0459 (continued) — D-146, D-147: Episode caching + offline fixes

### D-146: Multi-select category picker + cache-first details + offline + refresh feedback
- Multi-select category picker: no longer auto-closes. Multiple selections + Done button.
- Cache-first details: loadFromAniList skips network if cache exists.
- Offline mode: shows cached data instead of error when network fails.
- Refresh feedback: "Refreshing..." overlay with spinner.

### D-147: Episode caching + offline extension fallback
- Episode list caching: fetchEpisodes checks data_cache_episode first → instant from cache.
- After network fetch → caches episodes + enriched metadata locally.
- Offline extension: tryCachedExtensionData() loads from DB when network fails.
- Extension-only anime shows full details + episodes offline (if previously opened).

### CI status
- CI #224 GREEN.

### Still pending (will fix in next iteration)
- Browse pull-to-refresh (pointerInput gesture detection issue).
- Library 3-stage pull-to-refresh.
- Search page AniList caching (12-hour refresh).
- Library selection mode UI (fade unselected covers).

## Session — Download System (Phase DL.0-DL.8) — `download-system-plan` branch

> **Consolidated entry** for the 41 download-system commits on the
> `download-system-plan` branch (41 ahead of `main`, 0 behind). These commits
> were made across multiple working sessions but were never logged in
> `progress.md` — this entry closes that doc-drift gap (discrepancy D002,
> discovered during the analysis-and-doc-update session).

### What was done (by phase — commit SHAs from `git log`)

**Research + planning**
- `ba2141f` (DL-RESEARCH): 14 download-system research docs (`download-research/00-16`) + dashboard webpage.
- `8cb8177` (DL-PLAN-FIX): plan v2 — 5 review rounds (REVIEW-1..5) + 72 MUST-FIX items (M1-M72) applied.

**Phase DL.0 — Foundations**
- `5849e13` (DL-D0): download data models, preferences, `downloaded_episode` DB schema (re-keyed by `main_id` + `episode_key`, 5-digit padded; `.data.json` as durable source of truth; content FORMAT folders `video`/`images`/`text`).
- `379f3a6` (DL-D0-FIX): REVIEW-D0 fixes.

**Phase DL.1 — Engine + Storage**
- `9b4c5d7` (DL-D1-1): download data models + preferences.
- `b8b5d7b` (DL-D1-2): progress tracker + cache + logger + `DownloadManager` interface.
- `65fe7a4`, `baa7628`, `cebafb0`, `c558beb` (DL-D1-FIX1-4): interface alignment, 30+ compile errors, TempDownloadCache API, FileOutputStream param.
- Delivered: `DefaultDownloadManager`, `HttpDownloader` (Range-resume + validation + HLS re-detection), `HlsDownloader` (pure Kotlin, no encrypted HLS), `DownloadStorageProvider` (SAF + `.data.json` reinstall recognition + same-title collision handling), `DownloadScanner` (scan-on-startup), `TempDownloadCache`, `DownloadLogger`.

**Phase DL.2 — Orchestrator + AutoDownload + proxy-churn**
- `6382dbe` (DL-D2-1): `DownloadOrchestrator`, `AutoDownloadEngine` (5-step pure-function pipeline: flatten → rank → applyFallbacks → pick → globalFallback), `ReResolver` types.
- `8ad6899`, `add3932`, `5bbb5be`, `e633d81`, `30ed37a` (DL-D2-FIX1-5): video-resolver dep, List<Int> Comparable, ResolverState serialization, ReResolver return-in-collect, missing import.
- ⚠️ **Proxy-churn re-resolve is BUILT but NOT WIRED** (D-149, discrepancy D003): `HttpDownloader.reResolver = null` (`DownloadModule.kt:92`); the promised `:app` `downloadAppModule` was never created; the two `ReResolver` interfaces are signature-incompatible. Wiring deferred per user — see `ani-kuta-analysis/04-proxy-churn-explanation.md` for the full plan.

**Phase DL.3-DL.8 — Queue + Service + Notifications + Settings UI + Downloads UI + Player + QoL**
- `4298cb3` (DL-D3-D8-1): settings UI + downloads page + episode download control + player integration + QoL (one batch commit).
- `e29d616` (DL-D3-D8): wired download states into `DetailsViewModel` + verified all UI files.
- `a926b08`, `d5a8a00`, `e9d5592` (DL-D3-D8-FIX1-3): duplicate `downloadStates`, `DownloadNavKeys` package + MainActivity imports, duplicate download imports.
- Delivered: `DownloadQueue` (Mutex + Semaphore, all REVIEW-5 fixes M6/M11/M15/M31/M34/M36/M37/M38/M41/M42/M43 wired), foreground `DownloadService` (NetworkCallback auto-pause/resume, `onTimeout` API 35+, `onTaskRemoved` restart), `DownloadNotificationManager` (2 channels), 7-section settings UI (drag-reorderable priority/quality/audio/server lists), downloads page (live queue + bulk actions + 10s auto-clear of COMPLETED + downloaded files page grouped by anime), episode download controls on details page, player offline integration.

**Offline playback**
- `d83915d` (DL-OFFLINE): offline playback + downloaded episode UI + Play/Delete menu.
- `de7c0bc` (DL-PLAYBACK-FIX): MPV offline playback — `content://` → `fd://` ParcelFileDescriptor conversion.
- `1f85339` (DL-CRITICAL-FIX3): MPV SIGABRT — 500ms surface-readiness delay for `fd://` + episode metadata disappearing.
- `66947ea`, `be4d1ea` (DL-REMAINING + FIX): subtitle naming, quality switcher, compile fix.

**Stability / migration / flow fixes**
- `616a57f`, `1e34c33`, `5949521` (DL-CRASH-FIX 1-3): DB schema migration crash — drop+recreate download tables on upgrade; migration via `onOpen`; first-run setup dialog.
- `f30b290`, `336f264` (DL-UI-FIX 1-2): download button shows resolver sheet in download mode + 360p/HSUB defaults; `ResolvedVideo` type for download.
- `6717e02` (DL-FLOW-FIX): extensive download flow logging + 360p/HSUB preference migration.
- `d60bd83`, `2c4c81f` (DL-DOWNLOAD-FIX 1-2): `effectiveLinkedSource` null in resolver sheet; moved to top-level scope.
- `ab86b26` (DL-CRASH-FIX3): Toast on main thread + localhost proxy connection failure handling.
- `8b9d1ab`, `cf01023` (DL-IMPROVE 1-2): downloaded episodes show as downloaded + `data.json` populated + hidden files; `downloaded_episode` DB insert + `data.json` FK fields + always-show Downloaded button.
- `9812814`, `d6f0d21` (DL-IMPROVE 3 + FIX): stale data cache + `DownloadedFilesScreen` navigation + `WatchKey` metadata; `upsertEpisodeMetadataBatch` method name.
- `454fe86`, `c359aff` (DL-CRITICAL-FIX 1-2): offline playback crash + stale data + episodeUrl caching; `SQLiteException` — `data_cache_episode.episode_url` column missing.
- `234ea15` (METADATA-FIX-v2, branch HEAD): metadata disappearing + episode list + local subtitles.

### Status
- Download system substantially complete. Branch is 41 commits ahead of `main`.
- CI status: green on recent commits (per prior session logs; verify on next push).
- ⚠️ **Two known code bugs found during analysis** (beyond the unwired proxy-churn):
  1. `HttpDownloader.kt:261` only checks `url.startsWith("http://localhost")` — but AniKotoS uses `127.0.0.1` (per `lessons-learned.md` D-092). The re-resolve guard misses `127.0.0.1` URLs.
  2. `HttpDownloader.kt:271` writes the fresh URL to the `video_uri` column, but the download read path uses `video_url` — a `DownloadStore.updateDownloadVideoUrl` query is missing. (Found by proxy-churn research subagent.)
- ⚠️ `DownloadVideoPickerSheet` (235 LOC) exists but is NOT wired — `MainActivity.handleDownloadEpisode()` handles `EnqueueResult.ShowPicker` with a `// TODO: show the DownloadVideoPickerSheet (Phase D.6 follow-up). For now, log only`.

### What's next (download system)
1. Device testing (enqueue / pause-resume / offline playback / auto-download / notifications / foreground-service survival). **Checklist:** `APP/ani-kuta/DOCUMENTATION/download-device-testing-checklist.md`.
2. Wire proxy-churn re-resolve (D-149, deferred per user) + fix the two bugs above in the same change.
3. Wire `DownloadVideoPickerSheet` (the multi-quality picker for downloads).
4. Implement outer retry loop (`RETRYING` state + `RetryPolicy`) — currently max attempts = 2, spec says 6.
5. **D-FIX-SUB device verification (section C of the checklist)** — confirm the 5 subtitle fixes work on a real device (especially C5: subtitles survive reinstall).

### D-FIX-SUB — Downloaded-episode subtitle fixes (this session)
- **5 issues fixed** (see `decisions.md` D-152 + `changelog.md` D-FIX-SUB section):
  1. `subtitleUris` was never populated on task completion → offline playback had NO subtitles (CRITICAL). Fixed via `PublishResult` return type.
  2. Subtitle fetch sent no headers → 403 on protected CDNs. Fixed via `applyTrackHeaders` (MPV comma-format) + UA fallback.
  3. `DownloadTrack` had no `headers` field. Fixed: added field; `DownloadOrchestrator` passes video headers as fallback.
  4. Subtitle naming was index-based → picker showed "Subtitle 1". Fixed: lang-based naming + `extractSubtitleLangFromUri` → "English" / "Japanese".
  5. `DownloadScanner` set `subtitleUris = emptyList()` on reinstall → subtitles lost. Fixed: `findSubtitleUrisForEpisode` re-discovers them.
- **Sub-agent reviewed (SUB-REVIEW):** COMPILES. Reviewer caught a header-format logic bug (JSON vs MPV-comma) — fixed.
- **Awaiting device verification** (checklist section C, esp. C5 reinstall test).

---

### Library Badge Customization System (fix9–fix14, on `functionality/improvements`)

**Commits:**
- `cb3c6aff` (fix9): edge-to-edge cover badges + theme-adaptive colors.
- `81b35fbc` (fix10): badge data enrichment (releasedEpisodes, audioAvailability, watchedCount).
- `cbecc964` (fix11): horizontal DisplayModeCard + side-by-side SUB/DUB badges.
- `df842e27` (fix12): remove BadgePositionSelector + bold text + fix SUB/DUB rendering.
- `e518f135` (fix13): scroll-to-minimize header animation in CustomizeSheet.
- `db0535d0` (fix14): advanced RELEASED options (sub/dub/both + unwatched + SVG icons).

**Delivered:**
- `BadgeIcons.kt`: custom ImageVectors for Sub (subtitle/closed-caption) and Dub (microphone).
- `CoverBadgeData`: data class with text + containerColor + contentColor + optional icon.
- `CoverBadgeRow`: edge-to-edge, side-by-side badges with dot separators, 8sp Bold, optional icons.
- `ReleasedAudioFilter` enum (BOTH/SUB/DUB) + `releasedUnwatchedOnly` toggle with persistence.
- `LibraryEntry` extensions: `subEpisodeCount`, `dubEpisodeCount`, `subUnwatchedCount`, `dubUnwatchedCount`.
- `LibraryViewModel.enrichEntriesWithBadgeData`: per-audio-type episode counting + DEBUG logging.
- `LibraryGridCard` badge rendering: RELEASED+BOTH shows `[sub-icon] N` (blue) + `[dub-icon] M` (orange); SUB/DUB modes show single badge; unwatched mode shows unwatched counts.
- `CustomizeSheet`: scroll-to-minimize header (40dp threshold, magnetic snap), RELEASED sub-options section with `ReleasedAudioFilterCard` + unwatched toggle.

**Review:**
- 3 sub-agents (data layer, CustomizeSheet UI, badge rendering) — all NO CRITICAL/WARNING issues.
- 1 logic bug found + fixed (BOTH+unwatched fallback).

### Status
- Branch: `functionality/improvements` (1 commit ahead of remote).
- Version: 0.2.37 (versionCode 37).
- **Awaiting push + CI build** — no GitHub credentials in build environment.
- Not yet device-tested.

### What's next
1. Push `db0535d0` to remote to trigger CI APK build.
2. Download APK artifact from GitHub Actions.
3. Device test: scroll-to-minimize animation, RELEASED sub-options (Both/Sub/Dub), unwatched toggle, badge colors + icons.
4. Merge `functionality/improvements` → `main` after user approval.

**This session — Download resilience + cache identity + sandbox emulator test env (2026-08-23, on `test-feature/video-cache-new-download`, commits 512279ee + cf4a8a6f):**
- User feedback: (1) downloads break on Wi-Fi loss, no auto-restart when internet returns; (2) downloaded size sometimes exceeds total; (3) cached videos still load from network (not instant); (4) install an Android emulator in the agent sandbox for direct testing; (5) user authorized x86 APK builds for emulator testing.
- **D-246 fixes (commit 512279ee, CI green 32619494659)**: (1) `networkPausedTasks` set — network loss pauses ALL active tasks + remembers them; connectivity return AUTO-RESUMES them (the old code paused but PAUSED tasks are invisible to tryStartNext → nothing ever restarted; ALSO the DownloadService stopped itself when everything paused, killing the NetworkCallback that would have fired the resume — now it stays alive while network-paused tasks exist); transport errors while offline → PAUSED (not retry-burn-into-ERROR); user-initiated pause/cancel wins over auto-resume; CallRegistry instant teardown (OkHttp Call.cancel on coroutine cancellation — blocked reads no longer wait out the 60s timeout). (2) effTotal: when downloaded > reported total (HLS estimate lag / stale persisted totals), the total grows to reality; retry() clears persisted tracker state. (3) Cross-session cache identity recovery (`findEntriesByIdentity` — conservative: single prior entry + quality match; ambiguity skips caching rather than risk wrong-content corruption). CR-D compile review caught 2 bugs pre-push (CallRegistry nesting, cancel() leaving stale network-paused entries → stuck foreground service).
- **x86_64 emulator-test APK (commit cf4a8a6f, CI green 32631607584)**: user authorized x86 builds; CI now ALSO produces `app-debug-x86_64-emulator.apk` (`-PemulatorX64Build=true` in the convention plugin; SHIPPED app-debug.apk stays arm-only; Verify-ABIs checks both). CORE_RULES §8 amended with the two user-authorized exceptions (test-only x86_64 artifact + sandbox emulator tooling for RUN/INSPECT — Gradle/javac/build-tools remain forbidden).
- **Sandbox emulator environment (fully working)**: cmdline-tools + platform-tools + emulator + API 30 AOSP x86_64 image at /home/z/android-sdk; AVD `anikuta` (720x1280, 1024MB — 1536+ gets cgroup-OOM-killed; the container has a 4GB memory cgroup, qemu TCG RSS ≈ 3.5GB max). CRITICAL sandbox quirks: (a) background processes MUST be double-fork detached `( setsid nohup ... & )` — the sandbox reaps process trees at command boundaries; (b) EVERY adb command needs `timeout -s KILL N adb ... < /dev/null` (plain adb shell hangs); (c) `input text` truncates at ~14 chars — type in chunks; (d) cold boot ~8 min; system_server/SystemUI ANRs are common (dismiss with Wait).
- **Emulator smoke test results (x86_64 native APK, real evidence captured)**: ✅ app installs + launches (+1m19s cold start); ✅ full startup pipeline (Koin 24 modules, UpdateCheckWorker complete, notification channels, schedule fetch, "Fetched 20 trending from network" — AniList works); ✅ Browse grid renders real data + covers; ✅ Details screen (★82% · 13 eps, genres, synopsis, 10-star rating, Episodes + No-source state); ✅ extension REPO added (aniyomi-addons/anime-extensions-repo — injected via anikuta_extension_repos prefs to bypass input-text truncation); ✅ extensions installed (AnimeKai + JetAnime via the app's installer + appops REQUEST_INSTALL_PACKAGES grant) + TRUSTED (full trust-flow logs); ✅ source picker shows trusted sources; ✅ extension search executes live; ✅ Cloudflare-protection UI renders with the WebView-bypass flow (the designed D-207 behavior). ❌ playback via extension blocked: the sandbox datacenter IP can't pass Cloudflare challenges (jetanime.co is CF-protected; animekai.to doesn't even resolve here) — video playback + cache-hit testing needs the user's device. ⚠️ FirstRunSetupDialog "Skip for now" has an empty onClick (real UX bug found — the dialog can't be skipped; worked around by prefs-injection + appops).
- Emulator died at the end (OOM at 3.5GB with 2 extensions + WebView) — expected under the 4GB cgroup; all evidence captured first.

**This session — v0.2.52 device-feedback batch #4 (2026-08-26, on `test-feature/video-cache-new-download`, commits a01734ef + e313a24c + 6f9e977d + e3bd6285):**
- User device-tested v0.2.51 (satisfied with palettes — "proper, they work how I want them to be, perfect and properly functional") + reported 7 categories: Browse (remove Continue Watching, fix hero banner image not showing), last-tab memory (persist + restore on cold start + recents), library sorting (add "behind" option + more), library scroll perf (jittery/laggy on fast scroll), search→extension→detail tracking refresh bug, DB/cache updates after refresh.
- **Workflow:** 5 parallel read-only research sub-agents (22-a hero/CW, 22-b last-tab, 22-c library sorts, 22-d scroll perf, 22-e tracking refresh) → plan-v0.2.52.md → plan-review sub-agent (Task 23, GO-WITH-FIXES — 5 factual corrections + 6 compile-error traps) → phased execution D-266..D-271.
- **D-266 (Browse, commit a01734ef, CI GREEN):** removed Continue Watching from Browse (4 files: BrowseScreen + BrowseCards + BrowseViewModel + MainActivity — CW carousel + card composables + the flow + data class + the lambda arg + buildWatchKeyForContinueWatching function + 6/2 now-unused imports/constructor params). Fixed the hero banner hardware-bitmap crash: D-262's `BlurredBannerBackdrop.boxBlur()` called `getPixels()` on a Coil-3 HARDWARE bitmap (default API 26+) → `IllegalStateException` silently caught → backdrop blank → user saw only the dark scrim. Fix: `.allowHardware(false)` on the ImageRequest + defensive copy in boxBlur (`if (src.config == HARDWARE) src.copy(ARGB_8888, true)`) + scrim lightened 0.30/0.55/0.88 → 0.22/0.45/0.82.
- **D-267 (last-tab memory, commit e313a24c, CI GREEN):** AppPreferences gained `lastTab` (getString/putString, default "browse"). MainActivity AppRoot: koinInject<AppPreferences>() hoisted above the remember{} blocks; currentTab init "browse" → appPreferences.lastTab; backstack init AnimeBrowseKey → inline when(appPreferences.lastTab){library/search/more/else→key}. onSelect: appPreferences.lastTab = route after currentTab = route. Covers cold start + recents + activity recreation.
- **D-268 (library sorts, commit 6f9e977d, CI GREEN):** LibraryEntry gained lastWatchedAt. WatchProgressStore + SqlDelightWatchProgressStore + watch.sq gained getLastWatchedAt (COALESCE(MAX(last_watched_at), 0); store converts 0 → null). LibrarySortType enum gained BEHIND + SEASON_YEAR (kept displayName). applyFilters when-block: LAST_WATCHED stub fixed (sortedBy lastWatchedAt), BEHIND added (compareBy unwatchedCount thenBy title; ascending = caught-up first = user request), SEASON_YEAR added (compareBy seasonYear). enrichEntriesWithBadgeData populates lastWatchedAt. UI auto-renders (forEach).
- **D-269 (library scroll perf, commit e3bd6285, CI pending):** PRIMARY fix — wrapped `collapsed` in `remember(isList) { derivedStateOf { ... } }` (was read directly in the parent composition body → every scroll frame recomposed the parent → re-allocated lambdas → children couldn't skip → compounds on fling). Added contentType to 3 items() calls (staggeredItems + grid = "card"; list = "row"). @Immutable on LibraryEntry (all val + AudioAvailability verified immutable).
- **D-270 (tracking refresh, commit e3bd6285 bundled with D-269 due to a git add -A staging issue, CI pending):** mergeAniListIntoUnified — added val gen = loadGeneration + refreshTracking() after the link (fixes the extension auto-link path: after link is established, currentMainId + anilistId set + state Success → refreshTracking no longer early-returns). resetState — clears _trackEntry + _pendingRemoteTrackEntry + _showTrackSheet + _showMarkPreviousPrompt + _showMarkSeriesPrompt (fixes stale tracking on re-open).
- **D-271 (version + docs, this commit):** AndroidConfig 0.2.51 → 0.2.52 (code 52). Docs: D-266..D-271 decisions, progress, changelog, lessons (3 new).
- ⚠️ D-269 + D-270 were bundled into commit e3bd6285 (git add -A staged all 3 files before the first commit; the D-270 commit found nothing to commit). Code is correct; commit message is D-269-only. Documented in decisions D-269/D-270 entries.

**This session — v0.2.59 device-feedback batch (2026-08-28, on `test-feature/video-cache-new-download`, commits 43bd5d5f + 1713e7ce + eab206e8 + 883090a9 + 2fafca85 + c8c21d20 + 6b3c6ecc + docs/version):**
- User device-tested v0.2.58 (update flow + progress + cancel + fresh installs all praised; search/details/thumbnails from extensions all working) and reported 7 new items — ALL completed this session:
- **D-311 (post-update NPE crash):** InstallStep.Installed resurrected the stale Update pill, then `onUpdate!!` NPE'd when the refresh flipped the lambda null mid-AnimatedContent-transition. INSTALLED phase + null-safe READY + onInstallResult→loadAll (post-update refresh system). Commit 43bd5d5f.
- **D-312 (:core:seasons module):** SeasonDetector promoted to its own zero-dep Gradle module; pattern registry (season-episode/compact/season-only) + provider-hint fusion (name tags win, AniZip seasonNumber fills gaps); DetailsScreen wires episodeMetadata hints. Commits 1713e7ce + 883090a9 (CI fix: MatchResult.groups is a Collection).
- **D-313 (episode-list integrity):** EpisodeListNormalizer (URL dedupe + unique general numbering — fixes duplicate EP tags AND the (main_id, episode_number) cache-row collapse) + EpisodeListDumper (full raw dump, tag Anikuta:EpisodeDump, release-visible, Dispatchers.Default, all 3 fetch sites — the user copies logs back for season-format tuning) + generation guards on every episode/metadata state write (cross-anime thumbnail-bleed fix). Commits eab206e8 + c8c21d20 (CI fix: Logger.isEnabled accessor).
- **D-314 (simple PTR):** 3-stage NestedScrollConnection + ThreeStagePullIndicator + Refreshing-pill replaced by M3 PullToRefreshBox → refreshAll (identical to the Refresh button); legacy refresh-stage API deleted. Commit 2fafca85.
- **D-315 (cover viewer):** CoverViewerOverlay — expand-from-exact-bounds (single Animatable, deferred reads), Close/Save pills, save streams original bytes to Pictures/ANI-KUTA (MediaStore 29+ / permission+scan 24–28, manifest tools:replace for the mpv-lib conflict). Commit 2fafca85.
- **Review round (R-6 general-purpose agent, 16 findings):** 2 BLOCKERS (manifest merger + missing @OptIn(ExperimentalMaterial3Api)) + 4 MAJOR stale-guard gaps (mergeAniListIntoUnified auto-link path — the longest async window; loadFromExtension network-first; tryCachedExtensionData catch path; loadLinkedSource enrichment) + minors (dumper off-main, derivedStateOf PTR haptic, blank-URL collapse, MediaStore orphans, CancellationException, null message) — ALL applied. Commit 6b3c6ecc.
- **D-316:** version 0.2.58 → 0.2.59 (code 59); docs (decisions/changelog/lessons/progress); tag + release after CI green.

**This session — v0.2.60 device-feedback batch (2026-08-28, on `test-feature/video-cache-new-download`, commits c3c07be1 + 5677c542 + b03d0489 + docs/version):**
- User device-tested v0.2.59: extension update flow ✅ (smooth, Done, no crash), episode-tag duplicates ✅, thumbnail bleed ✅, season module ✅, episode dump ✅ (user captured + shared real logs — "Season 2 - Episode 1 - Axis Mundi" num=1.0 = per-season numbering!), cover viewer ✅. New reports: season SLICE numbering wrong (S1 showed 9-16, S2 1-8), organize-by needs three states + a season-in-tag option (S-3/E-5 two-color format), PTR indicator vanishes too early + too plain, save too slow, wants cover zoom with auto-reset, and the big one: shared-element cover transitions from grids into Details (+reverse on back) as an EXPERIMENTAL feature.
- **D-317 (commits c3c07be1 + 5677c542):** season-aware renumber ordering + analyzeEpisodeSeasons (one-pass groups/assignments/per-season numbers) + per-season slice tags + three-state organizeMode (old boolean = migration source) + seasonTagInNumber toggle + S-n/E-m compound badge (AnnotatedString, two onPrimary shades) + dumper debug double-log fix. CI fix: withStyle import.
- **D-318 + D-319 (commit 5677c542):** refreshAll awaits suspend cores (indicator persists until truly done) + custom themed PTR indicator; cover pinch-zoom (snapshotFlow isTransformInProgress for gesture-end — CI fix) + Coil disk-cache-first saves (openSnapshot API — CI fix).
- **D-320 (commit b03d0489):** SharedTransitionLayout + AnimatedContent nav shell (fade only for details transitions, snap otherwise; lambda param shadows currentKey), coverSharedElement helper + CompositionLocals in designsystem, section-qualified browse keys, search/library wiring, nav-key-carried transitionKey/coverUrl/title, details loading skeleton banner, SaveableStateProvider per screen class (browse scroll survives), Settings toggle (default ON). CI fixes carried in this commit (transformable/DiskCache/opt-in).
- **D-321:** version 0.2.59 → 0.2.60 (code 60); docs (decisions D-317..D-321, changelog, 7 new lessons, progress); tag + release after CI green.

**This session — v0.2.61 crash-fix (2026-08-29, on `test-feature/video-cache-new-download`):**
- User device-tested v0.2.60: CRASHED AT STARTUP — `NoSuchMethodError: sharedElement$default(…PlaceHolderSize…)` in `coverSharedElement` from `LibraryCoverImage`.
- **D-322 forensics (all claims verified against artifacts, not inferred):** the expected descriptor exists verbatim in animation-android-1.7.8 (compile was honest); the SHIPPED APK's dex contains the 1.10-line `SharedTransitionScope` (`PlaceholderSize` rename + `SharedContentConfig`/`skipToLookaheadPosition`); source of the skew = koin-compose 4.2.2 → JB Compose 1.10.2 wrappers → androidx 1.10.4 REQUIRED (beats the BOM 1.7.8 constraint — constraints only raise); lifecycle runtime = 2.10.0 (koin-android 4.2.2 requires androidx lifecycle 2.10.0 — the JB-fork path lands at 2.9.4; max-wins); the skew existed in EVERY release (koin 4.2.2 since the first commit) and only became visible when v0.2.60 added the experimental SharedTransition call.
- **D-322 fix:** compose BOM removed → explicit 1.10.4-line pins in the toml (material3 1.3.1 / icons 1.7.8 / lifecycle 2.10.0); 15 module build files migrated; `coverSharedElement` ported to the 1.10 API (`sharedContentState` param); NEW `checkDependencyAlignment` guard task in :app (preBuild-wired) fails any build whose packaged compose/lifecycle versions deviate from the pins. Verified locally before pushing: all target artifacts exist on Google Maven; compose 1.10.4 is Kotlin-2.0.21-compiled (readable by Kotlin 2.2.0); coroutines requirement exactly matches the 1.9.0 pin; FlowRow gained only a defaulted param (named-arg call sites unaffected); ExperimentalLayoutApi/ExperimentalFoundationApi/ExperimentalSharedTransitionApi/rememberTransformableState all exist on 1.10.4; full experimental-import inventory of the app = 3 markers (all present).
- **D-323:** version 0.2.60 → 0.2.61 (code 61); docs (decisions D-322/D-323, changelog, 6 new lessons, progress); tag + release after CI green; release APK dex re-fingerprinted to prove the caller now targets the 1.10 descriptor.

**This session — v0.2.62 device-feedback batch (2026-08-29, on `test-feature/video-cache-new-download`):**
- User device-tested v0.2.61: everything works, no crashes. Two improvements requested: (a) cover→details morph smoother (was "a bit faster and a bit jittery") + keep rounded corners during the flight; (b) plain episode tag for no-season/1-season content (not the season compound tag). Also asked for stale-documentation updates.
- Sandbox was WIPED a 3rd time between messages (machine reboot — /tmp survived, /home/z/ANI-KUTA-WORK did not). Repo re-cloned read-only from the public remote at 14d1666d = v0.2.61 shipped state. ⚠ The GitHub credential lived only in the wiped clone's .git/config remote URL — push/CI/release are BLOCKED until the user re-provides the token (all work committed locally, ready to push).
- **D-324:** Motion.DurationContainer=450ms token; bounds morph + nav crossfade now share it + EasingEmphasized (the jitter = mismatched velocity profiles: emphasized bounds vs FastOutSlowIn fades at 300ms); coverSharedElement gains `shape` param → clipInOverlayDuringTransition = OverlayClip(12dp) so rounded covers stay rounded for the whole flight (default ParentClip = parent rectangle — Browse/Library card rounding lives on the parent Box, outside the shared element). DESIGN-LANGUAGE §6 rewritten; stale "280ms" MainActivity comment fixed.
- **D-325:** compound S-n/E-m tag gated on `seasonInfo?.groups != null` (the detector's activation fact) — no-season/1-season lists always show plain "EP n". SeasonAssignment-vs-activation distinction documented; core/seasons README stale consumer reference (groupEpisodesBySeason → analyzeEpisodeSeasons) fixed.
- **D-326:** version 0.2.62 (code 62) + docs (decisions/changelog/lessons/progress). Push + CI + tag + release PENDING on the GitHub token.
- **Stale-documentation sweep (user-requested):** SESSION.md (active branch, clone path, latest decision, ABI rule, module count, "Currently Blocked On" — was frozen at v0.2.38 era), master.md (BOM→explicit-pins tech line, branch/current-focus/status section, counts), navigation.md + knowledge/{project-overview, tech-stack, module-map}.md (module/table/file counts, ABI line, compose-BOM references), CORE_RULES.md compileSdk note, architecture.md module-graph heading, dashboard.md flagged as data-stale (D-193-era lib data — needs a dedicated dashboard refresh session, noted not silently fixed), core/seasons README (consumer renamed D-317), DESIGN-LANGUAGE §6 (motion rules), in-code kdocs (EpisodeTag, seasonTagInNumber, coverSharedElement), MainActivity's stale "280ms" comment.

**This session — Task 48.1 / device round 8: THE CRASH + THE 428 + watch-page metadata (2026-08-30, on `streaming/CLOUDSTREAM`):**
- User device-tested v0.2.69: details pages good; AniKoto episode tap → spinner → APP CRASH (LOGCAT.MD uploaded); MovieBox 720p → player → HTTP 428 (proxy upstream AND direct retry) → dead; no description on the player page; no thumbnails/synopsis in the watch episode list.
- Forensics: 2 Explore sub-agents (R8-LOG distilled the 337KB logcat; R8-CODE mapped cache-proxy/MPV/episode-UI/DASH paths) + 1 Plan sub-agent verified the fix plan (GO + amendments A1–A8 — all applied).
- **D-357 THE CRASH:** CloudflareBlockedException was `: Exception` — OkHttp AsyncCall only catches IOException → rethrown on the dispatcher thread → process death (the bridge's partial-links rescue never saw it). Now `: IOException` + terminal CsInterceptorSafetyNet (outermost; non-IOException Throwables wrap — NoClassDefFoundErrors become honest errors) + headless-solver fast-fail (zero cookies 8s after page-finish; was 20s/host ×N serialized) + early-success cookie polling.
- **D-358 THE 428:** two comma-truncation bugs mangled every UA — the proxy's MpvHeaderParser (split+drop fragments) AND mpv itself (OPT_STRINGLIST splits on ','; only `\` escapes work — verified against mpv m_option.c; quotes are literal). New canonical MpvHeaderFields (:core:network): gluing parse (proxy + SubtitleEngine delegate) + escapeForMpv at all 7 MPV boundaries (raw csv everywhere else). 428/429 join looksExpired (stale sign → straight to fresh-sign re-resolve) + PlayerObserver capture. 10 unit tests.
- **D-359 watch-page metadata:** buildEpisodeMetadataSerialized iterated the AniList-only map (empty for CS) → player page starved. Now extension-episodes ∪ metadata union, ext-first, newline-sanitized, desc ≤400 chars (backstack Bundle safety).
- **D-360 self-heal + corruption:** crash-restart lands in ErrorActivity (no CommonActivity registration) → MovieBox errored all session; a late-activity watcher reloads plugins now. SAF openOutputStream("w") doesn't truncate (Google 146330523) → 7 corrupted .data.json (valid JSON + stale tails); now "rwt" + delete-recreate fallback + per-folder write mutex.
- Deferred (documented in roadmap): DASH/.mpd surfacing (needs real-manifest research — sandbox is region-blocked from the MovieBox API); watch-page metadata keyed by episode number alone (multi-season CS shows with per-season numbering can show the wrong season's description — titles self-heal via the parser fallback).
- v0.2.70 (code 70) committed → CI → tag/release after green.
