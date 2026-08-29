# 09 — Video Playing & the Download Pipeline

> **Scope**: what happens AFTER `loadLinks` has produced `ExtractorLink`s — how the
> CloudStream (CS3) app turns resolved links into playback and downloads: player
> architecture (Media3), MediaItem/source construction (headers/referer/DRM), the
> link & quality selection UX, the subtitle pipeline, watch-state & resume, skip-intro
> stamps, preview thumbnails, Chromecast, and the full download pipeline
> (queue → service → parallel/HLS fetching → playback of downloaded files).
>
> **Companion docs** — this file *defers* where earlier docs already covered the ground:
> - `05-data-models.md` §7.1–7.5 (`SubtitleFile`, `AudioFile`, `ExtractorLink` ALL-fields,
>   `DrmExtractorLink`, `ExtractorLinkPlayList`), §7.8 (bridge models: `ResultEpisode`,
>   `RepoLinkGenerator`, `VideoLink`, `VideoState`, `SubtitleData`)
> - `08-video-loading-extractors.md` — the whole link *resolution* pipeline:
>   `loadLinks` contract, extractor registry, `RepoLinkGenerator` caching (20-min,
>   saturated short-circuit), `PlayerGeneratorViewModel` `VideoState` streaming,
>   `sortLinks`/quality profiles, `extractorData`/`extractorVerifierJob`.
> This doc starts where doc 08's §7 sequence ends: the user (or autoplay) has a
> `VideoLink` and it must become pixels / bytes on disk.
>
> **Citation keys**
> - `APP/<path>` = `app/src/main/java/com/lagradost/cloudstream3/<path>`
> - `LIB/<path>` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/<path>`
> - `GRADLE` = `gradle/libs.versions.toml`, `APPGRADLE` = `app/build.gradle.kts`
> - `AK/<path>` = ANI-KUTA app tree (`/home/z/ANI-KUTA-WORK/ANI-KUTA/ANI-KUTA/APP/ani-kuta/…`)
> - Markers: `[verified]` read in source · `[docs]` from a prior doc, spot-checked ·
>   `[inferred]` reasoned, not directly observable.

---

## 1. Player architecture

### 1.1 Player library & versions

CS3 plays everything with **androidx Media3 (ExoPlayer) 1.9.3** — not the legacy
`com.google.android.exoplayer2` artifact [verified]:

```toml
# gradle/libs.versions.toml
media3 = "1.9.3"                                # GRADLE:40
nextlibMedia3 = "1.9.3-0.12.0"                  # GRADLE:43
previewseekbarMedia3 = "1.1.1.0"                # GRADLE:48
media3 = ["media3-cast", "media3-common", "media3-container", "media3-datasource-cronet",
          "media3-datasource-okhttp", "media3-exoplayer", "media3-exoplayer-dash",
          "media3-exoplayer-hls", "media3-session", "media3-ui"]   # GRADLE:153
```

Pulled in via `implementation(libs.bundles.media3)` (`APPGRADLE:246`) plus
`io.github.anilbeesetti:nextlib-media3ext` + `nextlib-mediainfo` (FFmpeg software
codecs, `GRADLE:122-123,155`) and `com.github.rubensousa:previewseekbar-media3`
(seekbar preview thumbnails, `APPGRADLE:266`). Notably there is **no
`media3-exoplayer-hls`-less fallback and no VLC/MPV** — the single engine is ExoPlayer,
with nextlib's FFmpeg renderers bolted on for codecs Android can't decode in hardware
[verified].

### 1.2 It's a fragment, not an activity

There is no `CS3PlayerActivity` in current master (grep = 0 hits [verified]). The player
is a **fragment inside the single-activity nav graph**:

```
AbstractPlayerFragment<T>          (APP/ui/player/AbstractPlayerFragment.kt:34)
  └─ FullScreenPlayer              (APP/ui/player/FullScreenPlayer.kt:74)   — all UI logic
       └─ GeneratorPlayer          (APP/ui/player/GeneratorPlayer.kt:144)   — episodes + links + watch state
ResultTrailerPlayer : FullScreenPlayer (APP/ui/result/ResultTrailerPlayer.kt:29, isFullScreenPlayer=false)
```

- `AbstractPlayerFragment` owns an `IPlayer` (defaults to `CS3IPlayer()`,
  `AbstractPlayerFragment.kt:39`), a `PlayerView` "host" that owns all player state and
  view refs (`:41-56`), and the percentage constants that drive watch-state
  (`:22-31`, see §4). `nextEpisode()/prevEpisode()/playerPositionChanged()/…` are
  `open` no-ops that **throw** `NotImplementedError` if a subclass forgets one
  (`:70-113`) — compile-time-checked-lite.
- `FullScreenPlayer` = "All the UI Logic for the player" (its own comment,
  `FullScreenPlayer.kt:72`): controls show/hide, dialogs (source/track/speed/subtitle),
  orientation/resize/lock, key events, PiP.
- `GeneratorPlayer` = the concrete stream player: holds `PlayerGeneratorViewModel`
  (the `VideoState` from doc 08 §4) + `SyncViewModel`, episode navigation, the
  notification player, watch-state writes.
- Separate `DownloadedPlayerActivity` (`APP/ui/player/DownloadedPlayerActivity.kt:16`)
  exists only as an **external "open with CloudStream" entry point** (ACTION_VIEW /
  content:// / shared URLs) and immediately delegates to
  `OfflinePlaybackHelper.playUri/playLink` (`:80-105`).

**Why the `IPlayer` abstraction?** `IPlayer` (`APP/ui/player/IPlayer.kt`) is the
interface every player feature codes against (`loadPlayer`, `getVideoTracks`,
`setPreferredSubtitles`, `handleEvent`, events via `initCallbacks`). Today there is
exactly one implementation — `CS3IPlayer` — but the split lets the player UI
(`PlayerView`, gestures, dialogs) stay engine-agnostic, and the event bus
(`PlayerEvent` sealed hierarchy, `IPlayer.kt:45-156`) is the only channel between
engine and UI. A second impl (e.g. the old VLC player, removed upstream) would slot in
without touching UI code [inferred from structure]. The related
`VideoClickActionHolder` extensible **external-player** system (VLC/MX/WebVideoCaster
via `VideoClickActionHolder.makeOptionMap`, `APP/ui/result/ResultViewModel2.kt:1382,
1605-1660`) lives outside the in-app player and receives plain URLs/links.

### 1.3 Launch: episode click → fragment

Default click path (doc 08 §7 covered resolution; here the player side):
`EpisodeClickEvent(ACTION_CLICK_DEFAULT)` → if a Chromecast session is connected it
diverts to cast (`ResultViewModel2.kt:1417-1430`), else
`getPlayerAction(ctx)` reads the user's `player_default_key` preference → maps a
player unique-id to an action int, default `ACTION_PLAY_EPISODE_IN_PLAYER`
(`APP/ui/result/EpisodeAdapter.kt:85-91`). `ACTION_PLAY_EPISODE_IN_PLAYER` finds the
episode's index in the shared `RepoLinkGenerator` and navigates:

```kotlin
activity?.navigate(
    R.id.global_to_navigation_player,
    GeneratorPlayer.newInstance(generator, index, list)   // ResultViewModel2.kt:1562-1567
)
```

`GeneratorPlayer.newInstance` registers the generator in a **process-wide
`ConcurrentHashMap<String, VideoGenerator<*>>` keyed by a random UUID** and returns a
Bundle (`uuid`, `index`, `syncData`) (`GeneratorPlayer.kt:150-164`) — the fragment
later pulls the generator back out of that map in `onBindingCreated`
(`:2239-2253`); if the entry is gone (process death without state) the player exits.
On binding: `viewModel.attachGenerator(generator, index)` → `viewModel.loadLinks()`
(unless a `currentSelectedLink` survived recreation, then `loadLink(selectedLink, true)`
`:2291-2297`). When `VideoState.loading` flips to Success/Failure, `startPlayer()`
picks the first usable link and plays (`:2337-2356, 1610-1629`); an "auto-start" also
fires mid-loading once any link's profile priority ≥
`QualityDataHelper.AUTO_SKIP_PRIORITY` (`:2380-2388`).

### 1.4 From `ExtractorLink` to `MediaItem`/`MediaSource` — all the plumbing

Entry: `GeneratorPlayer.loadLink(link: VideoLink, sameEpisode)` (`:499-550`) →
fires `loadExtractorJob` (the `extractorVerifierJob` keep-alive, doc 08 §4) →
`player.loadPlayer(ctx, sameEpisode, url, uri, startPosition, subtitles,
preferredSubtitle, preview=true)` → `CS3IPlayer.loadOnlinePlayer(context, link)`
(`CS3IPlayer.kt:271-324, 1801-1977`).

**MIME from link type** (`:1804-1889`): `M3U8 → APPLICATION_M3U8`,
`DASH → APPLICATION_MPD`, `VIDEO → VIDEO_MP4`. `TORRENT/MAGNET` is special-cased:
blocked unless the user's `prefer_media_type_key` includes Torrent **and** they accept
a one-time-per-session dialog, then `Torrent.transformLink` rewrites the link into a
local HTTP stream (see §1.7).

**MediaItem itself is tiny** — URI + forced MIME only:

```kotlin
private fun getMediaItemBuilder(mimeType: String): MediaItem.Builder {
    return MediaItem.Builder()
        //Replace needed for android 6.0.0  https://github.com/google/ExoPlayer/issues/5983
        .setMimeType(mimeType)                    // CS3IPlayer.kt:856-861
}
```

`ExtractorLinkPlayList` becomes N `MediaItemSlice(mediaItem, durationUs)` slices
(`:1905-1931`); a normal link is one slice with `durationUs = Long.MIN_VALUE`.
**All authentication lives in the `HttpDataSource.Factory`, not the MediaItem**
(`createVideoSource`, `:790-831`):

```kotlin
val userAgent = link.headers.entries.find { it.key.equals("User-Agent", ignoreCase = true) }
    ?.value ?: USER_AGENT                                   // :795-797
