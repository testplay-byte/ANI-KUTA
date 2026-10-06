# Round 119 — The Settings Icon Tile

> **Status:** IMPLEMENTED + CI GREEN FIRST-TRY (2026-10-02, commit e733bbe0 → run 37499473609). Decision: **D-744**. The Build APK debug run **37499473609** is the round's deliverable (the artifact carries versionCode **10120** — the standing wrinkle: it will not install over the v1.1.72 release without `adb install -d` or an uninstall).
> **The order (the user, round 119):** the settings-area leading icons ("in the more section it shows the SVG icons, in the settings themselves it shows SVG icons, and in many other places like those") get "a background kind of feel… a square with rounded corners, which the SVG icons logos will be living on, will have a background color, which will be separate from the background color itself… a better, cleaner looking color." Work optimally, do not rush, report any other issues found — and "if I do not like the things, then we will revert it."

## 1. What the order means (traced before execution)

The user wants an **ionicons-style icon tile**: the vector glyphs that lead the
More / Settings rows sit on a small rounded square whose background color is
distinct from (and cleaner than) the row surface. The scope is the
**nav-row leading-icon language** — not action buttons, not the extension
rows (those draw real extension images), not the profile edit icons.

## 2. The research (what the audit found)

- The leading-icon pattern lives in **`MoreListRow`** (`:core:designsystem`) —
  used by the More page (7 rows), the Settings hub (`HubSection` → `MoreListRow`),
  and the About / Appearance / SponsorDebug / Notifications screens — plus two
  hand-rolled copies of the same anatomy: **AboutScreen's update-check row**
  (with the `isChecking` spinner + the red update dot) and
  **SponsorDebugScreen's `DebugDoorRow`**.
- The settings **search-result row** (D-558) already wore the exact recipe:
  a 38dp rounded-square (`RoundedCornerShape(12.dp)`) tinted
  `primary.copy(alpha = 0.12f)` with a centered `primary`-tinted glyph.
- ⚠ **The D-250 conflict:** DESIGN-LANGUAGE §2.4 banned the tile (2026-08-24)
  — but the offending pattern was per-screen `primaryContainer` CHIP VARIANTS
  that made Settings look like a different format from the More page. The
  user's explicit round-119 order supersedes the ruling; the re-introduction
  is unified through ONE primitive so the D-250 inconsistency class cannot
  recur. Flagged in D-744 and in §2.4's history note (not silently ignored).

## 3. The implementation (5 files, one recipe)

1. **NEW `SettingsIconTile`** (`core/designsystem/.../component/SettingsIconTile.kt`)
   — the tile primitive: 38dp, `RoundedCornerShape(12.dp)`,
   `primary.copy(alpha = 0.12f)`, centered content slot, optional 8dp red
   notification dot at the tile's top-end corner. The glyph keeps its own
   size (24dp on nav rows) and `primary` tint.
2. **`MoreListRow`** — the leading `Box` + bare icon → `SettingsIconTile`:
   every More page row, Settings hub row, About/Appearance/Notifications/
   SponsorDebug row gets the tile; the update dot moved from the glyph's
   corner to the tile's corner. Row metrics unchanged (the tile is 38dp vs
   the ~40dp title+subtitle column — no taller rows).
3. **`AboutScreen`** — the update-check row's leading block →
   `SettingsIconTile(showDot = showUpdateDot)` with the `isChecking` spinner
   inside the tile.
4. **`SponsorDebugScreen`** — `DebugDoorRow` → `SettingsIconTile`.
5. **`SettingsScreen`** — the search-result row's hand-rolled tile →
   `SettingsIconTile` (the original recipe, now on the shared primitive).

Plus **`DESIGN-LANGUAGE.md` §2.4** rewritten: the tile rule, the call-site
discipline (via `MoreListRow`; direct calls only for the hand-rolled rows;
no per-screen variants), and the D-250 → D-744 history.

Import hygiene verified per file (the removed dot blocks left `CircleShape`/
`Color`/`clip` unused in three files — cleaned).

## 4. Deliberately out of scope

- The extensions-settings rows (`ExtensionIcon` / `CsPluginIcon` draw REAL
  extension images — a different language, not the "SVG glyphs" the order
  targets).
- The profile edit sections + action buttons (trailing utility icons).
- Any color/shape systemization beyond the one primitive (no new theme
  tokens — the recipe matches the established D-558 values exactly).

## 5. The device checklist

**More page**
- [ ] Open More → every row's icon sits on a rounded-square lime-tinted tile; the tile reads as its own surface against the row card.
- [ ] The rows' heights look unchanged (no taller/shorter rows).
- [ ] The update dot (if an update is pending) sits at the tile's top-end corner.

**Settings hub**
- [ ] Open Settings → Appearance / Extensions / Player / Notifications / Bug-report rows all wear the same tile as the More page (one format, no per-screen difference).

**About**
- [ ] About → the "Check for updates" row's refresh glyph sits on the tile.
- [ ] Tap it → the spinner renders INSIDE the tile (not beside a bare tile).

**Sponsor/debug**
- [ ] The debug door row (after the 10-second hold on Debug options) wears the tile.

**Search**
- [ ] Settings search → the result rows look IDENTICAL to before (they had the tile already; the primitive changed nothing visually).

**Press feedback**
- [ ] Tap any row → the whole row scales 0.97 as before; the tile does not flicker or re-tint during the press.

## 6. The revert path

The user: "if I do not like the things, then we will revert it." The revert is
ONE commit: the four call sites swap `SettingsIconTile { … }` back to the bare
`Icon` (and §2.4 returns to the D-250 wording). Nothing else depends on the
primitive.

## 7. THE DEBUG RELEASE — v1.1.73 (Routine A, the user's explicit order)

The user's order after the artifact wrinkle surfaced: "i do want the debug release which i can directly update from the app and you are only to build like this only build the release when i tell you not before that." D-738's recovery path exercised — the DEBUG line only (the professional line stays at v1.1.5).

1. **The branch:** `release/1.1.73` cut from the round-119 green head **e733bbe0**; ONE commit on top — the bump `10120 → 10173` / `1.1.20 → 1.1.73` in `AndroidConfig.kt` (with the release-comment block; the D-430 comment intact). 10173 > 10172 → the in-place update works.
2. **The tag:** the annotated **`v1.1.73`** (the round-119 bullets) → triggers `release-apk.yml` → run **37502575132** (the tag path: DEBUG APK, arm64-v8a, debug-signed with the committed keystore, GitHub Release on testplay-byte, stable + latest, `ani-kuta-v1.1.73-debug-arm64-v8a.apk` + `SHA256SUMS`).
3. **The CI ledger (D-472):** 2 runs this cycle — the implementation Build APK (37499473609) + this Release APK. The release-branch push itself triggered nothing (D-435 removed `release/**` from the build triggers).
4. **The mainline discipline:** the mainline stays 1.1.20/10120 (D-430); the bump rides the release branch only.
5. **Run verdict + live verification:** recorded at the bottom of this section (CI-verified via the API before the round closed).
6. **THE VERDICT (verified via the API, never assumed):** run **37502575132 GREEN first-try**; the release LIVE — `/releases/latest` resolves to **v1.1.73** (id 404975988, draft=false, **prerelease=false** — the updater's stable-visibility requirement, published 2026-10-06T17:23:02Z), the assets `ani-kuta-v1.1.73-debug-arm64-v8a.apk` (63,542,097 bytes) + `SHA256SUMS.txt` both present. The round is fully closed; the user's in-place update from v1.1.72 → v1.1.73 is the next input.
