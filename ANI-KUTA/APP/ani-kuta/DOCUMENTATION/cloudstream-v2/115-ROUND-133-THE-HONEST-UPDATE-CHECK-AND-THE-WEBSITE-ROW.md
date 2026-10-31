# 115 · ROUND 133 — THE HONEST UPDATE CHECK AND THE WEBSITE ROW

> 2026-11-01. The user's report: the in-app "Check for updates" found no
> update while v1.1.76 was live on the dev repo; plus the About screen's
> Website row should say "Go to the website" instead of showing the URL.
> Decision D-815.

## 1. The diagnosis

Every step of the updater's code path was verified against reality:

- **The wiring** (`AppUpdateModule`): the `.debug` package resolves the dev
  repo `testplay-byte/ANI-KUTA` (D-674's application-id check, not the
  runtime debuggable flag). Correct.
- **The live API**: the same request the app makes — unauthenticated, same
  Accept + User-Agent headers — returns v1.1.76 (draft=false,
  prerelease=false) with the arm64 asset. Correct.
- **The pick + compare**: the version-tuple max picks 1.1.76 over every
  older release; the installed versionName (1.1.73/74/75 — any of them)
  parses lower. The comparison is correct.

**The real defect was the error contract.** `UpdateSource` mandated
"never throw — return null on failure", and null meant BOTH "no update" and
"the check failed". A dead network, an HTTP 403 rate limit (the
unauthenticated API allows 60 requests/hour per IP), or a malformed response
all collapsed into the same flash of **"You are on the latest version"** —
the check's failure was invisible, and the report ("no new update detected")
is exactly what a failed check looks like.

## 2. The fix (D-815)

- `GitHubUpdateSource` now **throws `UpdateCheckException`** on HTTP errors
  (naming the code, and "rate limit — try again later" for 403), an empty
  body, a malformed JSON response, and network exceptions (naming the
  exception type + message). Returning null now means ONLY "the source
  answered and the installed version is the newest".
- `AppUpdateManager.checkForUpdate()` records the exception as
  `lastCheckError` (the About screen's inline error card already renders
  that state) and flashes "up to date" **only when a source actually
  answered**.
- The failure is also named in logcat under `Anikuta:Core:AppUpdate:GitHub`
  (`fetchLatestUpdate: HTTP 403 (rate limit — try again later)` and friends)
  — `planning/tools/parse_logcat.py` picks it up.

So the user's NEXT check either finds the update (the code path is proven
against the live API) or shows the real reason on the error card.

## 3. The About row

Community → Website: the subtitle is now **"Go to the website"** — the URL
is gone, per the order.

## 4. The release

The fix lives IN the app, so it can only reach the device through a release:
**v1.1.77** cut per Routine A from the round-133 green head (`11193359`),
ONE bump commit (10176/1.1.76 → 10177/1.1.77), annotated tag `v1.1.77` →
the Release APK workflow. CI ledger this cycle: 2 runs (the Build APK +
the Release APK), inside D-472. Verified live after the run (checksum +
manifest). v1.1.76 installs get the in-app offer.
