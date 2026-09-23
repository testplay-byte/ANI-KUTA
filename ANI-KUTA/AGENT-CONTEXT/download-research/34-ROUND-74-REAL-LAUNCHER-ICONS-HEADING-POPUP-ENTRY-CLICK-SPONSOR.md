# 34 — The REAL launcher-icon switch + the presets-only page + the heading-only popup + the entry-click Always-sponsor trigger (Round 74 / D-562, the v1.1.35 device round)

**Date:** 2026-09-23 (round 74) · **Branch:** `feature/round-57-cloudstream-downloads` · **Base:** 4d5f2fc6 (round-73 LIVE mirror) · **Next release:** v1.1.36 (10136, both releases this cycle)

The v1.1.35 device round set five directives — one acceptance carried over implicitly (the D-561 overlay row stays frozen), four fixes:

1. **The sponsor popup "does not look good"** — "I think we should only… show the heading Support [AniKuta]."
2. **The app icon functionality is BROKEN** — "when I change the app icon in the settings, then the app icon should actually be changed for the application, like the application's icon should properly change. Because this functionality was working previously."
3. **Remove the GitHub-repository icon search** — "I would like you to remove this functionality and only keep the preset app icons."
4. **The Always-sponsor page** — the heading "is not proper"; the toggle "does not need to be given a description"; and the trigger is wrong: "it currently works as such that every time the app opens. Instead what I wanted was that every time the user clicks on an entry, then it will show the sponsor rather than every time opening up the app."
5. **The ACTUAL release at 1.1.3(6)** — both releases again this cycle.

## 1. WHY THE ICON NEVER MOVED (the root cause)

The "previously working" functionality the user remembers is **D-417 (v1.1.1)**: 8 baked mipmaps + 8 activity-aliases carrying MAIN/LAUNCHER, switched via `PackageManager.setComponentEnabledSetting`. **D-434 (round 37)** removed that system on the user's then-order and rebuilt the page catalog-only; **D-561** then added the six presets — but both rode the **in-app-only** machinery (`inAppOverridePath` → an exported PNG → the hero `AsyncImage`). Android forbids runtime launcher icons from arbitrary bitmaps, so v1.1.35's pick could NEVER move the home-screen icon. The fix restores the proven D-417 shape with the presets as the aliases:

- **The manifest**: MAIN/LAUNCHER moves OFF `MainActivity` (its deep-link/OAuth filters stay on the activity) onto **SEVEN aliases**: `.icons.IconDefault` (enabled, `@mipmap/ic_launcher` + `_round`) + `.icons.Icon{Dark,Teal,Sky,Gold,Green,Pink}` (disabled, `@mipmap/ic_launcher_<key>` for both icon and roundIcon). Alias class names resolve against the NAMESPACE, not the applicationId — the `.debug` suffixed build uses the SAME strings (the D-417 verified pattern).
- **The resources** (per preset key): `mipmap-anydpi-v26/ic_launcher_<key>.xml` — the SAME full-bleed adaptive structure as the default icon (background = `@drawable/preset_icon_<key>`, foreground = the shared empty `@mipmap/ic_launcher_fg_none`) — plus `mipmap-xxxhdpi/ic_launcher_<key>.jpg` (a copy of the baked artwork) because **minSdk is 24** and an adaptive-icon XML cannot inflate below 26.
- **The switch** (`AppIconController.applyLauncherIcon`): enable the TARGET alias FIRST (`COMPONENT_ENABLED_STATE_ENABLED` + `DONT_KILL_APP`), THEN disable the previously-active one — the app never has zero enabled launcher entries. Persisted in `AppIconPreferences.launcherIconKey` ("" = default).
- **The self-heal** (`reconcileLauncherIcon`, called from `AnikutaApp.onCreate` AND on every page open): PackageManager component states can reset to the manifest defaults on an app UPDATE while SharedPreferences survive — without this the launcher would fall back to the default icon while the page claimed otherwise. One binder read when nothing is persisted; one re-apply when the saved alias is not EXPLICITLY enabled.

## 2. THE PRESETS-ONLY PAGE (the catalog dies)

