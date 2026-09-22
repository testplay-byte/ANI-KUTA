# 31 — The timeline's chicken neck, the frosted TEXT, the one-element heading, and the search polish (Round 71 / D-559, the v1.1.32 device round)

**Date:** 2026-09-22 (round 71) · **Branch:** `feature/round-57-cloudstream-downloads` · **Base:** 40dc7ae7 (round-70 LIVE mirror) · **Implementation:** 67845eaa · **Next release:** v1.1.33 (10133)

The v1.1.32 device round CONFIRMED the carry and refined the rest. The user's verdict, verbatim anchors:

- The scroll collapse is **"proper. It is well handled and properly managed, and everything is exactly like how it is meant to be."** — FROZEN, untouched.
- The cinema options are **"exactly like how I hoped for them to be"** (position left/right + solid/frosted both selectable) — but the **frosted effect is wrong**: "By frosted effect, what I meant for you was that the text itself being frosted rather than it getting a frosted effect on it. The text itself should be given the frosted effect."
- The timeline gap overshot: "instead of making the gap thin, you just outright removed it, like there is no connection between the left release date and the right side content itself … it should be like a chicken neck kind of thing, like a blob kind of effect, both of them being connected but with a thin chicken neck kind of feel."
- The heading-back WORKS everywhere ("I clicked on the headings, and it properly led me to the back"), but the composition is wrong: "combine the back arrow button and the settings headings … into a single element. If the user clicks on the back button, it is considered as clicking on the heading … reduce the gap between the two, and also I would like you to move the arrow much more to the left side, more than the padding on the left side … so that the title stays exactly where it was previously, almost."
- My Profile truncates: "the My Profile text was not showing at the top … It was only showing My PR, then three dots … when I scrolled, then it would start to show properly."
- The search: the results UI is ugly; the bar grows when typing; "episode thumbnail" finds nothing despite real rows; re-entry must start empty; the device back must clear before it pops.

## 1. THE CHICKEN NECK (TimelineBlobCardShape, 3 sub-shapes in ONE outline)

D-558 made the bump and the card DISJOINT (card left 52dp vs bump right 44dp) — the user read the honest 8dp breath as a full disconnection. D-559 keeps both bodies exactly where D-558 put them (the "expanding towards the release date" complaint stays honored) and reconnects them with a **neck**: a ~10dp-tall horizontal band spanning `bumpRight - 2dp → cardLeft + 2dp`, centered on the bump's vertical midline (where the stadium's edge runs tangent-vertical, so the bump-side join reads smooth), with 2dp corner radius and **2dp overlaps into BOTH bodies** (an exact-tangent join would leave an antialiasing hairline; overlapping same-direction sub-paths fill as their union under the nonzero winding rule — no `Path.op` needed). Clamps keep a short card from inverting the rect (`neckTop ≥ 0`, `bottom = maxOf(neckBottom, neckTop + 1f)`).

## 2. THE FROSTED TEXT (EpisodeCinemaCard)

The plate + veil died. The frosted number now renders as **TWO stacked copies of the same glyphs** inside a plain Box at the chosen corner:

- **The halo copy** (behind): `graphicsLayer { alpha = 0.45f; if (API >= 31) renderEffect = BlurEffect(14f, 14f) }` — a REAL RenderEffect blur on Android 12+; below S the renderEffect is a no-op and the low-alpha under-copy alone reads as the soft double-exposure frost (minSdk 24 must not crash or look broken).
- **The crisp copy** (on top, `Alignment.Center`): `primary.copy(alpha = 0.58f)` + the soft dark shadow (0.45, blur 14, offset 1,1) for legibility on bright imagery.

The imagery shows THROUGH the glyphs — glass, not sticker. No plate, no veil, no border.

## 3. THE HEADING = ONE BUTTON (CollapsingHeader)

The outer Row's `start = 16.dp` padding moved INTO the branches (`end`/top/bottom stay):

- **onBack != null:** ONE `Row.weight(1f).clickable(onClick = onBack, role = Role.Button)` = `[2dp start padding → 20dp arrow box (18dp glyph) → 4dp Spacer → title (weight 1f, ellipsis)]`. A single ripple, a single tap target — arrow or title, same destination. The arrow hugs the screen edge (2dp — INSIDE the 16dp content padding), the gap tightens to 4dp, and the title lands ~26dp from the edge (the D-558 box pushed it to ~54dp).
- **onBack == null:** the title keeps the EXACT original geometry (16dp start) — Browse/More are pixel-identical to pre-D-558.

## 4. MY PROFILE STOPS TRUNCATING (ProfileScreen)