val source = if (interceptor == null) {
    if (engine == null) OkHttpDataSource.Factory(app.baseClient).setUserAgent(userAgent)
    else CronetDataSource.Factory(engine, Executors.newSingleThreadExecutor())
        .setUserAgent(userAgent).setConnectionTimeoutMs(CRONET_TIMEOUT_MS)   // 15s :661
        .setReadTimeoutMs(CRONET_TIMEOUT_MS).setResetTimeoutOnRedirects(true)
        .setHandleSetCookieRequests(true)
} else { /* provider interceptor forces the OkHttp path */ ... }             // :799-818
// Do no include empty referer, if the provider wants those they can use the header map.
val refererMap = if (link.referer.isBlank()) emptyMap() else mapOf("referer" to link.referer)
val headers = refererMap + link.headers // Adds the headers from the provider, e.g Authorization
return source.apply { setDefaultRequestProperties(headers) }                // :820-831
```

So: **referer is folded into the header map (unless blank), provider headers layer on
top, and the UA is taken from `link.headers["User-Agent"]` with the app default as
fallback** — cookies are just whatever the provider passed in `link.headers` (there is
no separate cookie jar). The interceptor, when a provider supplies
`getVideoInterceptor(link)` (`:1940-1941`; the only consumer of that API, doc 08 §4),
forces the OkHttp path so the interceptor can rewrite requests
(`app.baseClient.newBuilder().addInterceptor(interceptor).build()` `:812-817`).

**Transport choice**: a single shared `CronetEngine` (Brotli+H2+QUIC, disk HTTP cache
`cacheDir/CronetEngine`) is created lazily and kept alive while
`activePlayers > 0`, released 60 s after the last player detaches (`:660-715, 756-788`);
Cronet failure falls back to OkHttp silently. The video source is then wrapped in a
`CacheDataSource` over a `SimpleCache` at `cacheDir/exoplayer` with an LRU evictor of
`simpleCacheSize` (a settings-driven player cache; `deleteFileOnExit` keeps it fresh)
(`:1256-1274, 840-854`) — this is the *streaming* cache, unrelated to downloads.

**Subtitles and external audio are merged as separate `MediaSource`s**, not track
overrides: `getSubSources` builds one `SingleSampleMediaSource` per `SubtitleData`
(language forced to `"_$name"`, id = `SubtitleData.getId()`) using the offline
factory for `DOWNLOADED_FILE`/`EMBEDDED_IN_VIDEO` origins and
`createOnlineSource(sub.headers, interceptor)` for URL subs — i.e. **subtitle files
keep their own headers** (`:1714-1747, 737-754`). `getAudioSources` builds a
`DefaultMediaSourceFactory` source per `link.audioTracks` entry (`:1754-1768`).
Final assembly in `buildExoPlayer` (`:1364-1376`):

```kotlin
setMediaSource(
    MergingMediaSource(*allSources.toTypedArray()),   // video + subSources + audioSources
    playbackPosition
)
```

Playlists (multi-slice) instead use `ConcatenatingMediaSource2` of
`ClippingMediaSource`s (duration must be known — links ExoPlayer issue #4727), with a
legacy `ConcatenatingMediaSource` fallback that "seems to fail with Torrents only"
(`:1335-1362`).

**Renderer/decoder setup** (`buildExoPlayer :1097-1225`): a custom renderers-factory
lambda reads `software_decoding_key` — `0`=HW+SW, `2`=SW+HW (PREFER), `1`=HW only,
`-1`=auto (phones/emulators ON, **TV always off "because of crashes"**) — and uses
`FixedNextRenderersFactory` + nextlib FFmpeg in `EXTENSION_RENDERER_MODE_ON/PREFER`
when enabled, plain `DefaultRenderersFactory` otherwise. A custom `TextOutput`
merges/dedups cues and applies the user's caption style (§3). Track selection is a
`DefaultTrackSelector` with `.setMaxVideoSize(Int.MAX_VALUE, maxVideoHeight)` — the
pref height comes from `quality_pref_key` vs `quality_pref_mobile_data_key`
depending on current network (`:871-880, 1387-1391`), so m3u8 variant choice follows
the user's quality setting without hard-failing larger streams. Seek tolerance is
±0.3 s (`:1225`), `LoadControl` gets a settings-driven `targetBufferBytes` +
30 s back-buffer (`:1226-1248`). Extractors come from a forked
`UpdatedDefaultExtractorsFactory` (a full copy of media3's
`DefaultExtractorsFactory` that swaps in `UpdatedMatroskaExtractor` — "enabled seeking
in formats where the seek information is at the back of the file" —
`:1251-1254`, `APP/ui/player/UpdatedDefaultExtractorsFactory.kt:52-68`) plus
`FLAG_MERGE_FRAGMENTED_SIDX` for offline fMP4s (`:1254`).

**SSL**: `ignoreSSL` (always true) installs a trust-all `SSLTrustManager` +
hostname-verifier via `HttpsURLConnection.setDefault*` before online loads
(`:161, 1893-1902`, `APP/ui/player/SSLTrustManager.kt`) — a global, blunt
"stream hosts have broken certs" workaround [verified].

### 1.5 DRM (`DrmExtractorLink`)

`DrmExtractorLink` → `DrmMetadata(kid, key, uuid, kty, licenseUrl, keyRequestParameters)`
(`:188-195, 1910-1925`). In `buildExoPlayer` (`:1280-1331`):
- **ClearKey** (`CLEARKEY_DRM_UUID`): a `LocalMediaDrmCallback` with an inline JSON
  body `{"keys":[{"kty":…,"k":…,"kid":…}],"type":"temporary"}` — no server at all.
- **Widevine/PlayReady**: `HttpMediaDrmCallback(drm.licenseUrl, client)` +
  `DefaultDrmSessionManager` (`setMultiSession(true)`,
  `setPlayClearSamplesWithoutKeys(true)`), delivered through a
  `DashMediaSource.Factory(dataSourceFactory)`.
- Any other UUID logs "DRM Metadata class is not supported" and plays nothing.

Note DRM'd sources always build a **Dash** media source regardless of `link.type`
[verified] — providers must hand out MPDs for this to work.

### 1.6 Errors & retry

`onPlayerError` (`:1540-1579`) has a deliberate ladder:
1. `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` **and duration already known** → just
   `prepare()` again — "allow playing the buffer without internet".
2. `ERROR_CODE_BEHIND_LIVE_WINDOW` → `seekToDefaultPosition() + prepare()`.
3. `HlsPlaylistTracker.PlaylistStuckException` → seek back to live head.
4. Anything else → `event(ErrorEvent(error))` → `PlayerView.mainCallback` →
   `GeneratorPlayer.playerError` (`:1564-1587`), which marks the link errored in
   `VideoState` (`addError`), logs url/headers/referer/position, and — if there is no
   next mirror — sets `viewModel.forceClearCache = true` so the next attempt bypasses
   the 20-min link cache. `AbstractPlayerFragment.playerError` default shows the
   standard error overlay; from there the user can retry (`reloadPlayer` re-runs the
   whole load with the same link, `:1979-1988`) or skip to the next mirror
   (`nextMirror()`, `:1706-1714` — next link at same episode, `sameEpisode=true`, so
   **position survives** mirror switches because `loadPlayer(sameEpisode=true)` keeps
   `playbackPosition` and re-seeks after rebuild `:283-295`).
A "skip loading" button also lets the user bail out of slow link resolution early
(`GeneratorPlayer.kt:2299-2304, 2360-2393`).

### 1.7 Torrent playback

`Torrent` (`APP/ui/player/Torrent.kt:19`) embeds a **Go TorrServer** via gomobile
(`go.Seq.load()`, `TorrServer.startTorrentServer(dir, 0)` `:201-213`).
`transformLink` starts the server, POSTs `{action:"add", link}` to
`127.0.0.1:<port>/torrents`, and returns a **new `ExtractorLink` of type VIDEO whose
url is the local stream endpoint** (`:215-235`) — so torrents play through the exact
same HTTP pipeline, with a progress event looper feeding the in-player download bar
(`torrentEventLooper :1039-1061`, surfaced via `DownloadEvent` →
`GeneratorPlayer.showDownloadProgress :465-497`).

### 1.8 Background / notification / PiP

`playerUpdated` attaches a `PlayerNotificationManager` (media3-session) to the
ExoPlayer with title/poster/large-icon (falls back to a mid-video preview frame
bitmap!) and a custom **stop action** broadcasting `STOP_ACTION = "stopcs3"`
(`GeneratorPlayer.kt:285-436, 146-148`). PiP is handled by
`PlayerPipHelper` + `AbstractPlayerFragment.onPictureInPictureModeChanged`
(`AbstractPlayerFragment.kt:127-130`). An `isAudioOnlyBackground` flag keeps audio
playing when backgrounded (`CS3IPlayer.kt:176, 625-648`).

---

## 2. Link & quality selection UX

### 2.1 The source dialog (in-player)

`GeneratorPlayer.showMirrorsDialogue()` (`:1014-1407`) opens a full-screen dialog
(`player_select_source_and_subs`) with three lists — sources, subtitle groups,
subtitle "options" (variants of the same subtitle name) — plus a quality-profile
button and a subtitle-encoding selector. Key behaviors [verified]:

- **Link rows are labeled `"$name ${Qualities.getStringByInt(link.quality)}"`** —
  provider mirror name + human quality string (`:1139-1143`); the same formatting is
  used in the pre-play single-link popup (`ResultViewModel2.kt:1234`) and the
  Chromecast source dialog (`ControllerActivity.kt:167`). Long-press a link copies
  its raw URL (`:1155-1163`).
- Links are **sorted by the current quality profile** (`viewModel.state.sortLinks`)
  and filtered to `shouldUseLink || currentLink` — links excluded by the profile
  (wrong type / below priority floor) are hidden behind an italic
  *"N links hidden"* footer (`:1122-1182`).
- **Subtitles are grouped by `originalName`** with the numeric `nameSuffix` as a
  secondary row — the dedup machinery from doc 08 §5 surfacing as UX
  (`:1198-1255`).
- The dialog pauses playback on open and resumes on dismiss (`:1019-1020, 1055-1060`).
- **Apply** (`:1382-1402`): only reloads if something actually changed. A different
  source calls `loadLink(it.link, sameEpisode=true)` — same episode ⇒ saved position
  is kept (§1.6); a different subtitle calls `setSubtitles` which may require a
  player reload (`REQUIRES_RELOAD` state, `PlayerSubtitleHelper.kt:124-132`).
- Footer actions on the subtitle list: *load from file* (`openSubPicker`, SAF file
  picker → `SubtitleData(DOWNLOADED_FILE)` `:911-943`), *search online* (§3.3), and
  the one-click **"download the best matching sub"** (`addFirstSub(SubtitleSearch…)`
  with imdb/tmdb/mal/anilist ids + episode/season/year `:1082-1113, 945-1010`).
- Encoding picker re-decodes subs via `updateForcedEncoding` + a dirty
  `player.seekTime(-1)` "to update subtitles" (`:1350-1379`).

Quality **profiles** (priority lists per server/label, `QualityDataHelper`, doc 08 §4)
are editable from this dialog via `QualityProfileDialog` (`:1311-1335`).

### 2.2 Pre-play selection & external players

From the episode long-press option menu, `ACTION_DOWNLOAD_MIRROR` /
`ACTION_CHROME_CAST_MIRROR` / external-player actions all funnel through
`acquireSingleLink(...)` → a radio popup listing `"$name $quality"` entries
(`ResultViewModel2.kt:1225-1243, 1488-1514`). The default action for a plain click is
just "play in app" (`EpisodeAdapter.kt:85-91`); `AlwaysAskAction` external players
pop their own chooser (`ResultViewModel2.kt:1608-1636`). Every external action chosen
is persisted under `last_click_action` (`:1638`).

### 2.3 Mid-playback switching summary

- **Mirror/source switch**: full player rebuild, position preserved
  (`sameEpisode=true` path §1.6).
- **Subtitle switch**: either a track-selection override (`setPreferredSubtitles` →
  `setPreferredTextLanguage`/track override, `CS3IPlayer.kt:498-541`) or a full reload
  if the new subtitle wasn't already a `SingleSampleMediaSource`.
- **Video track (m3u8 variant) switch**: `setMaxVideoSize(width, height, id)` with a
  `TrackSelectionOverride` when the id matches (`:353-379`), no reload.
- **Audio track switch**: `setPreferredAudioTrack(language, id, formatIndex)` —
  override per format index, fallback to `setPreferredAudioLanguage` (`:381-413`); the
  preferred language is persisted per-account under
  `$currentAccount/$PREFERRED_AUDIO_LANGUAGE_KEY` (`:717-732`) and re-applied on track
  change (`GeneratorPlayer.kt:224-233`).

The track picker (`showTracksDialogue`, `:1409-1564`) lists video tracks as
`label ?: "${width}x${height}"` sorted by height desc, and audio tracks as
`language/label/channelCount` strings — no reload needed for either.

---

## 3. Subtitle pipeline

### 3.1 Where subtitles come from (origins)

`SubtitleOrigin { URL, DOWNLOADED_FILE, EMBEDDED_IN_VIDEO }`
(`APP/ui/player/PlayerSubtitleHelper.kt:26-30`):

1. **URL** — provider `subtitleCallback(SubtitleFile)` during `loadLinks` → converted
   to `SubtitleData` with mime from URL suffix and headers preserved (doc 08 §5;
   `PlayerSubtitleHelper.getSubtitleData :111-121`, `.toSubtitleMimeType :102-109`
   maps `.vtt/.srt/.xml|.ttml`, default SRT).
2. **DOWNLOADED_FILE** — (a) subs downloaded next to the video by the download
   pipeline (§5.6) and discovered by `DownloadFileGenerator` matching filenames in the
   episode's folder (`DownloadFileGenerator.kt:55-74`); (b) user-picked local files
   (`openSubPicker`); (c) temp files from online sub providers (`SubtitleResource`
   downloads to `File.createTempFile("temp-subtitle", ".tmp")`, auto-deleted on exit,
   `APP/subtitles/AbstractSubProvider.kt:20-30`).
3. **EMBEDDED_IN_VIDEO** — discovered at runtime: `onTracksChanged` maps every
   supported text track to `SubtitleData(origin=EMBEDDED_IN_VIDEO, url=<media3 track
   id>, mime=format.sampleMimeType ?: APPLICATION_SUBRIP)`
   (`CS3IPlayer.kt:1448-1488`) and emits `EmbeddedSubtitlesFetchedEvent` →
   `viewModel.addSubtitles` (`GeneratorPlayer.kt:220-222`).

### 3.2 Decoding & rendering — *not* burned, but heavily customized

Rendering is the **media3 text renderer into the stock `SubtitleView`**
(`AbstractPlayerFragment.subView :51`), i.e. **overlay text, never burned into the
frame** [verified]. But CS3 replaces the decoder wholesale:

- `CustomSubtitleDecoderFactory` + `CustomDecoder` (`CustomSubtitleDecoderFactory.kt:39,
  366-406`) wrap every text track; the decoder **sniffs the format from content, not
  mime**: strip control/format unicode chars, then `"WEBVTT"`→`WebvttParser`,
  `<?xml`→`TtmlParser`, `[Script Info]`/`Title:`→`SsaParser` (ASS/SSA!),
  leading `1`→`CustomSubripParser`, mime fallback last (VTT/SSA/MP4VTT/TTML/SUBRIP/
  TX3G/DVBSUBS/PGS) (`:230-282`). So **SRT, VTT, TTML, ASS/SSA, tx3g, DVB, PGS are
  all supported** — ASS styling survives only insofar as `SsaParser`+cue styling
  carries it [inferred].
- **Charset**: juniversalchardet `UniversalDetector` sniffs the encoding (or the
  user's forced encoding from settings), falling back to UTF-8
  (`:198-228`) — the classic windows-1256 Arabic-subtitle fix.
- `CustomSubripParser` is a **fork of media3's SubRip parser** ("the google devs are
  useless, this entire class is just to override this", `CustomSubripParser.kt:19,
  240`) adding proper alignment tags.
- The renderers-factory installs the custom decoder with **legacy decoding enabled**
  (`experimentalSetLegacyDecodingEnabled(true)`, `CS3IPlayer.kt:1195-1216`) and a
  custom `TextOutput` that: splits bitmap vs text cues, fixes alignment, applies the
  user's `SaveCaptionStyle`, **dedups identical cue lines** ("often happens when the
  subtitle file uses multiple text lines as outlines. Most commonly found in fansubs
  with fancy subtitle styling") and merges same-position cues into one cue to prevent
  overlap (`:1129-1186`). Style is configurable via the subtitle settings fragment
  (`SubtitlesFragment` → `setSubtitleViewStyle`, incl. elevation so subs lift above
  the bottom bar, `FullScreenPlayer.animateLayoutChangesForSubtitles :257-272`).
- **Subtitle offset**: a live ± dialog (`showSubtitleOffsetDialog`) sets
  `CustomDecoder.subtitleOffset`, which shifts every parsed cue's start time; offset
  is stored per-player and reset per episode (`FullScreenPlayer.kt:499+;
  GeneratorPlayer.kt:545-549; CustomSubtitleDecoderFactory.kt:308-313`). Cues are
  also mirrored to `getSubtitleCues()` for a future sync feature (`:284-305`).
- Auto-selection: preferred language persisted under `SUBTITLE_AUTO_SELECT_KEY`;
  `autoSelectSubtitles()` prefers player-reported preferred → settings language →
  downloaded-file match, in that order (`GeneratorPlayer.kt:1797-1867`). A language
  filter pref can drop online subs not in the user's provider-language list
  (`PlayerGeneratorViewModel.isValidSubtitle :369-382`).

### 3.3 Online subtitle providers (OpenSubtitles et al.)

Subtitle search/download is a **syncprovider-style plugin subsystem**
(`SubtitleAPI` implementations registered in `AccountManager.subtitleProviders`):
`OpenSubtitles`, `Addic7ed`, `SubDL`, `SubSource`
(`APP/syncproviders/AccountManager.kt:121-126`) [verified]. OpenSubtitles uses
`https://api.opensubtitles.com/api/v1` with a **hardcoded API key**
(`uyBLgFD17MgrYmA0gSXoKllMJBelOYj2`) + `user-agent: Cloudstream3 v0.2`, JWT login
(username/password, 24 h token), a 30 s cooldown on HTTP 429, and search by
imdbId-or-query + language + season/episode/year
(`APP/syncproviders/providers/OpenSubtitlesApi.kt:39-46, 64-99, 105-125`). Search
results are `SubtitleEntity(idPrefix, name, lang, data, type, source, epNumber,
seasonNumber, year, isHearingImpaired, headers)` (`APP/subtitles/
AbstractSubtitleEntities.kt:6-18`); downloading uses `SubtitleResource`, which can
fetch **zip archives and unzip them into per-entry temp files**
(`addZipUrl`, `AbstractSubProvider.kt:80-91`).

