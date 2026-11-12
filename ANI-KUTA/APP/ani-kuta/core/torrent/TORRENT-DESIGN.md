# THE TORRENT SYSTEM — DESIGN (round 143, D-858)

> The user's order: CloudStream torrent support first (streaming + downloading), the
> Aniyomi-extension side later (T3), everything INDEPENDENT and EASILY REMOVABLE, documented.

## 1. What this is

ANI-KUTA embeds the torrent engine the entire Aniyomi-fork ecosystem standardized on:
**`com.github.Diegopyl1209:torrentserver-aniyomi`** (pinned `c18f58e51b` — the same commit
upstream CloudStream, Anikku, and Animetail consume). It is a gomobile AAR wrapping a
**TorrServer-lineage Go server** (fork of YouROK/TorrServer, GPL-3.0 — the licensing posture
every Apache-2.0 fork in this ecosystem already ships).

- The Go server runs **IN-PROCESS** (a native library, no separate app, no manifest
  permissions) and serves **sequential torrent streaming over HTTP** on `127.0.0.1:<port>`
  with full Range support — MPV seeks escalate piece priorities server-side.
- There is deliberately **no stop call** (upstream's own `/shutdown` path is broken; the
  server lives with the process and the cache is wiped at the next app start).

## 2. The module map (core/torrent — the ONLY place that knows the engine exists)

| File | Role |
|---|---|
| `TorrentModels.kt` | The server's wire contract (`TorrentRequest` add/list/get/drop/rem, `TorrentStatus` + `streamUrl()` + the chip line) |
| `TorrServerLoader.kt` | The ONLY file importing `torrServer.*`/`go.Seq` — start, echo, trackers, stale-cache cleanup |
| `TorrentServerApi.kt` | REST client on its OWN OkHttp (independence from the app network stack) |
| `TorrentGateway.kt` | The single injected facade: `prepare(rawLink)` → `TorrentPrepared(streamUrl, hash, …)`, `status(hash)`, `forget(hash)`, consent state |
| `TorrentPreferences.kt` | `torrent_enabled` (DEFAULT OFF) + `torrent_disclaimer_accepted` |
| `di/TorrentModule.kt` | Koin wiring |

## 3. The integration seams (the complete list of non-module touch points)

1. **`data/cloudstream/.../playback/CloudstreamLinkResolver.kt`** — the TORRENT/MAGNET links
   are passed through as `CsLinkType.TORRENT` when `allowTorrentLinks = true` is passed
   (else hidden + counted, the pre-torrent behavior). The flag is a PARAMETER — this module
   does NOT depend on core:torrent.
2. **`core/cs-player/.../CsLinkModels.kt`** — one additive enum value `CsLinkType.TORRENT`
   (display only; the player never receives a torrent link — playback always gets a
   transformed plain HTTP stream URL).
3. **`feature/cs-watch/impl`** — the play-time transform: a TORRENT-type pick runs
   `gateway.prepare(...)` first ("Preparing torrent…" state), then plays the stream URL;
   the player chip polls `gateway.status(hash)`; the sheet shows torrent rows + the
   disclaimer gate; the footer's "torrent link(s) hidden" line reflects the new behavior.
4. **`app/.../settings/TorrentSettingsScreen.kt`** — the enable toggle (default OFF) + the
   disclaimer + engine info; registered in the Settings hub + the settings search.
5. **`AnikutaApp.onCreate`** — one cleanup call: `TorrServerLoader(context).cleanupStaleCache()`
   (wipes the last session's `cacheDir/torrent_tmp`).
6. **`AnikutaApp` module list** — `torrentModule` added.

## 4. The behavior contract

- **Default OFF**: with the toggle off, torrent links behave exactly as before v1.1.85
  (hidden + "N torrent link(s) hidden" in the resolve footer). Zero behavior change.
- **First use**: enabling in Settings → the disclaimer dialog (user-initiated torrent
  traffic, VPN hint) → `torrent_disclaimer_accepted = true`.
- **Playing**: pick a torrent row → server starts (first use takes a few seconds) →
  "Preparing torrent…" → the stream URL plays in MPV; the chip shows seeders/peers/speed/
  preloaded %; leaving the playback forgets the hash server-side.
- **Downloading**: T4 (later round) — the existing HTTP downloader fetches the stream URL
  while the server stays alive for the session; an FGS (the manifest's DATA_SYNC permission
  is already declared) covers background runs.

## 5. Disclosed trade-offs (the user approved, round 143)

- **GPL-3.0 bundled component** (the Go server binary; the app's own code stays as-is) —
  the same posture as Animetail/Anikku.
- **APK size**: a Go runtime per ABI — the universal release APK grows ~30-50 MB; the
  arm64-v8a debug build grows the most of the per-ABI builds. Gradle's `abiFilters` packages
  only the matching .so per APK.
- **Network reality**: DHT/UDP may be blocked on some carriers; thin swarms surface as
  "No peers answered" — honest error, no retry loop.

## 6. The engine's origin (the ecosystem reference)

- Engine: https://github.com/Diegopyl1209/torrentserver-aniyomi (TorrServer + Android build)
- Upstream TorrServer: https://github.com/yourok/torrserver (GPL-3.0)
- The CS-side reference this module mirrors: recloudstream/cloudstream `ui/player/Torrent.kt`
- The Aniyomi-fork references: Animetail (`data/torrent/service/TorrentServerService.kt`),
  Anikku (`data/torrentServer/`)
