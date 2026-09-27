# Doc 79 — Round 97: The v1.1.53 device round — the coroutines wall, the patient tests, the aligned durations, and the hidden repositories

> Date: 2026-09-27 · Round 97 · Decisions D-662..D-667 · Shipped as **v1.1.54/10154**
> Previous: doc 78 (round 96). Next: doc 80.

## 1. The device report (v1.1.53)

The round-96 work confirmed on the device — the status dots, the full-details
page, the picker: "everything is implemented exactly like how I wanted them to
be implemented." Four asks came back:

1. **THE DOT SIZE** — "Although the size of the dot could be made bigger. I
   would prefer for you to handle it like that."
2. **THE DURATION ALIGNMENT** — the full-details stage bars' durations "shown
   on the bars on the right side of it… you can align them properly with each
   other instead of aligning them to the right."
3. **THE FALSE TEST FAILURES** — "it tried to search for a content on the
   search page. It failed badly. Even though the actual extension was working…
   it was showing the results on the search page. And also it said that it did
   not test the details page because the search failed, even though the details
   page of it was working properly too."
4. **THE CLOUDSTREAM LOAD FAILURES** (the round's biggest item) — 22 of 92
   plugins sat in the error section; the user shared a cleaned logcat where
   trusting BanglaPlex / Movies4u / UHDmoviesProvider each died with
   `NoSuchMethodError: runBlockingK`, plus grouped
   `NoClassDefFoundError`s for `syncproviders/SyncRepo`,
   `cloudstream3/MainActivity` and `newpipe/extractor/ServiceList`.
   Plus: "improve the console logcat logging… in the debug version… in the
   actual release version, we will remove these debugging functionalities."
5. **THE HIDDEN REPOSITORIES** — a per-repository show/hide toggle: "If I hide
   any of the repositories, then the results from that repository will not be
   shown… only the not downloaded results will not be shown. The downloaded
   results will continue to show exactly how they would." The delete button
   moves to the very bottom right; the show/hide button sits at the very top
   right — "the very right corner will be split into two parts, the top one
   and the bottom one."

## 2. The root-cause work (the dex census)

Before touching the compat layer, the four failing .cs3 files from the user's
repo (phisher98/cloudstream-extensions-phisher, `builds` branch — BanglaPlex,
StreamPlay, TorraStream, Ultima, RingZ) were downloaded and their `classes.dex`
parsed with a ~100-line python reader (string pool + type ids + method ids +
field ids + the raw bytecode patterns for check-cast/instance-of). The output
is the EXACT referenced surface — no guessing from upstream sources:

- **BanglaPlex / RingZ** (and 15 more of the 22): reference ONLY the standard
  surface plus `kotlinx.coroutines.BuildersKt.runBlockingK` /
  `runBlockingK$default` — coroutines-only failures.
- **StreamPlay / TorraStream** (and CineStream per the user's logcat):
  `syncproviders/SyncRepo.<init>(SyncAPI)` + `.authUser()` + `.library-IoAF18A(...)`,
  `AccountManager$Companion.getAniListApi()`, the `AniListApi$…` nested model
  getters, `plugins/PluginManager` (INSTANCE + `getPluginsOnline` +
  `unloadPlugin`), `AesHelper.cryptoAESHandler$default`, and
  `APIHolder.getCaptchaToken(String,String,String,Continuation)`.
- **Ultima**: `MainActivity` + `MainActivity$Companion`
  (`afterPluginsLoadedEvent` / `bookmarksUpdatedEvent` / `reloadLibraryEvent`),
  `PluginManager.getPluginPath/getPlugins/loadSinglePlugin`,
  `PluginWrapper.getPlugin/getRepositoryData`, `RepositoryManager.getRepoPlugins/
  getRepositories`, `HomeViewModel$Companion.getResumeWatching(Continuation)`,
  `RepositoryData.getUrl`, `AppContextUtils.setDefaultFocus$default`,
  `DataStoreHelper$ResumeWatchingResult.getId/getParentId`,
  `DataStore.getDefaultSharedPrefs/getSharedPrefs` — plus FOUR `instance-of`
  instructions against `MainActivity` and ZERO hard casts.
- **The mangled name**: `library-IoAF18A` is Kotlin's inline-class mangling for
  `suspend fun library(): Result<LibraryMetadata?>` — VERIFIED EMPIRICALLY by
  compiling exactly that declaration through the Kotlin Playground
  (api.kotlinlang.org, 2.2.0) and reflecting the JVM names: the declaration
  below reproduces `library-IoAF18A` bit-for-bit.

**YoutubeProvider** (also in the user's error list): its `org.schabi.newpipe.
extractor.ServiceList` resolution failure is NOT fixable inside the clean-room
doctrine — NewPipeExtractor is GPL-3.0, and this project deliberately does not
vendor or depend on GPL libraries (doc 23 §3). It stays in the error section
with an honest reason line (and the new loadAll roll makes that reason visible
without opening the row). Bundling it would need an explicit user decision.

## 3. D-662 — the coroutines bump (1.9.0 → 1.11.0)

**Root cause:** the current phisher98 repo builds against coroutines 1.11.x.
`runBlockingK` / `runBlockingK$default` exist ONLY from 1.11.0 — verified by
downloading the Maven artifacts and grepping the class bytes: **1.10.2 does
NOT contain `runBlockingK`; 1.11.0 does.** Our bundled 1.9.0 therefore failed
every plugin that touched it with `NoSuchMethodError` — 17 of the 22 errored
plugins in the user's logcat.

**The fix:** `kotlinxCoroutines = "1.11.0"` in the version catalog. Notes:

- 1.11.0 requires kotlin-stdlib 2.2.20 (Gradle resolves it above our 2.2.0 pin
  — same 2.2 metadata family, forward-compatible for the 2.2.0 compiler).
- Binary compatibility with 1.9 consumers is retained (coroutines follows
  semver; every earlier consumer in the app — Compose, Koin, SQLDelight,
  the aniyomi extension system — runs unchanged).
- 21 of the 22 errored plugins become loadable with D-662 + D-663 together.

## 4. D-663 — the compat surface expansion (clean-room, census-sized)

All new declarations mirror the census's interop facts; all implementations
are original ANI-KUTA code (the clean-room protocol, doc 23 §3). New files in
`:core:cloudstream-api`:

| File | Contents |
|---|---|
| `syncproviders/AuthAPI.kt` | `AuthUser`, `AuthToken`, `AuthData` (data shapes) |
| `syncproviders/SyncAPI.kt` | the open contract + `LibraryList` / `LibraryMetadata` / `LibraryItem` / `SyncSearchResult` / `AbstractSyncStatus` / `SyncStatus` / `SyncResult` |
| `syncproviders/SyncRepo.kt` | the safe wrapper — `<init>(SyncAPI)`, `authUser()`, `library()` reproducing `library-IoAF18A`, updateStatus/status/load |
| `syncproviders/AccountManager.kt` | the abstract anchor + companion's `aniListApi` |
| `syncproviders/providers/AniListApi.kt` | the inert API + the ten nested AniList GraphQL models the census reads |
| `plugins/PluginManager.kt` | the inert singleton (`pluginsOnline`/`plugins`/`loadSinglePlugin`/`unloadPlugin`/`getPluginPath`) |
| `plugins/RepositoryManager.kt` | the inert repo manager + `PluginWrapper` |
| `ui/settings/extensions/RepositoryData.kt` | the repo model + `REPOSITORIES_KEY` |
| `ui/home/HomeViewModel.kt` | the companion's `suspend fun getResumeWatching()` |
| `utils/DataStoreHelper.kt` | `PosDur` + `ResumeWatchingResult` (implements `SearchResponse`) |
| `utils/AppContextUtils.kt` | `AlertDialog.setDefaultFocus` (inert — touch layout) |
| `extractors/helper/AesHelper.kt` | **real** AES-CBC: the OpenSSL EVP_BytesToKey (MD5, 1 iteration) KDF on `javax.crypto` — StreamPlay's encrypted hosters actually decrypt |
| `ui/SyncWatchType.kt` | the watch-state enum `AbstractSyncStatus` references |

Plus three edits to existing files:

- **`MainActivity.kt` (the facade file)** gained `open class MainActivity :
  androidx.appcompat.app.AppCompatActivity()` with the companion's three
  `Event<Boolean>` anchors. Upstream's MainActivity is the whole app UI; ours
  is an EMPTY ancestor — the census touches only the companion and uses
  `instance-of`, never instance methods.
- **The app's own `MainActivity` now subclasses it** (`class MainActivity :
  com.lagradost.cloudstream3.MainActivity()`) — same AppCompatActivity
  ancestry as before (zero behavior change for the app), but every
  `context is com.lagradost.cloudstream3.MainActivity` in plugin bytecode now
  answers TRUE for the live activity plugins receive in `Plugin.load(context)`.
  `:app` gained the direct `:core:cloudstream-api` dependency.
- **`MainAPI.kt`'s `APIHolder`** gained `suspend fun getCaptchaToken(url, key,
  referer)` — the Google reCAPTCHA anchor/reload flow over our own `app`
  client (clean-room from the observable protocol), the member StreamPlay's
  census calls.

**Inert-by-design (disclosed):** `PluginManager`/`RepositoryManager`/
`HomeViewModel`/`DataStoreHelper`/`AccountManager` answer empty/null shapes —
the compat library cannot reach the app's real data layer, and the census
plugins only use them for their own settings/repository views, which render
their empty states. The AniList sync APIs answer the upstream "not logged in"
shapes. The app's own AniList integration is untouched.

## 5. D-664 — the debug-logging pass

- **Full stack traces**: the loader's failure log now passes the throwable
  (`Logger.e(TAG, t)`) — the v1.1.53 round had to diagnose `runBlockingK`
  from a one-line message; logcat now shows the failing class/method/line for
  every plugin load failure. Same for the trust-path failure warning.
- **The entry-class line**: `Loading entry class <name> from <file>` before
  instantiation — pinpoints WHERE a load dies.
- **The loadAll errored roll**: one WARN block listing every errored plugin
  with its one-line reason (`loadAll errored roll: BanglaPlex →
  NoSuchMethodError: … | Ultima → …`) — the whole set diagnosable at a glance.
- **Doctrine unchanged (D-362)**: DEBUG-level lines in debug builds only;
  INFO+ in release. The round's ask — "proper logging… in the debug version…
  in the release version we will remove these debugging functionalities" — is
  the existing contract, restated.

## 6. D-665 — the false-failure fixes (patient budgets + the interrupt poison)

**Root cause of "the test failed but the search page works":** the Cloudflare
WebView solve takes up to SOLVE_TIMEOUT_MS = 20s; the HOME_PAGE budget was
EXACTLY 20s and each search attempt 12s. A challenge-gated site therefore:
(1) timed out HOME mid-solve; (2) the test-isolation thread's
`shutdownNow()` interrupt landed on the solver's `latch.await`, which the
interceptor RECORDED as a failed solve — poisoning the host for
FAILED_SOLVE_COOLDOWN_MS = 60s; (3) every SEARCH phrase then failed instantly
with `CloudflareBlockedException: recent solve failed, retry later`; (4) the
engine's honest gate marked DETAILS "Not run — Search/Home page failed".
Meanwhile the app's real search page has no such deadline — the same solve
finished, cached clearance, results showed. Exactly the user's report.

**Fixes:**

1. **The interrupt is not a failure** (`WebViewResolver`): an
   `InterruptedException` from `solveViaWebView` now SKIPS the failed-solves
   cooldown (throws `CloudflareBlockedException("solve interrupted")`
   instead). The main-thread WebView keeps running to its own watchdog and
   lands its cookies in the system CookieManager, where the NEXT attempt's
   manual-jar merge picks them up — the ladder self-heals.
2. **HOME_PAGE 20s → 45s** — sized around solve(20s) + page fetch.
3. **SEARCH attempt 12s → 25s, kind budget 80s → 130s** (4 × 25s + 30s
   slack). Warm sites still exit the ladder on the first phrase in seconds.

## 7. D-666 — the two visual refinements

- **The status dots: 9dp → 13dp** (both tabs; the spacer before the dot grew
  6dp → 8dp) — "the size of the dot could be made bigger"; still a dot, not
  a badge.
- **The aligned duration column**: each stage-timing row is now
  `[track (weight 1f)] + [fixed-width zone, badge START-aligned]` — one tidy
  column of durations sharing the same left edge, instead of badges pinned to
  the full-width track's far-right edge. The zone width derives from the
  chip's own sp (`maxOf(52.dp, 9.sp.toDp() * 5f + 16.dp)` — font-scale-proof,
  the D-659 rule applied to width); the badge is now a solid `surfaceVariant`
  tag with `onSurface` text since it no longer rides a colored bar. The D-637
  motion (start-gated reveal, live growth, renormalization) is untouched.

## 8. D-667 — the hidden repositories

- **The flag**: `ExtensionRepo.hidden` / `CloudstreamRepo.hidden` (default
  false) — persisted in the existing prefs-JSON stores, backward-compatible
  (old rows load with the default). `setHidden(…)` on both repositories.
- **The filter**: the Extensions page's Available sections (both tabs) filter
  entries whose `repoUrl` belongs to a hidden repository. The available list
  already excludes installed/untrusted rows, so this is EXACTLY the user's
  rule: hidden ⇒ only the not-downloaded entries vanish; installed extensions
  render untouched — updates included (the update check still reads every
  repo; hiding is display-only).
- **The split-corner row**: the Repositories screen's row is now two lines —
  the title line ends in the SHOW/HIDE eye (36dp `ActionIconButton`,
  `Visibility`/`VisibilityOff`), the URL line ends in DELETE. A hidden repo
  dims its text to 55% (actions stay full-strength — the eye is the way
  back). The round-82 long-press-to-copy stays on the whole row; the CS
  delete confirmation flow is unchanged. The dead `deleteRequiresConfirm`
  parameter was retired in the rework.

## 9. Verification

- Brace/paren balance on all 30 touched/new files (a string-first stripper —
  the comment-first variant eats URLs' `//` and false-flags).
- Module greps: zero dangling `deleteRequiresConfirm`; every new import
  cross-checked against its target file; `Event<T>`/`atomicListOf`/
  `parseJson`/`base64*` confirmed present.
- The `library-IoAF18A` mangled name verified via the Kotlin Playground
  before writing the declaration.
- The coroutines artifact inspection (1.10.2 vs 1.11.0) is reproducible from
  Maven Central.

## 10. The release

Per the D-565 loop: implementation + this record + the ledger committed on
the mainline (0eaed454 + 8c5d854d) → Build APK run 36351323517 FAILED on two
of the round's own errors (the duplicate getCaptchaToken — the census grep
was case-sensitive against the camelCase name and missed APIHolder's existing
inert stub — and PosDur without @Serializable) → fixed in 6e12936b → run
**36351641064 GREEN** (run 2 of 2, inside the D-472 ≤2 budget, disclosed) →
`release/1.1.54` cut from the green head → the version bump (10120→10154 /
1.1.20→1.1.54, commit 67b5c2c8) rides the branch (D-430) → tag `v1.1.54` →
Release APK run **36351937411 GREEN** → **v1.1.54 LIVE** (published
2026-09-27T21:33:05Z, arm64-v8a debug APK 68.6 MB + SHA256SUMS.txt — verified
via the API) → the in-app updater picks it up.

## 11. The device-round checklist (v1.1.54)

1. **The 22 errored plugins**: re-enter the Extensions page (or hit Retry on
   one) — BanglaPlex, Movies4u, UHDmoviesProvider, RingZ, Cinefreak, DudeFilms,
   Fibwatch, FourKHDHub, HDhub4u, Hdmovie2, Hindmoviez, Zinkmovies, XDMovies,
   Toonstream, Tamilblasters, Pmsm, IStreamFlare, StreamPlay, TorraStream,
   CineStream and Ultima should now LOAD (trust them; their sources should
   appear in the search picker). YoutubeProvider is expected to STAY errored
   (NewPipe, not bundled — say the word if you want that discussed).
2. **The status dots** — visibly bigger (13dp) on both tabs.
3. **The stage timings** — the durations now form one aligned column just
   right of the bars (same left edge on every row).
4. **The patient tests** — re-run the extension that false-failed: the
   Cloudflare-gated site should pass home + search (the first phrase may take
   up to ~25s while the challenge solves; later ones are fast), and details
   should run.
5. **The repo screen** — the eye at the top right, delete at the bottom
   right; hide a repo → its Available entries vanish from BOTH tabs while its
   installed extensions (and their update badges) stay; unhide → they return.
6. **Logcat (debug build)** — `loadAll errored roll:` one-liner + full stack
   traces on any load failure.
