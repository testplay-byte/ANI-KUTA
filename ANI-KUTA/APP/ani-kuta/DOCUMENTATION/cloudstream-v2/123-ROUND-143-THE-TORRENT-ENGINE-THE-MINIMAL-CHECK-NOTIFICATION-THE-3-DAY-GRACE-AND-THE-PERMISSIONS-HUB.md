# Record 123 — Round 143: the torrent engine, the minimal check notification, the 3-day grace, and the permissions hub

> The user's round-142 close-out order (the research+plan phase) and the round-143 implementation
> order: "go with option 1 (the ecosystem engine)... let's not work on the Aniyomi torrent
> functionality yet, but let's first of all go on and focus on the torrenting functionality of
> Cloud Stream... afterwards give me a proper checklist of the things so that I can check them
> out, and also tell me the things which are remaining."

## 1. The research + plan phase (plan-only; the master plan lives in the workspace planning/)

Five read-only scouts + direct greps mapped the six workstreams to file:line; the master plan
was delivered and every open question answered by the user:

- **The support gate identified**: the user's "one free opening" description matches the
  **sponsor-ad interstitial** exactly (no donation prompt exists) — `consumeFirstOpenGrace()`
  (D-407, round 31). The user confirmed the mapping + specced the change.
- **The torrent ecosystem research** (the user's order: check Aniyomi/Anikku/Animiru):
  **Animiru has NO torrent support** (anime-only, mpv player). The forks that DO — Animetail
  (Apache-2.0), Anikku (Apache-2.0), Kuukiyomi — AND upstream CloudStream (`ui/player/Torrent.kt`)
  all embed the **same engine**: `com.github.Diegopyl1209:torrentserver-aniyomi` (a gomobile
  Android build of the TorrServer-lineage Go server, YouROK/TorrServer lineage). The torrent
  extensions' shared contract is `source-api`'s **`TorrentUtils.getTorrentInfo`** — shipped
  identically by **83 GitHub repos** (Anikku, Dantotsu, AnymeX, …). This finding REVERSED the
  original libtorrent4j plan (anitorrent = GPL-3.0 ruled out earlier; libtorrent4j would have
  broken the ecosystem extensions the user wants to work). **Option 1 approved by the user**
  with two hard requirements: easily removable + zero impact on other systems.
- **The user's answers**: streaming + downloading BOTH, CS-first (Aniyomi torrent = later);
  keep the live checking card; keep the cover on the result notification (N1); the result card
  taps into the UPDATES screen (N2); the release (episode banner) notification keeps its
  details-page deep link; the APK size accepted; wizard + new-Home layout deferred.

## 2. The implementation (commit 8932faad — 33 files, +1489/-149; CI run 43282610739 GREEN first-try)

### 2a. THE TORRENT ENGINE (D-858) — the new `core:torrent` module (CS-first)

- **The dependency**: `com.github.Diegopyl1209:torrentserver-aniyomi:c18f58e51b` (JitPack —
  already in the settings repositories). JitPack was reachable from the local build AND CI
  first-try.
- **THE API DISCOVERY** (the compile caught it): the pinned artifact's real surface is
  `touch()` (gomobile init), `startTorrentServer(path)` (**void, ONE arg** — the Go server
  binds its DEFAULT port 8090), `stopTorrentServer()` (exists but upstream reports crashes —
  deliberately unused), `waitTorrentServer()` (blocks forever on dead starts — deliberately
  unused), `addTrackers(String)`. Upstream CS's 2-arg + port-return usage pins a NEWER engine
  commit than the ecosystem's shared `c18f58e51b` pin — our loader adapts to the pinned API:
  `touch()` → `startTorrentServer(dir)` → a bounded **/echo readiness poll** (20 × 1s).
  `go.Seq.load()` does NOT exist in this artifact.
- **The module** (everything torrent lives here): `TorrServerLoader` (the ONLY file importing
  `torrServer.*`; start + echo + the curated tracker list + the stale-cache wipe),
  `TorrentServerApi` (its own OkHttpClient — independence from the app network stack;
  POST /torrents add/get/drop/rem + /echo), `TorrentGateway` (the single injected facade:
  prepare → `TorrentPrepared(streamUrl, hash, title, status)`, status, forget, consent),
  `TorrentPreferences` (`torrent_enabled` DEFAULT OFF + `torrent_disclaimer_accepted`),
  `TorrentModels` (the wire contract + `streamUrl()` mirroring upstream's shape:
  `/stream/<encodedFile>?link=<hash>&index=<n>&play`), `di/TorrentModule`.
- **The CS path**: `CloudstreamLinkResolver.resolve(providerName, data, allowTorrentLinks)` —
  when enabled, TORRENT/MAGNET links pass through as the new `CsLinkType.TORRENT` (the flag
  rides the cache key; disabled = the pre-round hiding+counting, byte-identical behavior).
  The watch VM transforms at PLAY time: `requestPlay` branches TORRENT →
  `prepareTorrentAndPlay` ("Preparing torrent…" state) → the stream URL plays as VIDEO (the
  player NEVER sees a raw torrent link). `autoStart` NEVER auto-picks torrents (explicit user
  pick only). Episode switches + VM teardown `forget()` the hash server-side (drop+rem). The
  currently-playing card shows the live status line (`TorrentStatus.chipLine()`:
  seeders/peers/speed/preloaded %) polled once per second. The resolve sheet's rows show the
  built-in "Torrent" chip (`qualityLabel`).
- **The settings**: Settings → **Torrent** (hub row + search entry + `TorrentKey`): the
  enable toggle (default OFF) behind the **first-use disclaimer dialog** (peer-to-peer IP
  visibility + VPN recommendation + legality line) — enabling requires accepting.
- **REMOVABILITY (the user's explicit order)**: `core/torrent/TORRENT-REMOVAL.md` lists every
  seam (delete the module + ~8 documented call sites); `TORRENT-DESIGN.md` documents the
  behavior contract + the disclosed trade-offs (GPL-3.0 bundled component — the Animetail/
  Anikku posture; APK size ~30-50 MB universal — bigger than the libtorrent4j estimate,
  disclosed to the user at approval). **Zero manifest changes** (the engine is in-process;
  the FGS arrives with torrent downloads in T4).
- **The app-start cleanup**: `AnikutaApp.onCreate` wipes the last session's
  `cacheDir/torrent_tmp` (the server has no clean stop; upstream does the same).

### 2b. THE MINIMAL CHECK NOTIFICATION (D-859) — the user's "raw format" verdict

- The RESULT card is a headline now: **"N new episodes found!"** / **"Episode check complete"** /
  **"No new episodes — you're all caught up."** + ONE line ("Checked N anime"). The per-anime
  outcome lines (`perItemLine`: "failed (HTTP 403…)", "+2 new (next: re-check in 8h)"), the
  "+N more…" accumulation and the next-check projection are REMOVED from the notification —
  they live in the in-app Update-check history page.
- **N1 honored**: the anime cover stays as the large icon (the full-fledged system kept).
- **N2 honored**: the result card taps into the **Updates screen** (`open_updates` extra →
  `PendingNavigation.Request.openUpdates` → `UpdatesKey`; the found-episodes list); the
  FAILURE card keeps the check-history deep link; the release (episode banner) notification
  keeps its details-page deep link (unchanged).
- The LIVE checking card STAYS (the user's explicit call) — "Checking N of M" with the
  determinate bar.
- The deleted renderers: `summaryLine`/`bigBody`/`perItemLine`/`caughtUpBody`/`nextCheckLine`;
  `MAX_NOTIFICATION_ITEM_LINES` no longer referenced by the notifier. The summary MODEL is
  untouched (the history page still gets the full per-item records).

### 2c. THE 3-DAY LAUNCH GRACE (D-860) — the D-407 supersession

- `AdPreferences.firstOpenTimestamp` (persisted once on first read; an install that already
  consumed the old one-shot grace migrates with the anchor 3 days in the PAST — no free
  period granted retroactively) + `launchGraceMs = 3 days`.
- `AdsRepository.isWithinLaunchGrace()` replaces `consumeFirstOpenGrace()`; the coordinator's
  gate proceeds without an ad and records NO cooldown during the window — after day 3 the
  normal cooldown system applies exactly as before. The "Always sponsor" debug toggle
  still bypasses everything (unchanged).

### 2d. THE PERMISSIONS HUB (D-861) — one place for everything

- Settings → **Permissions** (`PermissionsKey` + hub row + search entry): POST_NOTIFICATIONS,
  the SAF download folder (with the current state + re-pick), the battery-optimization
  exemption, SYSTEM_ALERT_WINDOW, and the legacy gallery-save permission (pre-29 devices
  only) — each row: live granted/denied chip (re-verified on every ON_RESUME), a one-line
  "what it affects", one-tap Allow (the exact request path), and the App-info fallback.

## 3. The verification + the honest disclosure

- Local `:app:compileDebugKotlin` **GREEN** (the errors it caught: the pinned artifact's real
  API, an exhaustive-when in `CsSourceListUi`, the search-index `add()` split, a duplicate
  helper — all fixed).
- **THE DISCLOSURE — the local test suite did NOT run**: the first full-suite attempt crashed
  the Kotlin daemon and filled the user's machine memory (their order: never run local Gradle
  again). CI (run 43282610739, assembleDebug) is the round's verification; the touched logic
  has no test pins (the notifier rendering is app UI; the resolver flag defaults preserve the
  old behavior; `CsMediaTypes.mimeFor` gained an unreachable-by-design TORRENT arm).
- **CI ledger (D-472): ONE run this cycle** — Build APK 43282610739 (first-try GREEN). The
  docs commit rides after it (docs-only pushes trigger no CI).
- **NO release was ordered** — the mainline stays 1.1.20/10120 (D-430); the debug release
  waits for the user's explicit word (D-738).
- ntfy: the round-143 notices delivered (3 × HTTP 200).

## 4. What remains (the user's "tell me the things which are remaining")

- **T3** — the Aniyomi-extension torrent side (the `torrentutils` contract in source-api +
  the bridge wiring) — the user deferred it AFTER the CS side proves out on device.
- **T4** — torrent DOWNLOADING through the existing queue (the stream URL as a DownloadRequest
  source + the DATA_SYNC FGS) + the torrent settings expansion (storage/metered/trackers).
- The wizard rework + walkthrough (deferred to its own round), the new personalized Home
  (layout planning with the user), and the deferred trending/popular integration into Home.
- The dead `library_show_continue_watching` toggle removal lands with the Home work.
