# 05 — EXTENSIONS AND TORRENT

## 1. The two extension ecosystems

**Aniyomi-style (APK extensions)** — `data/extension` + `core/source-api`:
- ExtensionManager: install (the intent ladder), trust (TrustService per-package),
  uninstall (the OS intent — NO DB rows are deleted on uninstall/untrust: the content
  survives, only the in-memory source map loses it), the sources map (sourceId → AnimeSource).
- The sources feed the unified bridge → the same Library/Details/Watch paths as AniList.

**CloudStream (JS plugins)** — `data/cloudstream` + `core/cloudstream-api`:
- CloudstreamPluginManager: the JS loader, the repo install, the uninstall (the plugin file
  delete + the store row — NO DB deletion).
- The bridge (CloudstreamAnimeSourceBridge) mintes synthetic source ids
  (`0x4000_0000_0000_0000L or providerName.hashCode()`); episodes resolve through
  `CloudstreamLinkResolver` → CsVideoLink (VIDEO/M3U8/DASH/TORRENT).

**The identity hand-off (D-849)**: opening a details page by mainId maps
`details.sourceId ?: content.extensionId` (0L is POISONOUS — it mints a new mainId) + the
animeUrl chain. The unified path: MainActivity.openDetailsByMainId (D-863) — the History/
Profile rows all funnel here; unresolvable extensions toast "Extension X not available".

**The extension-install caveat**: extensions reinstall on restore get trust re-applied;
the backup carries the APKs (Aniyomi) + the .cs3 plugins (CloudStream).

## 2. The torrent engine (round 143 — CS-first, REMOVABLE)

**The engine**: `com.github.Diegopyl1209:torrentserver-aniyomi:c18f58e51b` (JitPack) — the
TorrServer-lineage Go server compiled by gomobile, run IN-PROCESS (no manifest additions).
The whole ecosystem (Animetail/Anikku/CloudStream upstream) embeds the same engine — the
torrent extensions' shared contract is `source-api`'s TorrentUtils.getTorrentInfo (83 repos
ship it identically).

**The API of the pinned artifact**: `touch()` (the gomobile init), `startTorrentServer(path)`
(VOID — binds the DEFAULT port 8090), `stopTorrentServer()` (exists but is deliberately
UNUSED — upstream reports crashes), `waitTorrentServer()` (deliberately unused — blocks
forever on dead starts), `addTrackers(String)`. Readiness = a bounded /echo poll (20 × 1s).

**The flow**: Settings → Torrent (default OFF + the first-use disclaimer) → CS resolve
sheets show TORRENT-typed links (the passthrough flag rides the resolver's cache key) →
pick → prepare (add → hash → /stream URL) → MPV plays it as VIDEO → the status line polls
1 Hz (seeders/peers/speed/preloaded %) → the switch/teardown `forget`s it server-side.

**The seams (removal)**: core/torrent (delete) + settings.gradle include + app/cs-watch deps
+ AnikutaApp (the module + the cleanup call) + the CsWatchViewModel seam + the resolver flag
+ the CsLinkType.TORRENT enum + the TorrentSettings screen — ALL listed in
`core/torrent/TORRENT-REMOVAL.md`.

**The known limitations (disclosed, by design)**: port 8090 hardcoded (the pinned artifact's
behavior — a foreign TorrServer on 8090 would answer the echo; the fixes/options live in
09-KNOWN-ISSUES.md), the Go server has no clean stop (process-lifetime + the startup cache
wipe), streaming only (T4 downloads + the FGS pending), Aniyomi-side torrent = T3 pending.

## 3. The update ecosystem for extensions

- UpdateEngine: the smart release scheduling (the learned per-anime offsets, the backoff
  1h→24h, the 3-strike source-unavailable → auto-update disabled), the WorkManager periodic
  + the smart one-shots at the expected releases, the boot re-arm.
- The extension update checks run on every Extensions-page entry; the trusted updated rows
  pin to the top; index.min.json first (the min-only repos).
