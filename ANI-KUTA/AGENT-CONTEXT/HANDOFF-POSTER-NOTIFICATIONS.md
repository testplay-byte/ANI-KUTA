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