The in-player picker (`openOnlineSubPicker`, `GeneratorPlayer.kt:590-880`) queries
**all providers in parallel** and interleaves results round-robin (`:735-770`),
shows hearing-impaired icons, lets you narrow by language list and **year**
(a 1900→now spinner, `:690-716`), and applies the chosen sub via
`addAndSelectSubtitles` (which reloads the player with the new sub source active).

There is **no on-disk subtitle cache** beyond (a) the 20-min `RepoLinkGenerator`
link+sub cache (doc 08 §4), (b) temp files deleted on exit, and (c) subtitles
permanently downloaded as `.vtt/.srt` next to videos by the download pipeline (§5.6)
[verified].

---

## 4. Watch-state & resume

### 4.1 The write path — event-driven, not a timer

There is **no periodic "save every N seconds" loop**. Position is persisted whenever
`CS3IPlayer.updatedTime()` runs — which happens on: first rendered frame, every seek,
every BUFFERING state change, pause/stop (`onPause/onStop → saveData()`), and player
release (`CS3IPlayer.kt:580-595, 1627-1641`; `saveData :580-589` calls
`updatedTime()` then snapshots position/window/isPlaying). `updatedTime` emits a
`PositionEvent` (`:896-917`) → `PlayerView.mainCallback` → `callbacks.
playerPositionChanged` (`PlayerView.kt:770-773`) →
`GeneratorPlayer.playerPositionChanged` writes state.

