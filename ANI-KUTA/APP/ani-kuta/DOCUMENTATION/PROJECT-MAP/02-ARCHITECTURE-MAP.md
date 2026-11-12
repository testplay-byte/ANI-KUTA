# 02 — ARCHITECTURE MAP (59 modules; verified 2026-11-13)

> The dependency rule (CORE_RULES §5): **feature IMPLs may depend on sibling APIS; only :app
> depends on feature IMPLs.** The layering: app → feature.impl → feature.api / data / core.
> Verify anytime: `grep -c '^include(' settings.gradle.kts` (= 59).

## 1. The module groups (the load order matches the dependency direction)

### core/ (34) — the pure-logic + platform seams
| Group | Modules |
|---|---|
| Foundation | common (Logger), preferences (PreferenceStore + the typed prefs), designsystem (theme/components), navigation-api (NavKey) |
| Data | database (SQLDelight 30 tables), content (ContentRepository/Resolver — the identity spine), watch-progress (the progress store + the session recorder + EpisodeKeys), data-cache, metadata |
| Content sources | source-api (the Aniyomi surface), provider-api, cloudstream-api (the vendored CS3 compat), network |
| Media | player (MPV host), player-mpv-lib, cs-player (the ExoPlayer-host CS engine), playback-cache, video-resolver |
| Features' engines | download, notifications, updates (the smart check engine), schedule, app-update, ads, backup, torrent (round 143 — REMOVABLE), ratings, share, tracker-api, tracker-anilist, anilist, seasons, smart-matcher, activity-tracker, debug-api |
| UI support | — designsystem carries the shared components (BottomNavBar, SettingsGroupCard, SmoothCenterScroll, AppToast, the episode/player row renderers) |

### data/ (2) — the extension ecosystems
- `data/extension` — the Aniyomi APK world: ExtensionManager (install/trust/uninstall), the
  installer/receiver, the sources → the unified bridge.
- `data/cloudstream` — the JS plugin world: CloudstreamPluginManager, the bridge
  (CloudstreamAnimeSourceBridge — the synthetic CS source ids), CloudstreamLinkResolver (the
  episode → CsVideoLink pipeline; the torrent passthrough flag lives here, D-858).

### feature/ (22 = 13 features, api+impl where noted)
anime-browse, anime-search, anime-library, anime-details, anime-history, watch (MPV),
cs-watch, download, updates, extensions-settings, onboarding (the wizard — single module),
debug-bubble. api modules carry the NavKeys + the cross-feature contracts.

### app/ (1) — the shell
MainActivity (the backstack + the 48-branch NavKey dispatch), AnikutaApp (Koin + the startup
sweeps + the crash handler), the settings screens (SettingsScreen hub + ~20 pages), the
profile, MoreScreen, the notification banner composer (EpisodeBannerComposer), the wizard's
config content slots (onboarding/WizardConfigContent.kt), PendingNavigation (the
notification/share deep-link handshake).

## 2. The three request flows (the most-linked paths)

**Tap-to-play (CS extension):** Library/History/Profile tap → `openDetailsByMainId`
(MainActivity) → AnimeDetailsKey → DetailsScreen → episode click → resolver sheet
(CsResolveSheet) → `CloudstreamLinkResolver.resolve(provider, data, allowTorrentLinks)` →
CsVideoLink list → the user picks → `CsWatchViewModel.selectLink` → (TORRENT?
`TorrentGateway.prepare` → localhost stream URL) → `deliverPlay` → CsPlayerEngine.

**Tap-to-play (Aniyomi extension):** DetailsScreen → resolver sheet → video-resolver
(ResolvedVideosRegistry) → the extractor chain → Video → WatchScreen (MPV) `loadfile`.

**Watch tracking:** both players → `WatchProgressStore.save` (10s tick + the gates) +
`WatchSessionRecorder` (per-streak rows) → watch_progress + watch_session → the Profile
(watch flow / day sheet / time DNA), History, Continue Watching.

## 3. The identity system (the spine everything joins on)

`main_entry.main_id` = the synthetic UUID-ish key minted at first sight of a content. The
axes: `data_source_type='anilist'` (data_source_ref_id = the AniList id) vs the extension
axis (content_details.extension_id TEXT canonical + source_id INTEGER derived, D-795/B7).
The display truth: main_entry.title (validated writer, D-797). Every other table hangs off
main_id. **The FK web**: library_item/content_details + 12 more tables cascade to main_entry
(dormant — deleteMainEntry is dead code); watch_progress/watch_session were DECOUPLED in
v4 (D-862 — tracking survives removal). The removal contract: removeFromLibrary deletes
ONLY library_item.

## 4. The navigation model (no NavHost!)

MainActivity hand-rolls it: `mutableStateListOf<NavKey>` + `AnimatedContent` over
`when (currentKey)` (48 branches) + the bottom bar for the 4 root tabs (Browse/Library/
Search/More). Keys are @Serializable (persistable — but the backstack is NOT persisted
today, a known gap). The deep links (notifications/share) route through PendingNavigation.