ROOT CAUSE (from the code + the user's "shows properly after scrolling"): the header's mini tab pill is `width(120.dp)` with `graphicsLayer { alpha = scrollFraction() }` — **alpha 0 still occupies 120dp of layout width**. At the top (32sp ExtraBold) "My Profile" had `screen − 32 − 38(arrow) − 164(pill+spacer+gear)` ≈ 126dp left → ellipsis to "My PR…"; collapsed (24sp) fits → "shows properly after scrolling". FIX: the pill's WIDTH now animates `0 ↔ 120dp` via `animateFloatAsState(if (collapsed) 1f else 0f, tween(300, FastOutSlowInEasing))` — the same curve as the header's own animation — while the alpha stays scroll-driven (the fade-in feel is untouched). The gear + spacer (44dp) remain: the title gets ~246dp back.

## 5. THE SEARCH BATCH

- **The bar's height is INVARIANT:** the clear affordance was an `IconButton` — a 48dp minimum touch target that appeared only when typing and grew the whole bar. Now a 24dp clickable Box with the 18dp Close glyph; the row's metrics are identical empty or full.
- **Fresh entry, empty bar:** `query` was `rememberSaveable` — and the AppRoot's `SaveableStateProvider` keys are never removed, so the query AND its results resurrected on every pop + re-enter. Plain `remember` now: a screen left is a screen forgotten (process-death typing preservation is the deliberate trade — the user's fresh-entry rule wins).
- **Back clears first:** `BackHandler(enabled = query.isNotBlank()) { query = ""; focusManager.clearFocus() }` — the device back gesture clears the search while text is present; with the query empty the handler disables itself and the AppRoot's BackHandler pops as usual.
- **The results card:** ONE grouped `Surface` (rounded 18dp, surfaceVariant 0.35) with hairline `HorizontalDivider`s (64dp indent), and every row redesigned: a 38dp **icon tile** (primary 0.12 tint) carrying the destination's OWN glyph — a 24-branch `searchIconFor(page)` map over `SettingsSearchPage` (Palette → Appearance, PlayCircle → Player, BugReport → Debug …) — a 15sp SemiBold title, the 12sp breadcrumb, and a quiet trailing ChevronRight.
- **The engine:** `W_TITLE_PHRASE = 90` / `W_KEYWORD_PHRASE = 70`; the full normalized query is matched as ONE phrase on multi-token queries. The phrase is **bonus-only** — the review agent PROVED the originally-written "phrase rescue" was dead code (every token of a phrase present in a field is that field's substring, so the per-token pass already matches; and a rescue would have double-counted with the bonus) — it was removed pre-push. New synonyms: `episode → [ep, episodes, chapter]`, `thumbnail(s) → [thumbnails/thumbnail, poster, image, cover, art]`.
- **The index:** SEVEN new poster-row entries — `poster.layout` (Layout), `poster.artwork` (Artwork), `poster.title` (Episode title), `poster.thumbnail` (**Episode thumbnail**), `poster.badges` (SUB / DUB badges), `poster.branding` (ANI-KUTA branding), `poster.master` (Poster) — each with its anchor; `episodelist.page` + `notifications.poster` keywords enriched with the thumbnail/poster/image/art family. Hand-traced ranking for "episode thumbnail": **Episode thumbnail (the row) 250 → Episode list 220 → Notification poster 200** — exactly what the user expected to find.
- **The poster screen takes its half of the contract** (a D-558 gap): `NotificationPosterSettingsScreen` gains `highlightAnchor`, the `rememberSettingsAnchorScroll` map (page/layout → item 0, artwork → 1, the five element rows → item 2), and every row wrapped in `SettingsHighlightTarget`; MainActivity's `NotificationPosterKey` branch passes `takeAnchor(NOTIFICATION_POSTER)`.

## Verification

- Independent review agent (Task 71-13): **VERDICT: SHIP, zero blockers** — every new symbol verified against the real sources (icons exist in material-icons-extended 1.7.8; `BlurEffect` inside `GraphicsLayerScope`; `Role` semantics overload; `animateFloatAsState(label=)`; `HorizontalDivider`; the exhaustive 24-branch `when`; anchor ids 1:1 across index/map/targets; neck geometry cannot invert). Advisories: the dead rescue (FIXED pre-push), the pre-existing synonym looseness (benign), the poster-off pulse note (design trade), the 24dp clear target (deliberate height trade).
- Brace balance machine-verified on all 8 touched files (Kotlin nested-comment-aware checker).
- Untouched BY DESIGN: the collapse machinery (EpisodeListSettingsScreen untouched this round), SwipeToToggleWatched, the download pipeline, the resolve flow, the prefs keys, the sheets, SegmentedToggle.

## CI ledger

- Implementation run: **35765045924** on 67845eaa — GREEN on the FIRST run.
- Release run: see `62-RELEASE-1.1.33-BRANCH-POINT.md`.
