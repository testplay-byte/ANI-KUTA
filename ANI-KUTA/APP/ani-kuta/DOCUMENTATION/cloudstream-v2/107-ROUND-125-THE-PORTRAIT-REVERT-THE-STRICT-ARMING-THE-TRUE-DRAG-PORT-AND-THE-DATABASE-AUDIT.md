# Round 125 — The Portrait Revert, the Strict Arming, the True Drag Port, and the Database Audit

> THE ORDER (the user's round-124 device report — "let's make it our final improvements in these areas"): the Up Next card STILL showed over a failure; the portrait mode opened right-side drawers for subtitles/qualities — "you were not supposed to do… what I have never told you about… properly revert this completely to the exact same how it was before"; the drag is "still not smooth, not magnetic" — "take proper reference from that area" (the download-settings priority list) — with a transparent dragged background and glitched swaps; the Paused tag shouldn't show "0%"; the quality drawer could be wider; in fullscreen the subtitle SETTINGS should slide from the LEFT as the right options drawer disappears; the details page "did not load the details from the extensions properly… think about the database logically… don't change it… if you find any flaws create a proper HTML file representing all of them for me." PASS this round: the Up Next 10s window + the replay re-offer, the honest watchdog (paused-at-start now plays when switched), the scrim fade, the drawer centering, the skip-entry removal.

## 1. The card on failure — the last two orderings (D-780)

The round-124 strict arming kept a `dur <= 0` arm-branch, and a dead stream exploits BOTH remaining orderings: the EOF pin can arrive BEFORE the duration property (armed at dur=0 — then the duration arrives and the poll's band matches), or the stream emits a single `pos=0` before EOFing (armed at 0 — then the EOF hook's gates pass). Arming is now STRICT mid-file (`dur > 0 && pos > 0 && pos < dur - 1`): real playback always advances past 0 within seconds, every consumer of the gate requires dur > 0 anyway, and the pin at EOF never arms. The poll's offer band is `0..10 seconds left` (a Float conversion — `Int in FloatRange` has no stdlib operator), so a pinned/overshooting position is the EOF hook's business, never the poll's.

## 2. The portrait revert (D-781)

The round-124 conversion keyed nothing on mode — the sheets are mounted outside the fullscreen/portrait branches, so portrait got right-side drawers the user never asked for. The correction is structural: `SubtitleTracksSheet`, `QualitySheet`, and `SubtitleSettingsSheet` each take `asDrawer` and the WatchScreen passes `playerMode == PlayerMode.FULLSCREEN`. The selection bodies are byte-identical for both presentations (a local `host` composable lambda chooses the shell), portrait gets the exact pre-124 bottom sheets back. The shared shell moved to core.player as `PlayerSideDrawerShell` with `fromLeft`/`widthFraction`/`maxWidthDp` params — one implementation, three consumers.

## 3. The wider drawer + the left-side settings (D-782)

The fullscreen quality/subtitle drawers run 45% of the screen capped at 400dp (the episodes drawer keeps its approved 40%/340). And the subtitle SETTINGS panel — previously a bottom sheet even in fullscreen — slides in from the LEFT in fullscreen (the mirror of the right-side options drawer, which closes as it opens — the onOpenSettings wiring already closed it), while portrait keeps its bottom sheet.

## 4. The true drag port (D-783)

The round-124 port deviated from the reference in three load-bearing ways, and the user felt every one. (1) **The gesture was keyed `pointerInput(task.id)`** — when a swap moved the dragged task to another slot, the key changed at that slot and the ONGOING GESTURE DIED mid-drag: that is the "everything got glitched out" report, mechanically. The reference's `pointerInput(Unit)` per SLOT is load-bearing — slot indices are stable across swaps, so the handler never restarts and the captured index stays correct. (2) A `verticalScroll` wrapper the reference doesn't have. (3) Transparent row backgrounds — "I could see the first entry behind it". The port is now 1:1: identical geometry (fixed 52dp rows, 2dp gaps, integer swap math with the offset rebase — the reference's height/pitch mismatch is the slightly-early swap the user LIKES), an OPAQUE dragged surface with an 8dp drop shadow, tinted row surfaces like the reference, commit-on-drop, rollback-on-cancel, and the pinned rows still un-passable.

## 5. The Paused tag trims the zero (R6)

`Paused — 0%` carried no information; a 0%-paused task reads just "Paused" (both the queue row and the reorder card's status line).

## 6. The details/display chain — five writers, five fixes (D-784)

A read-only audit (research agent, schema untouched) traced "switch to extension doesn't load properly / refresh makes it worse" to concrete writers: **RC1** `refreshExtensionAxis` and the reopen enrichment assigned a sparse fetch RAW over the live base (the provider returns a stub on a Cloudflare wall — the extension-first load path had the null-merge fix since round 49; the refresh paths never did) — both null-merge now, as does the post-link enrichment; **RC2** the link write stored the raw int status (`"0"` rendered as the status row) and its partial UPDATE wiped the extras JSON — the status maps to its real name now and the extras are preserved; **RC3** the post-link enrichment was the ONE ext-axis writer that didn't null-merge — a sparse enrichment could erase stored description/genres/status and the persisted year/score/title; **RC5** the reopen restore dropped the persisted year/score (the Year/Score rows vanished on switch) and trusted legacy statuses — it restores them and normalizes the legacy ints now. The strict-switching rule itself (extension truth is extension truth, no AniList fallback) is by design and unchanged.

## 7. The database audit (the user's HTML report)

Per the order — the schema was NOT changed and the flaws are recorded in the workspace report: `planning/2026-10-25-database-flaws-report.html`. The design is sound (two clean axes, denormalized identity, additive extras JSON); the flaws are writer discipline and semantics: **B1** the download scanner can roll `main_entry` back in time (`INSERT OR REPLACE` from the `.data.json` snapshot + a reconciliation that reads after the rollback), **B2** the axis UPDATEs write every column including nulls (COALESCE-defense would fix the class in SQL — gated on §30), **B3** `display_source` can disagree with what's populated, **B4** restored extension-only downloads have no ext axis, **B5** three title sources, **B6** inconsistent content_id ecosystems, **B7** the same id in two typed columns. The report also closes the regression question: round-124's title work checked out clean at every site.

## 8. The verification

The pre-CI verifier compiled the touched modules with the project toolchain and caught FOUR compile errors pre-push: `:core:player` never declared activity-compose (material3 ships it runtime-scope only — PlayerSideDrawer's BackHandler wouldn't resolve), a missing `fillMaxSize` import in SubtitleSettingsSheet, the `Int in FloatRange` type error, and the `@OptIn` that was deleted along with the old shell (material3's ModalBottomSheet is experimental). All fixed pre-push.

## 9. The CI ledger

Commit `781b5a81` (fix, 8 files, +532/−255, one new file) → run **GREEN** (the `anikuta-apk` artifact, versionCode 10120; the mainline stays 1.1.20/10120 per the standing rule). ONE run — inside the D-472 budget. The docs push is paths-ignored.

## 10. The device checklist

`planning/2026-10-25-round-125-device-test.html` (+ the .md twin) in the workspace pack — and the database report at `planning/2026-10-25-database-flaws-report.html`.