Additionally `AbstractPlayerFragment`'s companion declares the percentage constants
(`AbstractPlayerFragment.kt:22-31`):

```kotlin
const val SKIP_OP_VIDEO_PERCENTAGE = 50        // switch skip-op → skip-episode
const val PRELOAD_NEXT_EPISODE_PERCENTAGE = 80 // preload next episode links
const val NEXT_WATCH_EPISODE_PERCENTAGE = 90   // resume on NEXT episode
const val UPDATE_SYNC_PROGRESS_PERCENTAGE = 80 // sync "watched" to trackers
```

`PlayerView.initialize()` passes exactly these as `requestedListeningPercentages`
(`:272-280`), and `CS3IPlayer.onRenderFirst` schedules a one-shot **player message at
`contentDuration * percentage / 100`** per value, each firing another `updatedTime`
(`:1677-1689`) — so position is *also* saved at 50/80/90/80 % of every video
[verified]. `addTimeStamps` uses the same message mechanism for skip stamps
(`:1646-1661`).

### 4.2 What gets written

`playerPositionChanged` (`GeneratorPlayer.kt:1724-1795`) skips livestreams and NSFW,
guards `duration <= 0`, then:

```kotlin
DataStoreHelper.setViewPosAndResume(id, position, duration, currentMeta, nextMeta)
```

`DataStoreHelper` (`APP/utils/DataStoreHelper.kt`), all keys **namespaced per
account** (`$currentAccount/...`):

| Purpose | Key | Shape | Site |
|---|---|---|---|
| Position+duration per episode id | `video_pos_dur` (`VIDEO_POS_DUR :47`) | `PosDur(pos, dur)` | `setViewPos :691-695` (skips `dur < 30_000` — "too short") |
| Watched flag per episode id | `video_watch_state` (`VIDEO_WATCH_STATE :48`) | `VideoWatchState{None,Watched}` (`APP/ui/result/ResultFragment.kt:33-37`); `None` = key removed | `:763-771` |
| Continue-watching pointer | `result_resume_watching_2` (`:53`) | `ResumeWatching(parentId, episodeId, episode, season, updateTime, isFromDownload)` | `setLastWatched/removeLastWatched :523-611` |

`setViewPosAndResume` (`:701-751`) does three things: write `PosDur`; **un-mark
"Watched"** if a previously-watched episode is being re-watched; and at
`percentage >= 90 %` flip the resume pointer to the **next** episode (or drop the
"last watched" entry entirely if this was the final episode). The same function is
reused verbatim by the Chromecast controller (`ControllerActivity.kt:263-272`).

The same handler also drives, at their thresholds:
- **≥80 % + once per episode** (`maxEpisodeSet` guard): `sync.modifyMaxEpisode(...)` —
  pushes progress to MAL/AniList/Kitsu/Simkl, gated by `episode_sync_enabled_key`
  (`:1758-1771`).
- **<50 %** on anime types: show the legacy "skip op" button (`playerSkipOp` — a plain
  **85-second jump**, `FullScreenPlayer.kt:495-497`); at ≥50 % it swaps to
  "skip episode" (`:1773-1788`).
- **≥80 %**: `viewModel.preLoadNextLinks()` — warms the `(apiName, id)` link cache for
  the next episode so the next-episode transition is instant
  (`PlayerGeneratorViewModel.kt:281-312`, doc 08 §4).

### 4.3 Resume behavior on open

`GeneratorPlayer.getPos()` (`:247-254`): resume from saved `PosDur`, **unless >95 %
through — then start at 0** (the last 5 % counts as "finished"). If the user arrived
via "next episode" (`isNextEpisode`) it also starts at 0 (`:534-536`). The details
page uses the same rule via `ResultEpisode.getRealPosition()`
(`APP/ui/result/ResultFragment.kt:67-72`: ≤5 % or ≥95 % ⇒ 0). "Mark as watched" /
"mark up to here" writes `VideoWatchState` directly from the episode options menu
(`ResultViewModel2.kt:1571-1603`).

### 4.4 Next-episode autoplay

Two independent triggers, both gated by `autoplay_next_key` (default **true**):
`Player.STATE_ENDED` in `CS3IPlayer` (`:1600-1613`) and `VideoEndedEvent` in
`PlayerView` (`:774-782`) → `CSPlayerEvent.NextEpisode` →
`GeneratorPlayer.nextEpisode()` → `viewModel.loadLinksNext()` (`episodeIndex + 1`)
with `isNextEpisode = true` (`:1678-1692`). Chromecast has its own queue-based
variant (§6.2).