`AppIconScreen` loses the entire GitHub catalog: `fetchCatalog`/`parseCatalog`/`loadCatalogIconFile`/`httpGet`/`CATALOG_API_URL`/`CatalogIcon`/`catalogJson`/the grid/the refresh IconButton/the "There aren't any icons yet." note/the `catalogDir` cache — deleted outright. The exported-PNG override machinery (`loadPresetIconFile`/`presetsDir`/`presetExportPath`/`processSquare`) dies with it — the hero now paints the ACTIVE LAUNCHER icon straight from the resource (`activeIconRes`) and the cells paint the baked drawables. The page is: hero ("Current icon" + the active name + a quiet "Launcher icon" caption while a preset is active) → the six-preset grid (chunked 4, the D-432 full-artwork rounded-cell rule, the 2dp primary selection ring) → "Back to the app's icon" (resets to the default alias) while a preset is active. Taps toast "App icon updated". `AppIconPreferences` shrinks to ONE field (`launcherIconKey` + its flow); the Appearance row's subtitle becomes "Preset app icons".

## 3. THE HEADING-ONLY POPUP (SmartLinkAdInterstitial)

The **hero bubbles are deleted** from every state (the `HeroBubble` composable removed) — "I think we should only… show the heading Support AniKuta": the heading is the card's top element, nothing decorates it.

- **Pending**: **"Support AniKuta"** (titleLarge) → **"(It just takes a few seconds)"** → **Continue** → **Not now** → the FROZEN overlay row while the consent is missing (title + the ONE description, granted = gone — the D-560/D-561 approved behavior untouched).
- **Waiting**: the bare 26dp spinner (the state needs something moving) → "See you in a moment" → "(Come back to AniKuta when you're ready)".
- **TryAgain**: "That was too quick" → "(Stay for ${seconds}s, then come back)" → Try again → Not now → the overlay row.
- The card carries ZERO "sponsor" strings; the D-560 machinery (Crossfade, back-cancels, the pill show-in-tap-handler wiring, the ON_RESUME consent re-read, the Idle early return) is byte-identical.

## 4. THE ALWAYS-SPONSOR TRIGGER MOVES TO THE ENTRY CLICK

The D-561 app-open path dies: **`AlwaysSponsorGate`** (the ProcessLifecycleOwner observer) is deleted, `AdsCoordinator.onAppOpened()` + `lastOpenAdCompletedAt` + the 2s respawn-suppression window are deleted (they existed only to keep the app-open trigger from looping). The gate now lives in **`requestNavigation`** — the one helper every navigate-to-details tap goes through — right after the in-flight guard: **ON = EVERY entry click shows the interstitial** ("every time the user clicks on an entry, then it will show the sponsor"), with the normal gates ALL bypassed (the global enabled flag, the one-per-install grace, the 6h cooldown, the offline deferral — the toggle means ALWAYS; a failed browser-open still completes gracefully). The held proceed is the REAL navigation, so completing the popup lands the user on the tapped entry. Toggle OFF = the normal system below, byte-for-byte.

**The page** (`SponsorDebugScreen`): the heading becomes **"Debug options"** (it IS the debug-options page — the D-561 heading repeated the toggle's own title), the section label stays "Debug", and the row is the title + the Switch, NOTHING else ("that does not need to be given a description").

## 5. THE RELEASES (v1.1.36 — BOTH lines)

The user's explicit order ("make sure to do the actual release too with the version 1.1.3") — the third consecutive cycle superseding the ≤2 budget: the disclosed ledger is THREE runs (implementation, the debug release on the tag, the all-ABI dispatch on the tag). Both releases ride the SAME tag v1.1.36 → versionName 1.1.36 / versionCode 10136: the debug arm64 APK via `release-apk.yml` (the tag push) and the five release-signed variants via `release-build-once.yml` (dispatch, artifacts-only — D-447 stands).

## Verification

- Stale-symbol sweep: ZERO code references to `onAppOpened` / `AlwaysSponsorGate` / `inAppOverridePath` / `catalogJson` / `fetchCatalog` / `CatalogIcon` / `HeroBubble` / the deleted controller helpers anywhere in `app/`+`core/` (the only match is the historical doc comment in AppIconPreferences).
- Manifest machine-validated (minidom): 7 aliases, IconDefault enabled + the six presets disabled, each preset alias carrying its own `@mipmap/ic_launcher_<key>`.
- Brace balance machine-verified on all ten touched Kotlin files (nested-comment-aware checker): all OK.
- User-visible string audit: the popup carries ZERO "sponsor" strings; the debug page carries no description; the App Icon page carries no repository/catalog text.
