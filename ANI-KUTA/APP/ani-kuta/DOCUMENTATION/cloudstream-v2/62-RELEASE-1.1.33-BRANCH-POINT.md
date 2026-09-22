# 62 — RELEASE v1.1.33 BRANCH POINT (Round 71 / D-559)

**Date:** 2026-09-22 · **Branch point:** `release/1.1.33` cut from the round-71 docs head on `feature/round-57-cloudstream-downloads` · **Feature CI:** 35765045924 GREEN on the FIRST run (67845eaa)

## What ships in v1.1.33 (10133)

The v1.1.32 device round's corrections — D-559, one release, the verified-good carry untouched:

- **The timeline's chicken neck** — the date blob and the details card CONNECT again through a thin ~10dp neck (one outline, three sub-shapes, 2dp anti-hairline overlaps); the D-558 "expanding toward the date" verdict stays honored.
- **The frosted TEXT** — the cinema number style now frosts the TEXT ITSELF: a blurred halo of the same glyphs behind (a real RenderEffect blur on Android 12+) and a translucent crisp copy on top; the imagery shows through the number. The plate and veil are gone.
- **The heading IS one back button** — the arrow and the title are a single tappable element with one ripple: the arrow hugs the screen edge (beyond the content padding), the gap is 4dp, and the title sits back near its original position.
- **My Profile fixed** — the title no longer truncates to "My PR…"; the header's hidden tab pill no longer eats its width.
- **The settings search, polished** — the bar's height stays constant while typing; results render in one clean grouped card with per-page icons, dividers and chevrons; "episode thumbnail" (and the whole thumbnail/poster family) now finds the real rows — the poster screen's own options included — and lands with a scroll + pulse; re-entering Settings starts with an empty bar; the device back gesture clears the search before leaving.
- Untouched on purpose: the scroll collapse ("exactly like how it is meant to be"), the cinema options section, the swipe/download/preview behaviors, the 0.67 sheets.

## Release mechanics

- `release/1.1.33` cut from the docs head; ONE bump commit: `versionName 1.1.33 / versionCode 10133` (the D-430 doctrine: the bump rides the release branch exclusively; the feature line stays 1.1.20/10120; the record comment rides the bump).
- Tag `v1.1.33` (message file repo-external) → the Release APK workflow (release run #2 of the ≤2 budget).
- LIVE verification: stable, --latest, not draft/prerelease, `ani-kuta-v1.1.33-debug-arm64-v8a.apk` + `SHA256SUMS.txt`; 10133 > 10132 → in-app update.