### 4.5 Skip-intro stamps (AniSkip & friends)

On first `playerPositionChanged` per episode, if `enable_skip_op_from_database`
(default true), `viewModel.loadStamps(duration)` (`:1732-1743`) →
`SkipAPI.videoStamps(page, episode, durationMs, hasNextEpisode)`
(`APP/utils/videoskip/SkipAPI.kt:68-102`): iterates **four providers in order —
`AniSkip`, `TheIntroDBSkip`, `IntroDbSkip`, `AnimeSkip`** — and takes the **first
non-empty** result, caching it in a process `ConcurrentHashMap<episodeId, …>`
(never cleared). AniSkip (GPLv3, credited to saikou-app) queries
`https://api.aniskip.com/v2/skip-times/<malId>/<episode>?types[]=ed&types[]=mixed-ed&types[]=mixed-op&types[]=op&types[]=recap&episodeLength=<s>`
(`AniSkip.kt:26-28`) — i.e. it needs a **MAL id** on the `LoadResponse`; no MAL id ⇒
null ⇒ next provider. Stamps are `SkipStamp(type ∈ {Opening, Ending, Recap,
MixedOpening, MixedEnding, Credits, Intro, Preview}, startMs, endMs, label?)`
wrapped as `VideoSkipStamp(timestamp, skipToNextEpisode = hasNext && ends <20 s
before video end, source)` (`:13-45`).

Delivery to UI: `player.addTimeStamps(stamps)` registers a player message at each
`startMs` → `updatedTime` → `getCurrentTimestamp` matches position-in-stamp →
`TimestampInvokedEvent` → `GeneratorPlayer.onTimestamp` shows an animated
skip button (auto-hides after 6 s, `:2066-2132`); clicking fires
`CSPlayerEvent.SkipCurrentChapter` → seek to `endMs`. The button text is either the
type label or **"Next episode"** when `skipToNextEpisode` [verified].

---

## 5. Downloads — the full pipeline

### 5.1 Entry points & queue

Two user actions enqueue downloads (`ResultViewModel2.handleEpisodeClickEvent`):
- `ACTION_DOWNLOAD_EPISODE` — "auto download": enqueue a
  `DownloadQueueItem(episode + response metadata)` with **no links**; links are
  resolved later inside the worker (`:1472-1486`).
- `ACTION_DOWNLOAD_MIRROR` — resolve links first (popup of
  `LOADTYPE_INAPP_DOWNLOAD` links = `{VIDEO, M3U8}` only, `IGenerator.kt:14-17`),
  then enqueue with the chosen `ExtractorLink` + the resolved subs
  (`:1488-1514`).

`DownloadQueueManager.addToQueue(wrapper)` (`APP/utils/downloader/
DownloadQueueManager.kt:225-240`): refuses items already ≥98 % complete on disk,
dedups by episode id, marks `IsPending`, and starts `DownloadQueueService` if not
running. The **queue itself is a persisted `Array<DownloadQueueWrapper>` in a
DataStore key `download_queue_key`** (`:35-48`); a `StateFlow` mirrors it and every
emission is written back (`init :53-58`). Wrappers are either
`DownloadQueueItem` (fresh) or `DownloadResumePackage(item, linkIndex)` (resumable)
(`APP/utils/downloader/DownloadObjects.kt:27-137`). On app start, `init(context)`
rehydrates the queue from both `QUEUE_KEY` and `KEY_RESUME_IN_QUEUE`
(`download_resume_queue_key`, written when a download *starts* so a killed service
re-queues), marks everything pending, and starts the service
(`DownloadQueueManager.kt:60-94`; `KEY_*` at `DownloadManager.kt:197-203`).

### 5.2 The services

