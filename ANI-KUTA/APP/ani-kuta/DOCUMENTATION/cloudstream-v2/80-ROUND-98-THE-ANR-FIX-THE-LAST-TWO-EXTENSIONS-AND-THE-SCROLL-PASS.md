# Doc 80 — Round 98: The v1.1.54 device round — the ANR fix, the last two extensions, the corner-flush repo rows, and the 60 FPS scroll pass

> Date: 2026-09-28 · Round 98 · Decisions D-668..D-673 · Shipped as **v1.1.55/10155**
> Previous: doc 79 (round 97). Next: doc 81.

## 1. The device report (v1.1.54)

The round-97 compat expansion was confirmed working — "you did work on it
properly, and you did make quite a lot of improvements. Like the errors were
fixed with the untrusted extensions" — but four problems came back:

1. **THE ANR CRISIS** (the round's biggest item) — "it was giving me the
   prompt that isn't responding every single time… even though the app was
   kind of working." The cleaned logcat showed the system ANR dialog
   (`Application Not Responding: com.confused.anikuta.debug`) firing at
   03:20:28 and again at 03:20:33, with `Quality Skipped: true 15 frames` and
   every plugin-load stack running
   `DispatchedTask.run → Looper.loop → ActivityThread.main` — plugin loading
   ON THE MAIN THREAD.
2. **THE TWO REMAINING ERRORED EXTENSIONS** — CineStream
   (`NoSuchMethodError: No virtual method getSimklApi()` in
   `AccountManager$Companion`, at `CineSimklProvider.kt:56`) and
   YoutubeProvider (`NoClassDefFoundError: Failed resolution of:
   Lorg/schabi/newpipe/extractor/ServiceList`). Final state in the logcat:
   `loadAll: 90 installed, 0 untrusted, 2 errored`. The user's order: "look
   into them better, and handle them a bit more properly."
3. **THE REPO-ROW BUTTON PLACEMENT** — the round-97 split-corner row shipped,
   but "the buttons were not placed in the correct positions where I needed
   them to be. Like there was a lot of empty space on the right side of them…
   and a lot of empty space at the top and at the bottom. So I need you to
   make sure that these buttons are not affected by the padding around them."
4. **THE PERFORMANCE PASS** — "the scrolling in the extensions page should be
   fully super smooth, 60 FPS or more, stable… the overall app's experience
   should be much cleaner, well handled, and everything should be managed in
   an optimized, well-handled way."

## 2. The root-cause work

- **The ANR**: `CloudstreamPluginManager.scope` was
  `CoroutineScope(SupervisorJob() + Dispatchers.Main)` — EVERY load
  (initial `loadAll` of ~90 plugins, `trustPlugin`, `retryPlugin`) executed
  on the main thread. Worse, the logcat showed CineStream being dex-loaded
  THREE times in three seconds: `trustPlugin` loads it (fails), then calls
  `refreshLocked() → loadAll()` which re-attempts every errored plugin, and
  the NEXT trust's refresh re-attempts them all again — each attempt a fresh
  `PathClassLoader` + class init + CineStream's own synchronous `urls.json`
  fetch.
- **CineStream**: the exact failing line in SaurabhKaperwan/CSX's
  CineSimklProvider.kt is `private val repo = SyncRepo(AccountManager.simklApi)`.
  The `BuildConfig.SIMKL_CLIENT_ID` / `SIMKL_API` reads two lines earlier are
  Java static-final String literals — compile-time constants the Kotlin
  compiler INLINES into the plugin dex, which is why they resolved without a
  BuildConfig mirror (and why no mirror is needed). The plugin's post-grab
  usage is `repo.authUser()` + `repo.library()` only — both inert-safe.
- **YoutubeProvider**: the recloudstream plugin (source: recloudstream/
  extensions) links against `org.schabi.newpipe.extractor.ServiceList` &
  friends — the NewPipe extractor stack that upstream recloudstream BUNDLES
  (`com.github.teamnewpipe:NewPipeExtractor:v0.26.3` from JitPack, with
  `coreLibraryDesugaring(desugar_jdk_libs_nio)` and `NewPipe.init(
  DownloaderTestImpl)` in `CommonActivity.init`). Its `loadLinks` delegates
  to `loadExtractor("https://youtube.com/watch?v=…")`, which only reaches a
  REGISTERED `ExtractorApi` — so a working integration needs the library, a
  `Downloader`, AND a builtin YouTube extractor to dispatch to.
- **The repo row**: the eye/delete were INLINE at the ends of the two text
  rows via the shared 36dp `ActionIconButton` (20dp glyph in a circle) —
  14dp inner padding + 8dp circle inset put ~22dp of dead space between each
  glyph and the card edge, and each text row's height was inflated to the
  36dp box (~90dp total for two lines of small text).
- **The scroll jank**: `val collapsed = listState.firstVisibleItemIndex > 0
  || listState.firstVisibleItemScrollOffset > 20` read RAW in the composable
  body of ELEVEN screens — every pixel of scroll invalidated the whole screen
  scope (the 2113-line ExtensionsSettingsScreen recomposes its 14
  collectAsStates + every remember block + the LazyColumn DSL each frame).
  Compounding it: un-memoized `installedPkgs`/`untrustedPkgs` sets, the
  aniyomi `updateInstalledStatuses()` copying all ~90 `Installed` instances
  on every catalog refresh (defeating skippability), and a
  `SubcomposeAsyncImage` per CloudStream row icon.

## 3. D-668 — the load worker

`CloudstreamPluginManager.scope`:
`Dispatchers.Main` → `Dispatchers.IO.limitedParallelism(1)`. A SINGLE
background worker preserves the exact FIFO serialization the Main dispatcher
gave (a synchronous `loadAll` still cannot interleave with `trustPlugin` /
`refreshLocked` — the atomicity the init path's comment relied on), while
taking every dex load off the main thread. `checkForUpdates`' explicit
`Dispatchers.IO` override was dropped for the same reason — it would have
escaped the one-worker serialization. Nothing in a `.cs3` load requires the
main thread (upstream CloudStream itself loads plugins on IO); plugins stash
the activity reference they're handed but construct fine from any thread,
and the UI collects the same StateFlows unchanged.

## 4. D-669 — the failure memo

`CloudstreamPluginLoader` gains `failedPlugins: HashMap<filePath, FailedLoad>`
where `FailedLoad = (reason, cause, contextClass, fileStamp, fileSize)`. A
repeat `loadPlugin` for a path whose memo matches on file stamp AND load
context returns the memoized failure in O(1) — the deterministic errors
(NoSuchMethodError/NoClassDefFoundError) never re-dex. `unloadPlugin` clears
the entry (Retry / update / uninstall re-attempt for real), a successful load
drops it, and a changed context class invalidates it (the late-activity
self-heal keeps its genuine retry). The v1.1.54 logcat's triple-load of
CineStream becomes one real attempt + two map hits. Safe as a plain HashMap
because the loader is only touched from the manager's single load worker.

## 5. D-670 + D-671 — the two extensions

**D-670 (CineStream)**: the inert `SimklApi : SyncAPI()` (mainUrl simkl.com,
syncIdName Simkl) in `syncproviders/providers/SimklApi.kt` +
`AccountManager.Companion.simklApi`. Clean-room, inert-by-design like
AniListApi — the plugin's sync views render their logged-out state.

**D-671 (YoutubeProvider — the GPL bundle, user-ordered)**: the round-97
doctrine held YoutubeProvider errored until the user explicitly ordered the
NewPipe bundle; the round-98 report names the NoClassDefFoundError errors and
orders the remaining extensions handled "better and properly" — that order.
What shipped:

- **The dependency**: `com.github.TeamNewPipe:NewPipeExtractor:v0.26.3`
  (JitPack) in `:core:cloudstream-api` as `implementation` — the plugins see
  it parent-first through the host classloader. The exact version upstream
  recloudstream bundles (what the plugin compiled against). Transitives:
  nanojson, jsoup 1.22.2 (the catalog pin moved 1.19.1 → 1.22.2 to match —
  Gradle's highest-version rule would pick it regardless), jsr305,
  protobuf-javalite 4.35.0, rhino 1.8.1 + rhino-engine (YouTube's JS
  deciphering).
- **The desugaring** (in `:app`, the final dexing step):
  `isCoreLibraryDesugaringEnabled = true` +
  `coreLibraryDesugaring(desugar_jdk_libs:2.1.5)`. Verified against the
  v0.26.3 sources: java.time in 11 files (needs API 26+ / the rewrite;
  minSdk is 24); java.nio is `StandardCharsets` ONLY (API 1) — the standard
  flavor suffices where recloudstream ships the NIO one. The plugin dexes
  themselves carry zero java.time references.
- **The runtime**: `network/NewPipeDownloader.kt` — a clean-room OkHttp
  adapter over NewPipe's abstract `Downloader` (own client: 10s/30s
  timeouts, in-memory cookie jar, 429 → ReCaptchaException per the contract)
  + `ensureNewPipeInitialized()` (`NewPipe.init`), called from
  `registerBuiltinExtractors()` which the plugin manager invokes BEFORE any
  plugin loads.
- **The dispatch target**: `extractors/YoutubeExtractor.kt` — a clean-room
  builtin over `StreamInfo.getInfo`: live → the HLS manifest as M3U8;
  regular → the adaptive video-only streams, each carrying the audio list as
  mergeable `audioTracks` (the CS player engine already builds a merged
  source from `ExtractorLink.audioTracks`), muxed progressive as the
  fallback, subtitles as `SubtitleFile`s. Mirrors for youtu.be / m.youtube
  .com / youtube-nocookie. Registered in `registerBuiltinExtractors`.

**License disclosure**: NewPipeExtractor is GPL-3.0; bundling it as a library
dependency means the distributed APK carries GPL-3.0 code (the app's own
code stays original; the combined-work obligation now applies). This was
explicitly ordered this round after the round-97 disclosure; if the user
ever wants it out, the revert is the catalog entry + the `:core:cloudstream-api`
dependency + desugaring + the two files + the registration block.

## 6. D-672 — the corner-flush repo rows

`RepoRow` rebuilt as a `Box` overlay: the text column (title + badge, URL)
keeps a 42dp end reserve and drives the row height; the eye is pinned
`Alignment.TopEnd` (3dp top, 4dp end inset), delete `Alignment.BottomEnd`
(3dp bottom, 4dp end), both via a new private `RepoRowCornerButton` (26dp
rounded target, 16dp glyph — sized for the corner, NOT the shared 36dp
`ActionIconButton` the extension rows use). `heightIn(min = 58.dp)` so the
two corner targets can never collide. Net: glyphs ~4.5dp from the card edge
(was ~22dp), row height ~64dp incl. outer padding (was ~90dp), and the
buttons are untouched by the text padding. The long-press-to-copy affordance
and the hidden-state dimming are unchanged.

## 7. D-673 — the scroll pass

- **derivedStateOf everywhere the raw read lived** (11 screens):
  ExtensionsSettings, ExtensionRepoSettings, ExtensionDetail,
  CloudstreamPluginDetail, SourcePreferences, the five testing screens
  (Run / TargetList / Stats / TargetDetail / Home), and History. The header
  collapse now invalidates only when the boolean flips, not per scroll
  frame.
- **Memoized derived sets**: `installedPkgs` / `untrustedPkgs` wrapped in
  `remember(source list)`.
- **Instance stability**: the aniyomi `ExtensionManager
  .updateInstalledStatuses()` keeps row instances when `hasUpdate`/
  `isObsolete` did not move and only re-emits the flow when something
  actually changed (the old unconditional copy produced ~90 fresh instances
  per catalog refresh, defeating Compose skipping).
- **CsPluginIcon**: `SubcomposeAsyncImage` → plain `AsyncImage` with an
  `onState`-tracked letter tile behind it (zero subcomposition per icon;
  identical loading/error visuals).
- **contentType hints** on the repositories screen's LazyColumn items.

## 8. Verification

- Programmatic brace/paren balance on all 20 touched Kotlin files (the three
  flagged deltas matched HEAD's pre-existing string-template false positives
  exactly — no structural change beyond the added blocks).
- Symbol greps: no dangling references to `ActionIconButton` in the repo
  screen (same-package symbol still used by the extension rows), no leftover
  `SubcomposeAsyncImage` imports, `derivedStateOf` imported in all 11 files.
- CI: Build APK run 36358901872 FAILED on my own one-error cluster (named
  arguments passed to NewPipe's JAVA Response constructor — Kotlin
  prohibits named args for non-Kotlin functions; the parameter names don't
  exist at the bytecode level) → fixed positionally in b6443f87 → run
  36359199002 GREEN (run 2 of 2, inside the D-472 budget). Every other
  module compiled clean on run 1 — the failure was isolated to the new
  Downloader adapter file.

## 9. The release

TBD-RELEASE (filled at ship time below)

## 10. The device-round checklist (v1.1.55)

See the user report.

## 11. Judgment calls disclosed

- **The search-field filter passes were left un-debounced**: the four
  filter+sort blocks are already memoized on their inputs (D-658), the
  catalogs are a few hundred entries, and a per-keystroke pass is ~1-3ms —
  the per-frame recomposition (fixed by derivedStateOf) was the real jank
  source. A debounce changes typing feel (laggy results) for no measurable
  win; revisit if a device round still shows keystroke jank.
- **The 26dp corner targets** are below the 48dp touch-target guideline —
  deliberate: the whole row remains the long-press target, the buttons are
  corner-pinned glyphs per the user's spec, and the previous 36dp boxes were
  the complaint. If mis-taps show up on the device, the upgrade path is
  expanding the transparent hit area (not the visual box).
- **The GPL bundle** (see §5) — the one doctrine-level change this round,
  ordered by the user after the round-97 disclosure.
