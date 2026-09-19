# release/1.1.15 — the branch point (D-507, round 52)

The thirteenth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `048f9422` (CI green —
  run 35465964501; the round-52 implementation needed one CI round — the
  studio imported the composer from `:core:notifications` while
  `EpisodeBannerComposer` lives in `:app`'s `com.confused.anikuta.notifications`
  package, and every `PosterEditorArt`/`loadEditorArt`/`resolveAudioVariant`
  reference in `PosterCustomizeScreen` collapsed from that one wrong import —
  the documented one-fix-push pattern of rounds 50/51).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.15` / `10115` + this record).

## Process note (D-498 — the standing release-first rule, applied)

The release IS the verification build: cut + tag immediately after the
implementation CI is green; the user's device round happens ON the release
APK via the in-app updater. The ≤2-runs budget (D-472) maps: run 1 = the
implementation push (35465599161 → failed on the one wrong import, fixed and
green as 35465964501), run 2 = this tag's Release APK run.

## What the release carries (the round-52 set on top of v1.1.14)

The v1.1.14 device round APPROVED the poster section overall ("looking
clean, beautiful, and well-built and the layout was proper") and asked for
the notification to BE the banner, real device popups, both audio badges,
fixed thumbnails, no darkening, an animation on shuffle, a 30-second test
stagger — and the POSTER STUDIO. D-503..D-506 (full detail:
`AGENT-CONTEXT/memory/decisions.md`, the round-52 records):

- **D-503 THE CLEAN BANNER:** when a banner composes, it IS the
  notification — the BigPictureStyle sets only `bigPicture`/`bigLargeIcon`
  (no `setBigContentTitle`/`setSummaryText`) and the builder's title/text
  collapse to empty on BOTH post paths; the expanded notification shows the
  poster and nothing above it. A failed compose keeps the old informative
  text card (the fallback is untouched). THE REAL HEADS-UP POPUP: the
  channel moved to `anikuta_new_episodes_high` at `IMPORTANCE_HIGH` — the
  original `anikuta_new_episodes` was created at IMPORTANCE_DEFAULT, which
  never produces the Android heads-up banner (the device round: "it does
  not show me the notification as a device popup"); channels are immutable
  after creation, so `ensureChannel` creates the HIGH channel and DELETES
  the legacy one (no duplicate rows in the settings app); the non-silent
  builders moved to `PRIORITY_HIGH`. THE IN-APP OVERLAY RETIRED: the
  user's verdict — "the banner shows at the top of the app itself, which is
  kind of not what I wanted. I wanted the banner to be shown as a
  notification" — so D-500's `InAppBannerController`/`InAppBannerHost` are
  DELETED with their wiring and the AppRoot host call. THE STAGGER: the
  second test notification arrives 30 SECONDS after the first (was 5
  minutes), still via WorkManager so it survives app death.
- **D-504 THE CHIP TRUTH WIDENED + THE THUMB CHAIN:** the device round kept
  seeing a single chip on dual-variant content — the feed union alone is
  thinner than reality (a sub release posts before any dub row exists;
  'initial' batches record one variant). `resolveAudioVariant` now unions a
  THIRD layer: the EPISODE CACHE's own row parsed with `parseAudioAvailability`
  (the exact parser the details page renders its per-episode SUB/DUB pills
  from — the evidence the user cross-checks against). Both available → BOTH
  chips; one → one; genuine nothing → no chip (still honest). THE
  THUMBNAIL CHAIN: the episode's own still loads first (with ONE retry at
  8s for the transient CDN silence behind "available but not shown"), then
  the extras' large cover → the cover → the banner (6s each) — the left
  card stays on stage for rows whose `thumbnail_url` is null; an empty
  chain still composes WITHOUT the card. NO DARKENING: the D-499
  right-column + bottom scrims are GONE — the art renders at full
  brightness ("the banner image is apparently getting a darkening kind of
  effect applied to it … it should not"); the D-499 text shadows are the
  readability carrier.
- **D-505 THE POSTER STUDIO:** the user's headline feature. The CUSTOMIZE
  button (left of the shuffle preview button) opens a forced-landscape
  two-pane editor: the LEFT panel (~32%) carries the five elements (content
  title / episode number / SUB-DUB badges / episode title / thumbnail card)
  with per-element visibility switches (pref-synced where a pref exists), a
  12-swatch color palette + the "A" default chip, a per-element style
  reset — and the SIZE SLIDER pinned at the very bottom. The RIGHT pane is
  the editable live preview at the composer's true 2.56:1 proportions,
  rendered through the SHARED `PosterDrawing` primitives (extracted from
  the composer, together with `PosterCanvasMetrics` — the studio is a
  faithful miniature, not a lookalike). INTERACTIONS: one-finger drag
  anywhere, two-finger pinch resize (0.4–2.5×), topmost-first hit-testing,
  touch-to-select, and MAGNETIC SNAPPING — a fine 8px grid plus a 14px
  snap window onto the canvas margins/centers, the thumb-box lines, and
  the other elements' center lines, with lime guide lines flashing on the
  snapped axis. One deliberately KEYLESS `pointerInput(Unit)` runs the
  whole gesture (a state key would restart the detector mid-drag); the
  pinch→drag handoff re-anchors the previous position to the remaining
  pointer so the element never jumps. FLOW SEEDING: while
  `customized=false` the studio renders the v1.1.14 flow layout; the FIRST
  edit seeds every untouched element at its current flow anchor (the
  poster never jumps under the finger) and flips the composer into
  ABSOLUTE mode. SAVE: persists the `PosterLayoutConfig` JSON
  (`notif_poster_layout_json`) + dumps the FULL JSON to the console log
  (`Anikuta:App:PosterStudio`, "POSTER LAYOUT SAVED: …") — the user's
  explicit tooling request, so their layout can be handed back and made
  the shipped default — + a toast; Reset restores the factory flow layout.
  Persistence: `PosterLayoutConfig` (@Serializable per-element
  x/y/scale/colorArgb/visible + the customized gate) in
  `NotificationPreferences.posterLayoutJson`; the composer parses it
  leniently (a garbled save degrades to DEFAULT, never to a broken banner).
  Supporting seams: `EpisodeBannerComposer.loadEditorArt` (the exact art
  loads minus the compose) and `resolveAudioVariant` made public (the
  studio resolves chips through the same resolver).
- **D-506 THE SHUFFLE FEEDBACK:** "I should be given an animation so that I
  know that the shuffling did happen" — every shuffle tap pulses the
  preview box (spring), spins the shuffle icon a full 360°, and the
  recomposed banner CROSSFADES in when the compose lands (which also
  smooths toggle-flip re-renders). D-494's semantics untouched: toggle
  flips still never re-select.

## Verification

- Implementation: Build APK run **35465964501** GREEN on the fix push
  `048f9422` (the failed first run 35465599161 diagnosed from its logs:
  the single wrong import above).
- Release: the `v1.1.15` tag's Release APK run (see the tag + the release
  on GitHub) — arm64-v8a + the ABI splits per the workflow's gate, published
  stable + `--latest` with `SHA256SUMS.txt`.

## Device-round checklist (on THIS release APK, via the in-app updater)

1. A dual-variant episode shows BOTH chips (check an entry whose latest
   episode has a dub).
2. The thumbnail card shows on entries that previously dropped it.
3. The background art is FULL brightness (no darkening).
4. The expanded notification shows ONLY the banner — no title/text above it.
5. Sending a test notification while in the app pops the REAL Android
   heads-up banner (and no in-app overlay); the second test arrives 30
   seconds later.
6. Customize opens the landscape studio — drag/pinch the five elements,
   feel the magnetic snap + lime guides, pick colors, then Save and
   re-check the preview; the console log carries the full saved JSON.
7. Shuffle plays the pulse + spin + crossfade.
8. Airplane-mode: the preview still renders (offline-first unchanged).