- **`DownloadQueueService`** (`APP/services/DownloadQueueService.kt:48-279`) is the
  brain: a **foreground service (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`)** showing one
  persistent "N active / M queued" notification. It waits up to **15 s for plugins to
  load** (extractorVerifier jobs need plugin classes; safe mode aborts), then runs a
  flow-collector over `(downloadInstances, queue, currentDownloads)`: removes
  completed/failed instances, and while `instances.size < maxConcurrentDownloads`
  (pref `download_parallel_key`, default **3**, `DownloadManager.kt:114-116`) pops
  the queue **one at a time** ("Cant start multiple downloads at once") and calls
  `instance.startDownload()` (`:189-247`). It self-stops when queue+instances are
  empty (500 ms debounce to ride out transient states) and is `START_STICKY`
  (`:268-270`). A `downloadEvent` listener turns notification **Stop** actions into
  queue removals (`:132-142`).
- **`VideoDownloadService`** (`APP/services/VideoDownloadService.kt:12-45`) is a
  dumb relay: notification buttons pause/resume/stop → broadcast `Pair<id,
  DownloadActionType>` into `VideoDownloadManager.downloadEvent`.

Per-download notifications (one per episode, tag `FROM_DOWNLOADER`) are built by
`createDownloadNotification` (`DownloadManager.kt:239-478`): poster large-icon,
percentage+MB+MB/s+ETA (segment counts for HLS), and Pause/Resume/Stop actions whose
`PendingIntent`s start `VideoDownloadService` (`:403-453`).

### 5.3 `EpisodeDownloadInstance` — one episode's state machine

`EpisodeDownloadInstance(context, wrapper)` (`DownloadManager.kt:1671-2095`)
`startDownload()`:
1. Persist the wrapper under `KEY_RESUME_IN_QUEUE` (crash safety).
2. Branch: resume package exists → `downloadFromResume`; links empty →
   `downloadEpisodeWithoutLinks`; else → `downloadEpisodeWithLinks`.
3. **`downloadEpisodeWithoutLinks`** (`:1999-2093`): builds a fresh
   `RepoLinkGenerator(listOf(episode))`, runs `generateLinks(sourceTypes =
   LOADTYPE_INAPP_DOWNLOAD)` while showing a "Loading…" notification, then sorts links
   by **the first quality profile that contains `QualityProfileType.Download`**
   (`-getLinkPriority`) and calls `downloadEpisodeWithLinks`. (This is the auto-mode:
   mirrors are tried in profile-priority order.)
4. **`downloadEpisodeWithLinks`** (`:1893-1997`): writes the visual caches
   `DOWNLOAD_HEADER_CACHE/<resultId>` (show card) and
   `DOWNLOAD_EPISODE_CACHE/<resultId>/<epId>` (episode card — "3 deep folder for
   faster access"), then spawns **two parallel jobs**: the video download
   (`startDownload(info)`) and the **subtitle download job** (§5.6).

`downloadFromResume` (`:1768-1834`) iterates `item.links` from the saved
`linkIndex`: on `retrySame` status it retries the same link once with resume; on
failure it moves to the next mirror; success ⇒ `isCompleted`; exhausted ⇒ `isFailed`.
`isCompleted`/`isFailed`/`isCancelled` setters drive cleanup (remove queue keys,
delete partial subs, emit status events, `DownloadQueueManager.forceRefreshQueue()`).
Global events: `downloadStatus`, `downloadStatusEvent`, `downloadProgressEvent`,
`downloadDeleteEvent`, `downloadEvent` (`:206-210`) — the downloads UI and the
details-page episode download buttons observe these [verified].

### 5.4 `downloadSingleEpisode` — dispatch by type

(`:1474-1560`) Rejects `MAGNET`, `TORRENT`, `DASH` outright
(`DOWNLOAD_INVALID_INPUT` — no torrent/DASH downloading, playback-only §1.7). Starts
the `extractorVerifierJob(link.extractorData)` keep-alive for the duration of the
download (doc 08 §4). Dispatch:

- **`ExtractorLinkType.M3U8` → `downloadHLS`** (§5.5).
- **`ExtractorLinkType.VIDEO` → `downloadThing`** — plain HTTP file download
  (§5.5b).

### 5.5 The fetchers

**b) `downloadThing` — progressive HTTP with parallel range requests**
(`:995-1260`). Headers: `link.headers + USER_AGENT` (a Chrome desktop UA constant,
`:128-129`) via `appendAndDontOverride` (provider headers win case-insensitively,
`DownloadUtils.kt:136-144`). `streamLazy` (`:883-991`) does a HEAD for
`Content-Length`, then probes range support: `Accept-Ranges: none/bytes` or an actual
`Range: bytes=0-…` GET returning **206** (also recovering length from
`Content-Range`). Plan: no-range or small (<20 MiB) ⇒ single `[startByte..EOF]`
connection; else split into **10 MiB chunks** (`LongArray(ceil(len/chunk)) { start +
i*chunk }`). Then `parallelConnections` (pref `download_concurrent_key`, default 3)
workers each: grab the next chunk index under a mutex, stream it into memory,
and write through a **single ordered write queue** — the documented
`@downloadexplanation` scheme: responses landing out of order park in
`pendingData: HashMap<startByte, bytes>` (cloned buffers!) until
`metadata.bytesWritten` catches up (`:1098-1205`). Backpressure: workers sleep 500 ms
while `bytesDownloaded - bytesWritten > 50 MB` (RAM cap, `:1175-1185`). Pause =
`while (IsPaused) delay(100)` under the file mutex; Stop/external file deletion
(a 5 s `fileChecker` poll notices the file vanished, `:1085-1096`) ⇒ delete file +
`DOWNLOAD_STOPPED`. Failure ⇒ keep the partial file (only user Stop deletes it,
`:1248-1254`). Resume = reopen the existing file and `startByte = fileLength`, only
if range is supported — otherwise the file is restarted (`:1056-1064`). Min video
size 10 MiB (`:1549`) rejects dead links.

**a) `downloadHLS` — segment concatenation, no remux**
(`:1262-1468` + `LIB/utils/M3u8Helper.kt`). The output file is **always `.mp4`**
(`:1282`) but the bytes are **raw MPEG-TS segments concatenated in playlist order**
— there is **no ffmpeg remux**: playback works because ExoPlayer sniffs TS
(`TsExtractor` is in `UpdatedDefaultExtractorsFactory`) [verified + inferred].
Pipeline:
1. Wrap the link (headers + UA + referer) as `M3u8Helper.M3u8Stream` and call
   `M3u8Helper2.hslLazy(m3u8, selectBest = true, requireAudio = true)`
   (`DownloadManager.kt:1313-1321`).
2. `hslLazy` (`LIB/utils/M3u8Helper.kt:250-361`) recursively (max depth 3) parses
   master playlists with `HlsPlaylistParser`: picks the variant that is **playable
   standalone** (has audio — "m3u8 files can include separate tracks for dubs/subs",
   so separate-audio variants are skipped to avoid muxing), `selectBest` = max
   `width*height*1000 + averageBitrate`, skipping trick-play; at media-playlist level
   it regex-extracts all `#EXTINF` TS URLs (resolving relative paths) and — "because
   crunchy uses it" — **AES-128 keys**: `ENCRYPTION_URL_IV_REGEX` captures the key
   URL + IV, the key is fetched, and every segment is decrypted in-memory with
   AES-CBC (kotlin-cryptography, IV = declared IV or the segment index) before being
   returned (`:110-111, 227-246, 314-337`).
3. `resolveLinkSafe(index)` fetches one segment with **3 tries / 3 s backoff**, and
   rejects suspicious bodies: empty, or `<128 bytes and all-ASCII` ("error 404"
   HTML pages) (`:207-246`).
4. The download loop is the same parallel/ordered-write scheme as `downloadThing`
   but keyed by **segment index** (`hlsWrittenProgress`), 3 connections, 50 MB RAM
   cap, pause/stop/file-checker semantics identical (`DownloadManager.kt:1326-1468`).
   **Resume for HLS is segment-granular**: `DownloadedFileInfo.extraInfo` stores the
   last written segment index, and a restart re-opens the file and continues from
   `startAt` (`:1284-1301, 1516-1524`). Progress notifications count
   `hlsProgress/hlsTotal` segments (`:1396-1398`).

`DownloadStatus` values encode mirror policy: `DOWNLOAD_INVALID_INPUT` (skip to next
mirror, don't retry same), `DOWNLOAD_FAILED` (retry same + next),
`DOWNLOAD_STOPPED/SUCCESS` (stop trying), `DOWNLOAD_PARTIAL_SUCCESS` (≥50 MB but
incomplete: retry same only) (`:171-195`).

### 5.6 Subtitles during download

`downloadEpisodeWithLinks` filters the resolved `SubtitleData` list to the user's
**download-sub languages** (`SubtitlesFragment.getDownloadSubsLanguageTagIETF()`),
takes at most **3** ("max subtitles download hardcoded (?_?)" — upstream's own
comment), and downloads each via `downloadSubtitle` as **`.vtt` (or `.srt` if the URL
says so) next to the video file with name `"$videoFileName ${subName}.vtt"`**
(`DownloadManager.kt:1953-1989`; `DownloadUtils.downloadSubtitle :96-113`).
`DownloadFileGenerator` later finds them again by filename matching
(`isMatchingSubtitle`) and parses the trailing ` N` into `nameSuffix`
(`DownloadFileGenerator.kt:53-74`). Cancel deletes matching subs (`:1713-1719`).

### 5.7 Storage layout & metadata

- **Base dir**: `download_path_key` setting → `SafeFile` (content:// SAF or file
  path); default = public **Downloads** via `MediaFileContentType.Downloads`
  (`DownloadFileManagement.kt:48-65, 125-130`). Everything goes through
  `com.lagradost.safefile.SafeFile` (the UniFile-style abstraction) so scoped storage
  works [verified].
- **Folder scheme**: `<TvType-prefix>/<Sanitized Show Name>/` for episode-based
  types (e.g. `TV Series/…`, `Anime/…` via `getFolderPrefix()`), type folder only
  for movies (`getFolder :111-116`). **File name**:
  `"Season S E E - EpisodeName.mp4"` built from localized strings + sanitized
  (`getFileName :74-100`; `sanitizeFilename` replaces `|\?*<":>+[]/'` `:16-23`).
- **Metadata** (DataStore, not MediaStore): per-episode
  `DownloadedFileInfo(totalBytes, relativePath, displayName, extraInfo=<hls segment
  index>, basePath, linkHash)` under `download_info/<epId>` — written continuously by
  `DownloadMetaData` during the download (`:531-776`); show/episode cards under
  `DOWNLOAD_HEADER_CACHE` / `DOWNLOAD_EPISODE_CACHE` (§5.3). Deletion
  (`deleteFileAndUpdateSettings :1618-1640`) removes the file + matching subs + the
  key and fires all the delete events.

### 5.8 Playing a download back

From the Downloads screen (`DOWNLOAD_ACTION_PLAY_FILE`,
`APP/ui/download/DownloadButtonSetup.kt:118-170`): load show header + all episode
caches + each episode's `DownloadedFileInfo`, and build a list of
`ExtractorUri(uri = Uri.EMPTY /* placeholder, resolved lazily */, id, parentId, name,
season, episode, headerName, tvType, basePath, displayName, relativePath)` — then
navigate to the **same `GeneratorPlayer`** with a
`DownloadFileGenerator(items)`. That generator's `generateLinks` resolves the real
`uri` lazily from `getDownloadFileInfo` (an SAF directory walk is expensive, hence
the placeholder comment) and emits sibling-file subtitles
(`DownloadFileGenerator.kt:24-77`). `VideoLink.second` (`ExtractorUri`) then flows
into `CS3IPlayer.loadPlayer(data=…)` → `loadOfflinePlayer`:
`MediaItem(VIDEO_MP4, uri)` + `DefaultDataSource.Factory` (+ no cache) + the same
subSources merging (`:1692-1712`). Watch-state works identically offline
(`ExtractorUri` branch in `setViewPosAndResume` sets `isFromDownload=true`,
`DataStoreHelper.kt:729-749`), and the player shows an "offline pin" badge
(`GeneratorPlayer.kt:1941`). The TV download fragment routes through the same
`DownloadButtonSetup` action handling (`APP/ui/download/DownloadButtonSetup.kt` is
shared UI logic; TV long-press menus expose the same `DOWNLOAD_ACTION_PLAY_FILE`)
[inferred — only the phone path was traced end-to-end].

---

## 6. Preview thumbnails & Chromecast

### 6.1 Seekbar preview thumbnails

`PreviewGenerator` (`APP/ui/player/PreviewGenerator.kt`) renders seekbar hover
previews with **`MediaMetadataRetriever`** (no re-encoding, no server):
- Disabled on TV ("low ram") and by the `preview_seekbar_key` pref
  (`IPreviewGenerator.new :67-79`); thumbnails are w/4×h·9/64 of screen width
  (`ImageParams.new16by9 :40-48`).
- Two sub-generators by link type: **`Mp4PreviewGenerator`** sets
  `retriever.setDataSource(url, headers)` — with the link's headers! — and grabs
  frames at binary-fraction positions ("LOD" levels: 1, 2, 4, … up to
  `ceil(log2(duration/10s))` capped 3..6, i.e. up to 2⁶−1 = 63 frames)
  (`:409-546`); **`M3u8PreviewGenerator`** parses the playlist via
  `M3u8Helper2.hslLazy(selectBest = false)`, keeps a prefix-sum of segment times,
  and points the retriever at individual **TS segment URLs** (encrypted streams are
  unsupported) (`:246-407`).
- A caching shell keeps the previous generator alive when switching mirrors of the
  *same* episode (same duration ±10 s) so previews don't blank out mid-switch
  (`:122-231`).
- UI: `previewseekbar` library — `PreviewTimeBar.attachPreviewView(frameLayout)` +
  `setPreviewLoader { pos, max -> getPreview(pos/max) }`; scrubbing pauses playback
  and hides subtitles, resume on release (`PlayerView.kt:284-330`). The frame is
  also reused as the **notification large icon when no poster is available**
  (`GeneratorPlayer.kt:364-376`) — a neat "thumbnail from the actual stream" trick.
- Episode-list thumbnails are just poster URLs from providers (doc 07); there is no
  episode-thumbnail *generation* [verified].

### 6.2 Chromecast

Cast is **first-class**: if a cast session is connected, a plain episode click
diverts to `ACTION_CHROME_CAST_EPISODE` (`ResultViewModel2.kt:1417-1430`), and the
episode options menu grows cast entries (`:1362-1369`).

- Link resolution reuses `RepoLinkGenerator` with `sourceTypes = LOADTYPE_CHROMECAST`
  (VIDEO/DASH/M3U8 only — no torrents, `IGenerator.kt:19-23`) and `isCasting=true`
  (which providers use to skip links that require app-side headers/interceptors —
  doc 08 §4).
- `CastHelper.startCast` (`APP/utils/CastHelper.kt:89-150`) builds
  `MediaInfo.Builder(link.url)` with content type from `link.type`, movie metadata
  (title/subtitle "EpisodeName - MirrorName Quality"/poster), and **one text
  `MediaTrack` per `SubtitleData`** (contentId = sub URL — the receiver fetches subs
  itself; note **link headers/referer cannot be sent to a Chromecast**, a hard
  platform limit ⇒ header-protected streams fail, which is why `hasChromecastSupport`
  exists on MainAPI for providers to opt out [docs+inferred]). The **entire episode
  list + current links + subs are serialized into `MediaInfo.customData` as a
  `MetadataHolder` JSON** (`APP/ui/ControllerActivity.kt:82-92`).
- `awaitLinks(load(...)) { loadMirror(index + 1) }` auto-falls-through to the next
  mirror when the receiver reports `CastStatusCodes.FAILED`
  (`CastHelper.kt:72-87, 125-144`).
- The **`ControllerActivity`** (a `ExpandedControllerActivity`, `:360-391`) adds four
  cast-remote buttons: source picker (`SelectSourceController` — a radio list of
  `"$name $quality"`, switching preserves `approximateStreamPosition`, `:94-238`),
  ±30 s (`SkipTimeController`), and next-episode (`SkipNextEpisodeController`).
- `SelectSourceController.onMediaStatusUpdated` is the remote-side brain: it saves
  watch position (`setViewPosAndResume`), and when the queue nears its end it
  **resolves the NEXT episode's links in-app (`LOADTYPE_CHROMECAST`, non-casting
  repo cache) and `queueAppendItem`s it** — "we never want to autoload the next
  episode [twice]" — keeping an infinite episode queue on the receiver
  (`:249-338`). Cast subtitle styling applies the saved caption style via
  `TextTrackStyle` (`:134-147`).

---

## 7. Sequence — episode click → link dialog → player → watch-state

```
 User                 ResultViewModel2/RepoLinkGenerator        GeneratorPlayer/CS3IPlayer            DataStore
  │ click ep                                                            │                                    │
  ├──────────► handleEpisodeClickEvent(ACTION_CLICK_DEFAULT)            │                                    │
  │              │ chromecast? ── yes ──► startChromecast ──────────────┼──────────────────────────────────► │ (§6.2)
  │              ▼ no                                                   │                                    │
  │              getPlayerAction() → ACTION_PLAY_EPISODE_IN_PLAYER      │                                    │
  │              navigate(global_to_navigation_player,                  │                                    │
  │                       newInstance(generator, index))                │                                    │
  │                                            ┌────────────────────────┤                                    │
  │                                            │ onBindingCreated       │                                    │
  │                                            │  attachGenerator(index)│                                    │
  │                                            │  viewModel.loadLinks() │ (repo cache / provider, doc 08)   │
  │                                            │  state.loading=Success │                                    │
  │                                            │  startPlayer() ── sortLinks(profile) → first shouldUseLink      │
  │                                            │  loadLink(link,false)  │                                    │
  │                                            │   ├─ loadExtractorJob │ (extractorVerifierJob keep-alive)     │
  │                                            │   └─ loadPlayer ─────►│ loadOnlinePlayer(link)              │
  │                                            │                        │  mime = f(link.type)               │
  │                                            │                        │  MediaItem(uri, mime)   ← no headers!
  │                                            │                        │  createVideoSource:                │
  │                                            │                        │   UA+referer+headers →             │
  │                                            │                        │   Cronet/OkHttp factory            │
  │                                            │                        │  (+CacheDataSource)                │
  │                                            │                        │  subSources/audioSources →         │
  │                                            │                        │  MergingMediaSource → prepare()    │
  │  [optional: "sources" button → showMirrorsDialogue → pick "$name $quality" → loadLink(.,sameEpisode=true, pos kept)]     │
  │                                            │                        │ onRenderedFirstFrame               │
  │                                            │                        │  → messages @50/80/90/80% + stamps │
  │                                            │                        │ PositionEvent ───────────────────► │
  │                                            │  playerPositionChanged│  setViewPos(pos,dur) → video_pos_dur
  │                                            │   ≥80% sync.modifyMaxEpisode  ─────────────────────────────► │ tracker
  │                                            │   ≥80% preLoadNextLinks (warm repo cache)                    │
  │                                            │   ≥90% resume pointer → next ep ───────────────────────────► │ resume_watching
  │                                            │  STATE_ENDED + autoplay → nextEpisode() → loadLinksNext()   │
```

---

## 8. ANI-KUTA mapping preview

Our app's relevant pieces: `AK/feature/watch/impl/…/WatchScreen.kt` (2,195 lines),
`AK/core/player/` (player module), `AK/app/…/download/` (DownloadOrchestrator,
ReResolver, ResolveContext, ReResolverAdapter, EnqueueResult) and
`AK/core/download/` (DefaultDownloadManager, DownloadQueue, DownloadService,
HttpDownloader, HlsDownloader, Parallel/SingleConnectionFetcher,
DownloadStorageProvider, AutoDownloadEngine…). **One correction up front: the task
brief called our player "Media3/ExoPlayer-based" — it is not.** WatchScreen drives
**libmpv** (`com.github.aniyomiorg:aniyomi-mpv-lib` 1.18.n,
`AK/gradle/libs.versions.toml:74,151`; `MPVLib` imports throughout
`WatchScreen.kt:95-204`) [verified].

**Player / playback**
- CS3: engine-agnostic `IPlayer` + single Media3 impl; MediaItem carries only
  uri+mime, **all headers live in the DataSource factory** (referer folded into
  headers, UA from link headers, Cronet-vs-OkHttp per link, interceptor hook).
  Ours: MPV takes a raw URL; auth is `MPVLib.setOptionString("http-header-fields",
  headers)` **before** `loadfile` (WatchScreen.kt:663-706) — the same
  "headers-not-in-URI" principle, but a single global option with no per-request
  interceptor concept, no engine fallback, no streaming disk cache (we instead wrap
  URLs in our `CacheProxyServer` `cachedUrl(...)` `:726-731` — functionally our
  analogue of CS3's `CacheDataSource`). [gap] no Cronet/H2/QUIC path; [gap] no
  engine abstraction if we ever want ExoPlayer features (track overrides, live
  windows, DRM).
- CS3: separate audio tracks from `link.audioTracks` merged as extra MediaSources.
  Ours: MPV `sub-add`/`audio-add` external tracks from `ResolvedVideo.subtitleTracks`
  (`pendingSubtitleTracks`, WatchScreen.kt:620-634). Roughly equivalent; [gap] our
  `SourceVideo` has no per-track headers (doc 08 §8).
- CS3: mid-playback mirror switch keeps position (`sameEpisode=true`), errors
  auto-fall-through mirrors (`nextMirror`, `forceClearCache`). Ours: ReResolver
  re-resolves by pinned (server, audio, quality) — but only on download IO errors
  (`ResolveContext.kt:11-13`); [gap] playback-side error→mirror fallback and
  position-preserving source switch are weaker (player errors surface via
  `PlayerErrorOverlay` rather than auto-switching).
- CS3: quality = `DefaultTrackSelector.setMaxVideoSize` + per-network prefs; ours =
  MPV profile/pins per server row. [gap] no mobile-data-vs-wifi quality split.
- CS3: torrent→local-HTTP bridge (TorrServer) and a consent dialog; [gap] we have
  nothing equivalent (nor is it needed for our sources today).

**Subtitles**
- CS3: content-sniffed parser (SRT/VTT/TTML/ASS/SSA/tx3g/PGS/DVB), charset
  autodetection + forced encoding, cue dedup/merge, offset dialog, styled
  `SubtitleView`. Ours: `core/player/subtitles/SubtitleEngine` + embedded
  `subfont.ttf` with MPV's native ASS rendering (arguably *better* for fansub ASS)
  and `SubtitleTrackFormatter`. [gap] our resolver-side subtitle model lacks
  headers/mime (doc 08 §8); [gap] no online subtitle provider network
  (OpenSubtitles/Addic7ed/SubDL/SubSource) and no "auto-download best sub" — a
  high-value, self-contained feature to port.
- CS3: subtitles downloaded alongside videos (max 3, language-filtered, `.vtt`
  next to file, filename-suffix discovery). Ours: HlsDownloader sidecar/
  DownloadScanner handle subs? — `core/download` has no subtitle-download step
  visible in file names [inferred gap].

**Watch-state**
- CS3: event-driven saves + one-shot player messages at 50/80/90/80 %, 90 % flips
  resume to next episode, 5 %-margin resume-to-zero, auto-unwatch on rewatch,
  per-account DataStore keys, tracker sync at 80 %, AniSkip/IntroDB/AnimeSkip
  stamps with skip button. Ours: `WatchProgressStore` (SqlDelight) written during
  playback + on dispose (WatchScreen.kt:384-396, 739-755) — a positional store, but
  [gap] no percentage-message machinery (no preload-next-episode at 80 %, no
  resume-flip at 90 % — we compute resume eligibility at read time instead),
  [gap] no skip-intro stamp providers at all (we're an anime app — AniSkip by MAL id
  is directly applicable and cheap), [gap] no auto-mark-watched→tracker push from
  the player.
- CS3 next-episode autoplay + `preLoadNextLinks` cache warming; ours has episode
  switching overlays but [gap] no link pre-warm for the next episode.

**Downloads** (both apps have strikingly parallel architectures — ours was
clearly built with CS3 as a reference)
- Queue: CS3 persistent `Array<DownloadQueueWrapper>` + foreground
  `DownloadQueueService` (plugin-wait, max-concurrent pop loop, START_STICKY);
  ours `DownloadQueue` (768 lines) + `DownloadService` — same shape
  [verified-ish: names/sizes]. CS3 resumes from TWO key sets (queue + per-start
  `KEY_RESUME_IN_QUEUE`); ours has `TempDownloadCache`/sidecars.
- HLS: CS3 `hslLazy` (variant select w/ standalone-audio filter, AES-128-CBC
  decrypt, ASCII-error detection) → parallel segment fetch → **raw TS concat into a
  `.mp4`-named file**, segment-index resume in `extraInfo`. Ours:
  `HlsDownloader` — same concatenation approach but honestly named `.ts`, master→
  "first variant" (CS3 picks best-resolution standalone-audio variant — smarter for
  dubbed anime), AES-128 with per-sequence IV, **PNG-header stripping** (a CDN hack
  CS3 lacks), sidecar resume + refined totals. Ours is a generation ahead on
  robustness; CS3's audio-variant filter is worth copying for multi-dub sources.
- Progressive: CS3 10 MiB range-chunk parallel + ordered byte queue + 50 MB RAM
  cap + range-probe (HEAD + 206 test); ours `ParallelHttpFetcher` +
  `SingleConnectionFetcher` (same concepts, `connection-budget-capped`).
- Mirrors: CS3 `DownloadStatus{retrySame, tryNext, success}` state machine tries
  mirrors in quality-profile order automatically; ours pins (server, audio, quality)
  via `ResolveContext` + `ReResolver` on IO errors — [gap] no proactive
  mirror-fallthrough on HTTP 403/404 mid-download for us; CS3 has no
  proxy-churn-aware re-resolve (our differentiator).
- Subs: CS3 downloads ≤3 language-matched subs with the video; ours — see gap above.
- Playback of downloads: CS3 routes through the SAME player/generator abstraction
  (`ExtractorUri` placeholder → lazy path resolution) — elegant. Ours converts
  `content://` → **`fd://` ParcelFileDescriptor** for MPV with a 500 ms
  surface-race delay (WatchScreen.kt:663-724); same "one player for online+offline"
  goal. [gap] ours lacks CS3's sibling-subtitle auto-discovery by filename.
- Storage: CS3 `SafeFile`/SAF + `TvType/<Show>/Season E - Name.mp4` + DataStore
  metadata; ours `DownloadStorageProvider`/`DownloadScanner` (doc 13 will cover).
- Preview thumbnails: CS3 retriever-based LOD seekbar previews incl. per-TS-segment
  for m3u8; ours — none found in player module [gap; low priority].

**Chromecast**: CS3 ships a full cast pipeline (queue management, customData
metadata, remote source switching). Ours: none — MPV makes this hard; if we ever
want casting it argues for a Media3 player variant behind an `IPlayer`-style
abstraction. [gap, strategic]

---

## 9. Unverified / open items

- `OfflinePlaybackHelper` internals (external-URL → link resolution) only skimmed
  (`playIntent/playUri/playLink` dispatch seen at `DownloadedPlayerActivity.kt:80-105`).
- `PlayerPipHelper` (205 lines) not read; PiP behavior asserted from
  `AbstractPlayerFragment.onPictureInPictureModeChanged` only.
- `LiveManager`/`LivePreviewTimeBar` (live streams) only glanced at
  (`LiveHelper.registerPlayer`, `CS3IPlayer.kt:1446`).
- `VideoClickActionHolder` external-player plugin system — dispatch seen
  (`ResultViewModel2.kt:1605-1660`) but the holder itself not read; exact supported
  external players (VLC/MX/WebVideoCaster) not enumerated.
- `quality_pref_mobile_data_key`/`quality_pref_key` default value assumed
  `Int.MAX_VALUE` from code default, not from preferences XML.
- Whether `MergingMediaSource` ever fails when a URL subtitle 404s (SingleSample
  source error path) — error is surfaced via `onPlayerError`, but per-sub fallback
  untested [inferred].
- ANI-KUTA `DownloadQueue`/`DownloadService` internals not read this batch (file
  names + HlsDownloader header comments only); mapping bullets for those are
  marked accordingly. Doc 13 (B3-c) should cover them properly.
- Chromecast claim that receivers can't receive custom request headers is platform
  knowledge [inferred], not from this repo's source.

---
## ✔ B5-a Verification Note (2026-08-29)
Checked: 32 claims sampled → 32 verified, 0 corrected, 0 flagged-stale.
Corrections: none.
Confirmed (incl. all high-value targets): **Cronet+OkHttp DataSource** (`CS3IPlayer.createVideoSource` at :790-831 — OkHttpDataSource.Factory when engine==null or provider interceptor present, else CronetDataSource.Factory with `CRONET_TIMEOUT_MS=15_000` (:661), setResetTimeoutOnRedirects + setHandleSetCookieRequests; UA from `link.headers["User-Agent"]` with app default fallback; blank referer excluded, else folded into `setDefaultRequestProperties`); **watch-position = event-driven, not a timer** (no periodic save loop; `updatedTime` on first frame/seek/buffering/pause/release; `AbstractPlayerFragment.kt:22-31` constants SKIP_OP=50 / PRELOAD_NEXT=80 / NEXT_WATCH=90 / SYNC_PROGRESS=80; `onRenderFirst` schedules one-shot player messages at `contentDuration * percentage / 100` re-firing `updatedTime` — IP:1677-1689 exact; `addTimeStamps` uses the same message mechanism at :1646-1661); **TS-concat-as-.mp4 downloads** (`downloadHLS` sets `val extension = "mp4"` in the HLS path, raw TS segments concatenated in playlist order, no ffmpeg remux, segment-granular resume via `extraInfo` startIndex); **85 s hardcoded skip-op** (`player.seekTime(85000) // skip 85s` at FullScreenPlayer.kt:496, shown <50% on anime types, swaps to skip-episode ≥50%). Also verified: `setViewPos` skips `dur < 30_000` ("too short"); resume → 0 when >95 % through (GeneratorPlayer.kt:250); `autoplay_next_key` defaultValue true (settings_player.xml:93-94); `maxConcurrentDownloads` default 3 (:114-116); 10 MiB chunk comment (:888); KEY_RESUME_IN_QUEUE="download_resume_queue_key" (:197-203); OpenSubtitles hardcoded key `uyBLgFD17MgrYmA0gSXoKllMJBelOYj2` + host + 30 s 429-cooldown + "Cloudstream3 v0.2" UA (OpenSubtitlesApi.kt:39-46); LOADTYPE_CHROMECAST drops torrents; fragment-based player (no CS3PlayerActivity); skip-stamp provider order AniSkip→TheIntroDBSkip→IntroDbSkip→AnimeSkip with first-non-empty; the ANI-KUTA-side correction that our player is libmpv (not Media3) is accurate per our `aniyomi-mpv-lib` dependency.
