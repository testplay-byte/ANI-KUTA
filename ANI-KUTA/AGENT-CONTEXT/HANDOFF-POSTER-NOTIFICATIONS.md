# HANDOFF — the poster-notification + preview issues (rounds 46-47, v1.1.9/v1.1.10 device findings)

> Written by the round-46/47 agent for the NEXT agent. Read this alongside
> `SESSION.md` + `CORE_RULES.md`. The user tested v1.1.10 on device and the
> features below DO NOT work yet. This file is the full technical picture:
> what was built, what breaks, root-cause hypotheses, and where to dig.

## 1. What exists (built in rounds 46-47, all on `feature/ads-return-pill`)

| Piece | File(s) | State |
|---|---|---|
| Poster art provider seam | `core/notifications/.../NotificationArtProvider.kt` | interface, fine |
| NotificationManager poster path | `core/notifications/.../NotificationManager.kt` — `postNotification` (BigPictureStyle when `artProvider` returns a bitmap; BigText fallback), `postPosterNotification` (the test/preview primitive), small icon = `R.drawable.ic_notification` | compiles; the FALLBACK is what fires on device |
| Poster config keys | `core/preferences/.../NotificationPreferences.kt` — `posterEnabled`, `posterBackgroundSource` ("banner"/"cover"), `posterShowEpisodeTitle`, `posterShowEpisodeThumbnail`, `posterShowAudioBadge`, `posterShowBranding` | fine |
| The composer | `app/.../notifications/EpisodeBannerComposer.kt` — 1024×576 Canvas composition (art center-crop + scrim + title + "EPISODE N" lime + episode title + SUB/DUB chip + episode thumbnail chip + branding) | compiles; **returns null on device** (the exception is logged — see §2) |
| Demo picker | `app/.../notifications/EpisodeDemoPicker.kt` — random library content WITH cached episodes + its latest episode | untested on device |
| Test notifications | `app/.../notifications/EpisodeNotificationTester.kt` (feed-first → library fallback) + `DelayedPosterTestWorker.kt` (5-min staggered second post) | posts arrive but WITHOUT banners on device (the same composer failure) |
| Preview page | `app/.../settings/NotificationPosterSettingsScreen.kt` — live preview (feed-first → library fallback → "No episodes available yet" state) + composition toggles + shuffle | **shows "Couldn't load the preview art" on device** — the composer threw; the exception text is swallowed into a log line |
| Notification icon | `core/notifications/src/main/res/drawable/ic_notification.xml` (kawaii mouth, monochrome) | works |
| Updates-apply fix | `core/updates/UpdateStore.observeUnacknowledgedCountForMain` + `feature/anime-details/impl/.../DetailsViewModel.kt` D-475 observer (auto `refreshEpisodesListNow()` when unacknowledged rows rise) | untested on device — verify with a real update detection |
| Settings IA | `app/.../settings/SettingsScreen.kt` (one "Updates & Notifications" row) + `NotificationsSettingsScreen.kt` (Sub/Dub/Both SegmentedToggle + poster nav row + the new test row) | the user wants the Sub/Dub/Both block RESTRUCTURED (see §4) |
| Library badges | `feature/anime-library/impl/.../LibraryScreen.kt` `CoverBadgeRow` (soft pills, `BadgePosition.TOP_CENTER` default in `LibraryViewModel.kt`) | user approved the style, then asked to CENTER AT TOP (done in v1.1.10) — re-verify on device |

## 2. THE two on-device failures (v1.1.10) — diagnose before changing anything

