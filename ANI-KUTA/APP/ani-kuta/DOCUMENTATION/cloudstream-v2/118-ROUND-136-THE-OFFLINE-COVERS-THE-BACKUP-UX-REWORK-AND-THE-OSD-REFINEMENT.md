# 118 — ROUND 136: the offline covers, the backup UX rework, and the OSD refinement

The v1.1.79 device round verified the round-135 player work ("quite
satisfied" with the sliders' look; seeking "perfect"; the extension update
options "finally" worked) and returned a detailed report centered on ONE
headline: **an offline restore failed to bring the library back to life** —
no covers anywhere, extension repositories missing, and the backup page's
"What to include" section was called "ugly" and ordered into a popup. The
user's closing instruction: "act like a professional UI designer." No
release was ordered this round; the round ends at CI green per the standing
rules.

## 1. The offline covers (W-D — the headline)

**The root cause of the empty grids.** The app renders every image from
URLs through Coil; a backup's `images/` payload carried only the download
folders' `.cover.jpg` files — which NO grid displays (they feed the
notification art). A data clear wiped Coil's disk cache, so a restored
library (offline, by the user's own test design) had nothing to paint. The
four covers the report saw were the SAF files, and they never surface in
any grid.

**The fix — a URL-keyed offline image store.** `OfflineImageStore`
(:core:backup) keeps bytes under `filesDir/offline_images/<sha256(url)>.img`
(filesDir, NOT cacheDir — the Coil disk cache lives there and both die on a
data clear; only the store survives the app's own lifetime) with a
concurrent hash-set index for O(1) lookups. At BACKUP time the engine
collects every library entry's THREE image URLs (`data_cover_url` — the
library grid renders `ext_thumbnail_url ?: data_cover_url` depending on the
display preference, so both are carried — plus `data_banner_url`) and the
extension icons (Aniyomi via the repo catalog; CloudStream with BOTH
`%size%` and `%exact_size%` resolved to 64, matching every render site),
fetches them (capped: 400 images, 2MB each, a 128MB total budget, 8-wide
parallel, a 60s wall-clock deadline, a connectivity probe, streamed reads
that stop hard at the cap — every bound from the verifier), and writes
`offline_images/<hash>.img` into the archive. At RESTORE the bytes land in
the store.

**The paint side — one interceptor.** `OfflineCoverInterceptor`
(coil3.intercept.Interceptor, registered FIRST in the app's custom
ImageLoader) rewrites any http(s) request whose hash is in the store to the
local file — keeping the URL as the explicit `memoryCacheKey` (Coil 3.0.4
keys the memory cache off the DATA; without the re-key the swap would
silently break memory caching — verified against the 3.0.4 sources). Every
AsyncImage in the app resolves this one loader, so the library grid, the
details headers, the extension icons, and the accent-color extractor all
gain offline coverage with zero call-site changes. Anything the store
doesn't have falls through untouched; the interceptor never fails a
request.

## 2. The repositories that never came back (W-E)

The archive DID carry the repo lists — `anikuta_extension_repos` /
`anikuta_cloudstream_repos` are plain SharedPreferences files in the prefs
payload. The defect was staleness: both repositories hold IN-MEMORY
StateFlows built once at app start, and the restore's prefs application
wrote behind their backs — the Extensions page kept showing the pre-restore
(empty) lists until a full process restart, and the onboarding path never
restarts. Both repositories gained `refresh()`; the bridge's new
`onPrefsRestored()` (called right after the prefs loop) refreshes them plus
the trust set. The bridge's `onCloudstreamRestored()` then triggers
`CloudstreamPluginManager.reloadAfterRestore()` — a new ASYNC entry that
runs the full refresh SERIALIZED on the manager's single load worker under
its mutex (a bare `loadAll()` from the restore thread would have violated
the D-668 single-worker contract over the loader's plain-HashMap registries
and held the restore hostage to plugin dex-loads — the verifier's P1,
caught and fixed before any device saw it). The restored repos and plugins
now appear LIVE.

## 3. The backup & restore UX rework (W-A/B/C/F)

**The page.** The bento BACKUP | RESTORE top stays (the report approved it).
The granular selector left the page body: a "What to include" row (icon
chip, a live "N of 13 items included" summary, an Edit affordance) opens a
CHECKMARK POPUP — four grouped sections of checkboxes bound to the same 13
persisted switches. The popup's dismissal bumps a revision counter so the
summary recomposes (the verifier's P1: the summary is a plain
SharedPreferences read — no snapshot state — and strong-skipping memoized
the item lambda). The cards share the app's icon-chip + ExtraBold-title +
one-muted-line language.

**The responses.** Toasts are formatted ("Backup created — 1,838 entries ·
231 covers · 80 Aniyomi · 30 CloudStream", thousands-grouped via a shared
`formatCount` in :core:common, Locale.US-pinned), and the backup toast
carries BOTH extension counts (the round-135 toast ignored the CloudStream
count — the report's "still said 80"). The restore page's preview and
result show the REAL key stats the report asked for — entries, categories,
history, tracking, downloads — with "Not included" for tables a granular
backup didn't carry (never a lying "0"), covers as "N (offline-ready)", and
both extension ecosystems counted. The onboarding restore step says
"Continue" (accent) after a successful restore — "Skip for now" made no
sense there — and its summaries use the same formatter.

## 4. The OSD refinement (W-G)

**Portrait.** The volume/brightness pill renders COMPACT (46×148dp, 8dp bar,
11sp SemiBold, 18dp icon — the report: "way too bold and a little bit way
too bigger"), and the vertical travel dropped from 600dp to ~300dp in
portrait (the report: "way too less sensitive… I had to scroll way too
much"; fullscreen keeps 600dp, which the same report called proper).

**The stutter.** The bar and the percentage read `state.level` DIRECTLY —
the round-134 animated intermediate retargeted per event and read as
catch-up stutter ("it should adjust the speed dynamically, but it should
not stutter"). The level is pure per-event float math (one percent costs
3dp of portrait travel — no visible quantization), so the finger IS the
smoothing. The verifier confirmed the direct drive cannot regress the
round-134 "20→70" jump.

**The design language.** All three OSD containers (volume/brightness, seek,
zoom) are slightly-rounded RECTANGLES (14dp/12dp corners, like the player's
other surfaces) instead of full pills, with 3dp-cornered bars, and the OSD
texts ride RobotoFamily. The portrait OSD now draws LAST (on top of the
controls layer — the fullscreen rule; the verifier caught portrait drawing
it underneath where the controls' top/bottom rows overlapped the pill).

## 5. The verification

Pass 1 (two parallel agents): the UI pass found 1×P1 (the stale include
summary) + 4×P2; the engine pass found 1×P1 (the CS manager concurrency
bypass), 5×P2 (the thread race on the store's index, the unbounded fetch
memory, the offline stall, the `%exact_size%` mismatch,
`anythingEnabled` omitting imageCovers), and swept the round-135 invariants
(ALL PASS — the restore order, the plugins_json filter, the dense cs3
index, the granular mapping). Every finding fixed. Pass 2 (one agent,
re-verification): **12/12 FIXED, no new defects**. The full unit test
suite passes; the debug APK assembles.

CI ledger this cycle: **1 run so far** (the Build APK for the round commit
41343019578). No release ordered.

## 6. What was intentionally NOT carried

The search/browse result grids and episode thumbnails stay URL-text (the
browse cache re-fetches with a 6h TTL; carrying every remote image is
unbounded) — the offline payload targets the library's own covers, the
details banners, and the extension icons, which is the set the report
actually missed. Restored covers can go stale if a CDN serves a newer image
under the same URL — accepted (AniList covers change URL when they change).
The stale-until-restart sweep (schedulers, launcher icon reconcile) is
P3-covered by the restart flow and documented in the decisions log.
