# 49 — RELEASE 1.1.20 BRANCH POINT (round 57)

`release/1.1.20` cut from the round-57 feature head `cf067e68`
(`feature/round-57-cloudstream-downloads`, CI **GREEN** — run 35522908889).
Version bump rides THIS branch as its first commit: **1.1.20 / 10120**.

## What v1.1.20 carries (the round-57 device round — the user's verdicts: all five
poster templates PROPER, Browse + Library "quite satisfied")

| Decision | What shipped |
| --- | --- |
| **D-536** | The poster master toggle is the Elements card's LAST row, titled **"Poster"** — the top card is gone; the preview, Shuffle, Layout, Artwork, the element rows and the section label each collapse around the never-disappearing toggle. |
| **D-537** | The CS player pauses on app-background (the MPV watch screen's exact ON_STOP observer; no auto-resume — both players identical). |
| **D-538** | The auto-link SKIP is PERSISTENT per (sourceId, animeUrl) — reopening content never re-attempts; swipe-dismiss stays session-only; a manual link/unlink clears the flag. |
| **D-539** | **CS DASH DOWNLOADS** — the research doc 17's Option C implemented: the resolve sheet's DASH filter is gone; the manifest + chosen-rep segments cache into the app-private SimpleCache (`csdash|` keys, NoOp eviction) through the SAME queue/service/notifications; the SAF folder keeps `.data.json` + cover + subtitles with NO video file; DB rows carry the `csdash:` marker (zero schema changes); offline playback rides the existing CS watch screen (`startOfflineDash` over a CacheDataSource + the maxVideoHeight pin); downloaded-season switching prefers the cache; the delete path purges the cache; DRM manifests fail honestly. |
| **D-540** | Ecosystem truth (CS content records system/extension_type **"cloudstream"**, the bit-62 flag, the idempotent startup heal) + the data.json translation (ContentIdentitySync fires the previously-dead contentId sync queries + the verified identity rewrite on every source switch — downloads survive, files never move, `providerName` rides the durable metadata). |
| **D-541** | The review round: 3 blockers + 4 risks found and ALL fixed pre-CI (the suspend interface, the offline key's navigation, the stale offline request, dashManifestUrl in the rebuild, direct-children addressing, Locale.ROOT, the guarded offline switch). |

## The CI ledger (this cycle)

- Implementation: run 35520223532 (red: setMaxVideoHeight/baseClient) → 35520604934
  (red: the $-template escapes + resume imports) → 35521117893 (red: the
  module-isolation primitives rule) → 35521703890 (red: the AnimatedVisibility
  receiver resolution) → 35522908889 **GREEN** on cf067e68. Four one-fix pushes —
  every fix was a single-cause, log-diagnosed correction (the one-fix-push pattern).
- The release costs exactly ONE more run (the tag-driven release-apk.yml; the
  branch push is docs-only filtered).

## The launch pad

The DASH design + lifecycle rules: `AGENT-CONTEXT/download-research/18-CS-DASH-DOWNLOAD-IMPLEMENTATION.md`.
The handoff: `AGENT-CONTEXT/HANDOFF-POSTER-NOTIFICATIONS.md` §14.