### 2a. The preview shows "Couldn't load the preview art"
`failed = true` means `EpisodeBannerComposer.buildBanner` THREW (the only
paths returning null are the catch blocks — compose() itself always returns
a bitmap). The exception is logged via `Logger.e/w(TAG…)` with tag
`Anikuta:App:BannerComposer`. **First step: reproduce + capture logcat** (the
user tests via Android Studio's Logcat panel — CORE_RULES §20 filter format):

```
tag:Anikuta:App:BannerComposer | tag:Anikuta:App:NotifTester | tag:Anikuta:Core:Notifications
```

Candidate root causes to check, in order of likelihood:
1. **Main-thread DB/IO inside `produceState`**: the preview's `produceState`
   coroutine runs on the MAIN dispatcher and calls `updateStore.getAllUpdates`,
   `contentRepository.getMainEntryByMainId`, `dataCacheRepository.getEpisodeMetadata`
   (blocking `executeAsList`) + Coil network loads. If StrictMode or the DB
   lock bites, it throws. FIX DIRECTION: move the whole composition into
   `withContext(Dispatchers.IO)` inside the composer, or wrap the preview's
   produceState body in `withContext(Dispatchers.IO)`.
2. **Coil `imageLoader` access**: `context.imageLoader` (coil3) inside the
   composer resolves the app's singleton — verify this is non-null from an
   application-context-only path (the proven pattern in
   `UpdateProgressNotifierImpl.loadCoverAsync` uses the SAME access from a
   worker; compare and mirror it exactly).
3. **`DataSourceExtras.fromJson`** on a null/malformed `dataExtraJson` — the
   parser is `ignoreUnknownKeys` + runCatching-wrapped upstream, unlikely.
4. The `maxBy { it.episodeNumber }` on an empty list is guarded (skip), but
   `episodes.maxBy` vs the Float `episodeNumber` — check nullability.

### 2b. The test notifications post as PLAIN TEXT (no banner)
Same composer exception → the BigText fallback fires. Fixing 2a fixes this.
Additionally the user asked WHY the network is needed at all: the art comes
from remote URLs. Improvement direction: prefer Coil's disk cache (500 MB,
already configured in `AnikutaApp`'s ImageLoaderFactory) and consider
persisting the composed banner (filesDir) per (mainId, episode) so
re-tests are instant and offline.

### 2c. The user's explicit product asks for the NEXT agent
1. **The preview must work offline-first** — locally available art (Coil disk
   cache / any locally stored cover) before touching the network.
2. **The preview + test posts must pick**: feed row first; else a RANDOM
   library content with cached episodes (latest episode), re-rolled per open
   (`EpisodeDemoPicker.pickRandom` — verify it returns content on the user's
   7-anime library) + the "Shuffle preview" action re-rolls; never unlinked/
   episode-less content; the "No episodes available yet" state otherwise.
3. **The test notifications**: the first immediately, the SECOND 5 minutes
   later (DelayedPosterTestWorker — verify the 5-min delay actually fires;
   the user saw both arrive simultaneously in v1.1.9 — in v1.1.10 the
   staggered worker exists but was untested on device).
4. **The episode-type (Sub/Dub/Both) block on the Notifications screen must
   look EXACTLY like the Updates screen's**: title + description at the top,
   the full-width SegmentedToggle BELOW (not in a row's trailing slot).
   Mirror `UpdatesSettingsScreen.kt` L293-323's structure.
5. **The library badges**: the user approved the soft-pill restyle and asked
   to CENTER AT TOP (TOP_CENTER shipped) — verify on device, then polish
   further per fresh feedback (they said "not handled properly as I hoped"
   about v1.1.9's state, then approved the pills in v1.1.10, then asked for
   centering — get a screenshot-level confirmation this time).

## 3. The update-apply fix (D-475) — verify, don't assume
`DetailsViewModel`'s D-475 observer auto-refreshes when unacknowledged
episode_update rows rise for the open anime. It has NEVER been device-tested.
Verify: add library anime → force a check (Updates settings → "Check for
updates now") while the details page is open → the episode list should grow
by itself. Watch for: the observer loop (own writes are pre-acknowledged, so
it shouldn't), and the refresh not running while `_isRefreshing` is true.

## 4. CI state (D-472) — do not regress
build-apk.yml no longer triggers on `release/**` or docs-only pushes; at most
2 runs per release (1 feature push + 1 tag run). The unit-test steps are gone
(D-465) unless the user asks. The What's New release body = the annotated
tag's body (D-466) — ALWAYS write user-facing bullets in the tag annotation,
never commit logs.

## 5. Branch/version state at handoff
- `feature/ads-return-pill` = the full v1.1.4..v1.1.10 line (CI green).
- `release/1.1.10` + tag `v1.1.10` = the current LIVE debug release
  (1.1.10/10110, ani-kuta-v1.1.10-debug-arm64-v8a.apk).
- `main` has NONE of this — the user has NOT confirmed the merge.
- The version bump lives ONLY on the release branches (D-425 discipline).
- Release notes = the tag annotation body (D-466) — the workflow turns them
  into the GitHub release body the app displays.

## 6. RESOLVED (round 49, D-491) — the §2a root-cause hypotheses were NOT the cause
The v1.1.11 device logcat (the user's round) delivered the definitive answer:
`IllegalArgumentException: Software rendering doesn't support hardware
bitmaps` at `EpisodeBannerComposer.drawCenterCrop` ← compose ← buildBanner.
Coil decodes into `Bitmap.Config.HARDWARE` by default on API 26+; a HARDWARE
bitmap cannot be drawn on the composer's SOFTWARE canvas
(`Bitmap.createBitmap`) — the first draw threw, the catch nulled the banner,
and the UI blamed the connection. This doc's §2a hypotheses (main-thread
blocking DB reads) were real-but-orthogonal: D-486 fixed them, and the
device STILL failed — the bitmap config was the missing layer. The fix:
`bitmapConfig(ARGB_8888)` at the loadBitmap choke point (+ the engine's
`isCacheValueValidForHardware` memory-cache validation) + `ensureSoftwareSafe()`
copy-or-null as the never-crash last line. The "toBitmap converts hardware
bitmaps" comment that justified the old code was false — verified against
the coil3 3.0.4 bytecode (BitmapImage is returned as-is). Lesson for the
next agent: a catch-only-null failure mode HIDES the real exception class —
round 48's exception-class-in-log discipline is what finally cracked this.


## 7. ROUND 50 (D-493..D-496) — the presentation round: what changed after the pipeline went green
The v1.1.12 device round approved the PIPELINE (posters render end-to-end) and flagged the PRESENTATION. What the next agent must know:
- **The canvas is 1024×440 (≈21:9)** — `EpisodeBannerComposer.CANVAS_WIDTH/HEIGHT` (PUBLIC consts; the companion is public on purpose — Kotlin forbids cross-file access through a private companion's facade). The preview box reads them for its aspectRatio.
- **The layout is zone-anchored** (text from the top; the chip row + branding on a shared bottom baseline; the thumbnail aspect-preserved in a 210×330 box) — never re-introduce a flow layout that lets text push elements off-canvas.
- **Never use a BitmapShader without a local matrix for the cover-crop** — the v1.1.12 "glitched background" was a shader sampling at native size + CLAMP edge smear. The current `drawCoverFit` (src→dst rect math) is the keeper; background loads pass `coil3.size.Scale.FILL`.
- **The badge truth table**: the engine's `audioVariant` ∈ {"sub", "dub", "unknown"}; "unknown" means both-variants-or-unparsed. DEMO paths (preview feed-first, `normalizeForDemo`; the picker's random variant) normalize to sub/dub so the demo always shows a chip; the REAL path passes the engine value through untouched (unknown → no chip, honest — real posts only ever carry sub/dub anyway).
- **The preview's selection flow**: `selectPreviewContent` (file-level in NotificationPosterSettingsScreen.kt) — screen open: feed-first → library-random; SHUFFLE: library-random EXCLUDING the on-stage id (one-shot `shuffleExclude` ref, consumed at the top of the produceState pass); TOGGLE FLIPS: re-render the CACHED `PreviewSelection` (never re-select). The refs/cache are deliberately NOT produceState keys.
- **The tester spec** (EpisodeNotificationTester): the feed is NOT consulted; random distinct library entries (ANY entry qualifies); cycling when the library is small; `pickPureDemo()` when the library is EMPTY (blank mainId → the styled no-art stage; the title rides KEY_TITLE in the work data). The tester ALWAYS posts ≥1 when notifications are permitted.
- **Episode labels**: `episodeLabel` in NotificationManager's companion + the composer's `episodeLabel` — "EP 12"/"EP 12.5", never "EP 12.0".

## 8. ROUND 50 RELEASED (v1.1.13) + the release-first process (D-497/D-498)
- **v1.1.13 is LIVE**: release/1.1.13 = the feature head `24be155b` + ONE bump commit (`c66db803`, 1.1.13/10113 + the 42-RELEASE-1.1.13-BRANCH-POINT.md record); tag `v1.1.13`; Release APK run 35456726478 GREEN (the only CI run the release cost); stable, `--latest`, `ani-kuta-v1.1.13-debug-arm64-v8a.apk` + `SHA256SUMS.txt`. The user's device round happens ON this release (in-app update, 10113 > 10112).
- **D-498 THE STANDING PROCESS (the user's instruction — "Remember to handle the future changes exactly like this too")**: after an implementation push is CI-green, cut the release branch + tag IMMEDIATELY. The release IS the verification build; there is NO device round on a feature build before the tag and NO separate verification builds. A failed release run is diagnosed from the release CI logs; the ≤2-runs budget (D-472) = the implementation push + the release run.
- Both lines (feature + release) carry the final docs; docs-only pushes trigger no CI.

## 9. ROUND 51 (D-499..D-501) — banner v3, the dead seam, the in-app banner, the planned shuffle
The v1.1.13 round approved the pipeline and asked for presentation depth + two features. What the next agent must know:
- **The canvas is 1024×400 (2.56:1)**; the layout is MIRRORED: the episode thumbnail is a LEFT-side CARD (fixed 16:9 400×225 box, `drawThumbBox` cover-crops ANY source into it — the box's shape always wins), rounded + dark border + two-layer fake-blur shadow; the text column hangs top-RIGHT and the scrim is right-anchored. Never re-add a right-side thumbnail without re-mirroring the scrim.
- **The episode number is a TAG, not a hero line**: the tag row `[EP n] [SUB] [DUB]` sits under the ≤2-line title (EP chip = lime fill + dark label; audio chips = dark fill + lime label; "both" → both chips). All text carries the soft dark shadow layer (light-art safety is a user rule now).
- **The chip truth lives in the FEED**: `resolveAudioVariant` (composer) unions `UpdateStore.getVariantsForEpisode` (DISTINCT audio_variant for mainId+episode — the engine inserts SUB and DUB as separate rows) with the passed variant. Never trust a single row's variant alone; never let a DB failure fail the banner (degrade to the passed variant).
- **THE SEAM**: `single<NotificationArtProvider> { get<EpisodeBannerComposer>() }` in AnikutaApp is LOAD-BEARING — Koin indexes by concrete type, so without that explicit binding `getOrNull<NotificationArtProvider>()` in NotificationsModule resolves NULL and every system notification silently falls back to plain text (this shipped broken from D-477 until D-500 — the device never showed notification banners). Keep the binding; keep the same pattern for ANY new nullable interface seam.
- **The in-app banner**: InAppBannerController (core/notifications, replay=0 SharedFlow) ← NotificationManager emits after nm.notify() on BOTH poster paths (composed bitmap + texts) ← InAppBannerHost (AppRoot overlay stack, repeatOnLifecycle(STARTED) — foreground-only by construction; current/visible state split so the exit animation plays). Auto-dismiss 5s; tap dismiss.
- **The Shuffle is a DECK now**: EpisodeDemoPicker.ShuffleDeck (shuffled queue, refill + no-immediate-repeat head-swap, @Synchronized next, stale-id pruning); pickRandomPlanned(deck, exclude) with the D-494 no-op fallback; screen-open stays feed-first → random. The deck instance lives in the screen's remember{}.
- **v1.1.14 is LIVE** (D-502): release/1.1.14 = feature head `5141d1f1` + ONE bump commit (2f26f467, 1.1.14/10114 + the 43-RELEASE record); Release APK run 35462212024 GREEN; the user's device round happens ON this release.
- **Round 52 (D-503..D-506) — the clean-notification + Poster-Studio round**: the CLEAN banner rule (when a banner composes, BigPictureStyle sets ONLY bigPicture/bigLargeIcon and the builder title/text are "" — no text above the poster, both post paths; a failed compose keeps the old text card); the REAL heads-up popup (channel `anikuta_new_episodes_high` at IMPORTANCE_HIGH — the old DEFAULT channel never pops heads-up; legacy deleted in ensureChannel; PRIORITY_HIGH builders); the D-500 in-app overlay DELETED (InAppBannerController/InAppBannerHost gone — the user rejected the in-app card: "I wanted the banner to be shown as a notification"); the 30s test stagger (`index * 30L SECONDS` in EpisodeNotificationTester).
- **The chip truth is now THREE layers**: passed variant ∪ feed's per-variant rows (D-499) ∪ the EPISODE CACHE row parsed with `parseAudioAvailability(scanlator, sourceName ?: title)` — the same parser the details page's per-episode pills use. Both → both chips; one → one; nothing → no chip.
- **The scrims are GONE** — D-504 removed drawScrims entirely; the art renders full-brightness and the D-499 text shadows are the only readability carrier. Do not reintroduce a scrim without the user asking.
- **The thumb evidence chain**: episode still (12s, then one 8s retry) → extras.coverUrlLarge → cover → banner (6s each) — `loadThumbBitmap`. The left card survives null thumbnail_url rows; an empty chain still composes without the card.
- **THE POSTER STUDIO (D-505)**: `PosterLayoutConfig` (kotlinx-serialization JSON in `NotificationPreferences.posterLayoutJson` = `notif_poster_layout_json`) + `PosterCanvasMetrics` (the shared 1024×400 geometry) + `PosterDrawing` (the extracted draw primitives — composer AND studio render through the SAME code) + `EpisodeBannerComposer.loadEditorArt` + public `resolveAudioVariant`. The screen `PosterCustomizeScreen` (app:settings) forces SENSOR_LANDSCAPE (DisposableEffect restores the previous orientation), left panel ~32% (elements + visibility + 12-swatch palette + style reset + the bottom size slider), right = the 2.56:1 editable preview (one keyless `pointerInput(Unit)` — a state key would restart the detector mid-drag; awaitEachGesture: 1 finger drag, 2 fingers pinch, topmost-first hit-test; magnetic snap = 8px grid + 14px magnetism to margins/centers/other elements' center lines with lime guides; the pinch→drag handoff re-anchors prev). FLOW SEEDING: the first edit seeds untouched elements at their flow anchors and flips `customized=true`; the composer renders ABSOLUTE mode from the saved JSON (the v1.1.14 prefs still gate audio badge/episode title/thumbnail). SAVE: persist + `Logger.i("Anikuta:App:PosterStudio") { "POSTER LAYOUT SAVED: <full json>" }` — THE USER READS THIS LOG TO HAND US THEIR LAYOUT AS THE FUTURE DEFAULT. Reset restores the factory flow layout.
- **The shuffle feedback (D-506)**: preview pulse + icon 360° spin + banner Crossfade on every shuffle tap; D-494 semantics untouched.
- **v1.1.15 is the next release** (D-498 standing process): cut release/1.1.15 from the green feature head as ONE commit (1.1.15/10115 + the 44-RELEASE record) the moment CI is green; the user's device round happens ON the release APK.
- **v1.1.15 is LIVE** (D-507): release/1.1.15 = feature head `048f9422` + ONE bump commit (5974113a, 1.1.15/10115 + the 44-RELEASE record); implementation run 35465964501 GREEN (after the one-fix-push for the studio's wrong composer import), Release APK run 35466505113 GREEN on the FIRST run; the user's device round happens ON this release — the checklist in progress.md (chips both / thumbs / full-brightness / banner-only notification / real heads-up + 30s / the studio / shuffle animation / offline).

## 10. ROUND 53 (D-508..D-517) — the studio obeys the finger; the notification speaks again
The v1.1.15 device round approved the studio and asked for nine refinements. What the next agent must know:
- **THE DRAG MODEL IS ABSOLUTE (D-508)** — never go back to per-event delta accumulation through `origin()`: the D-503 loop wrote the SNAPPED position back every frame, so any sub-14px movement was swallowed forever (the user's "slow swipe never escapes the magnet"). `moveElementTo(kind, rawX, rawY)` receives the raw target computed from the SEGMENT anchor (the element's origin + the pointer position captured ONCE at gesture down / pinch start / pinch→drag handoff); the snapped value never feeds the accumulation. The studio's clamps MIRROR the composer's (texts x ≤ W−120, y ≤ H−lineHeight×scale; chips/cards measured) — keep them in lockstep with composeAbsolute or WYSIWYG dies.
- **The rich style layer (D-511)**: `PosterElementLayout` now carries fontKey/bold(Boolean?)/italic/shadow/chipBgArgb/labelOverride — ALL with factory-look defaults (old saved JSON parses untouched; `bold=null` = the element's factory weight). Rendering goes through `PosterDrawing.typefaceFor(fontKey, bold, italic, fallbackBoldDefault)` — ONE resolver for composer AND studio. The audio row's comma-split override lives in the composer's `audioChipLabels` and the studio's `chipLabelsFor`.
- **The studio is a DESIGN CANVAS (D-513)**: BOTH tags render regardless of availability (the real banner stays truthful via the resolved variant); the canvas opens on the settings screen's EXACT selection (`PosterCustomizeKey` = @Serializable data class carrying mainId/title/episodeNumber/variant; `onOpenCustomize: (PreviewSelection?) -> Unit`). `PreviewSelection` is PUBLIC (an internal type in a public composable's parameter does not compile — the round's one CI error).
- **The scrim is BACK, SMARTER (D-514)**: `PosterDrawing.applyAdaptiveScrim` measures the background's 24×10 luminance and veils proportionally (0% ≤0.40 → 46% at 1.00) — called on the composer AND the studio right after the cover-crop. The D-504 "no darkening" rule is SUPERSEDED; the D-499 shadows stay (now a per-element toggle).
- **The notification speaks (D-516)**: when a banner composes, the builder title = `headline(title)` (40 chars + "…") and the text = `shortDescription(title)` ("New episode available for " + the first two words) on BOTH post paths; the BigPicture style stays clean (no bigContentTitle/summaryText). The D-503 empty-title rule is SUPERSEDED. Android cannot force-expand a heads-up — title+text is the honest collapsed-state fix.
- **wrappedLines hard-ellipsizes EVERY line (D-512)** — a single long word no longer overflows its column; the studio's elementRect uses the SAME wrap math (its width floor is 24px, mirroring the composer's floorless W−x−margin bounded by the x ≤ W−120 clamp).
- **v1.1.16 is LIVE** (D-517): release/1.1.16 = feature head `3cb5adcc` + ONE bump commit (b87f7fd0, 1.1.16/10116 + the 45-RELEASE record); implementation run 35470221593 GREEN (after the one-fix-push for the PreviewSelection visibility), Release APK run 35470742809 GREEN on the FIRST run; the user's device round happens ON this release — the 9-point checklist in progress.md (slow drag / slider below / real buttons / rich options / long titles / same content + both tags / smart darkening / title + description on the heads-up / old layout still loads).

## 11. ROUND 54 (D-518..D-521) — the studio gets a shell, the title gets aware, the shuffle always shuffles
- **The shell (D-518):** the round-54 device round found the studio's header UNDER the status bar (zero insets on a forced-landscape edge-to-edge screen) with an unclickable Save. Root Column → statusBarsPadding; full-width header (back · title · labelled Reset · labelled primary Save); body Row carries navigationBarsPadding (either landscape edge). Left panel = tonal SectionCards; right pane = the untouched gesture canvas + a carded slider strip. DSL trap recorded: LazyListScope `items{}` cannot run inside a plain composable lambda — the ELEMENTS card uses a plain `for`.
- **The aware columns (D-519):** `PosterDrawing.awareWrapWidth` — a text element's wrap width ends before the left edge of any visible neighbour sharing its band. Band is TWO-PASS (lines counted at full width first — one-line titles are never shrunk by chips below them). Neighbour rects carry left/top/bottom ONLY (no measurement → composer/studio identical). AWARE_MIN_WIDTH=120 floor; candidates below it are skipped, never collapse the column. The composer clamps the awareness anchors exactly as it renders (legacy JSON safety); the studio declares awarenessRects/awareWidthFor BEFORE elementRect (Kotlin local funs are not visible above their declaration — the round's one CI-validated blocker) and passes the hoisted per-recomposition widths into drawPoster. THE CRASH FIX: the composer's thumbnail render clamp was the last unguarded coerceIn — saved thumb scale > ~1.78 (the studio's slider allows 2.0) threw IllegalArgumentException → every real notification silently fell back to text. All clamps now range-guarded.
- **The shuffle (D-520):** the D-494 exclude handshake no-op'd when the on-stage id was still null (a tap before the first compose finished reused the cached selection — pulse with no change). Now an explicit shufflePending flag consumed first on Main per pass (mid-flight taps cancel + the next pass re-reads), "" sentinel keeps the deck path alive with nothing on stage, and a `shuffling` state (finally-cleared, cancellation-safe) drives an indeterminate rail on the preview's bottom edge while the new banner composes.
- **The text (D-521):** shortDescription() = "New Episode" on both banner paths (headline keeps the content name).
- Released as v1.1.17 per D-498 (the 46-RELEASE record lives on the release branch). Full round record: memory/progress.md Round 54; decisions D-518..D-521.
