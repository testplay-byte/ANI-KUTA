# release/1.1.16 — the branch point (D-517, round 53)

The fourteenth release branch cut from a FEATURE branch (the standing model;
main still carries NONE of this — the merge awaits the user's confirmation):

- **Cut from:** the `feature/ads-return-pill` head `3cb5adcc` (CI green —
  run 35470221593; the round-53 implementation needed one CI round — the
  D-513 signature change made the public `NotificationPosterSettingsScreen`
  expose the D-503-internal `PreviewSelection` through its
  `onOpenCustomize` lambda parameter, and Kotlin rejects a public parameter
  type argument — the documented one-fix-push pattern of rounds 50–52).
- **This branch = that head + ONE commit** (the version bump to
  `1.1.16` / `10116` + this record).

## Process note (D-498 — the standing release-first rule, applied)

The release IS the verification build: cut + tag immediately after the
implementation CI is green; the user's device round happens ON the release
APK via the in-app updater. The ≤2-runs budget (D-472) maps: run 1 = the
implementation push (35469866699 → failed on the single visibility error,
fixed and green as 35470221593), run 2 = this tag's Release APK run.

## What the release carries (the round-53 set on top of v1.1.15)

The v1.1.15 device round APPROVED the studio ("exactly how I hoped it would
be: proper, looking good, and clean") and the working heads-up popup, and
asked for the smart darkening back, real button feel, the slider below the
preview, non-sticky drags, much richer text/tag options, long-title
ellipsis handling, both-tags-in-the-studio, the same preview content in the
studio, a proper back button — and the notification title back. D-508..D-516
(full detail: `AGENT-CONTEXT/memory/decisions.md`, the round-53 records):

- **D-508 THE STUDIO DRAG REBUILT (the round's headline):** the D-503 loop
  accumulated per-event deltas THROUGH the element's snapped origin — the
  snap wrote the magnet position back every frame, so any movement smaller
  than the 14px window was swallowed forever ("it would get stuck
  somewhere occasionally … if I slowly moved my finger down for a while, it
  would not do anything"). The new model is ABSOLUTE: `moveElementTo` gets
  the raw target computed from the SEGMENT anchor (the element's origin +
  the pointer position captured once at gesture down / pinch start / the
  pinch→drag handoff), so the snapped value never feeds the accumulation —
  slow precise drags glide 1:1 with the finger and the magnet only pulls
  while the target is genuinely close to a snap line. The clamp bounds now
  MIRROR the composer's (texts x ≤ W−120 / y ≤ H−lineHeight, chips and
  cards clamp to their measured size) so the studio can never place an
  anchor the real banner would re-clamp; the old `coerceIn(0f, negative)`
  crash edge for canvas-wider rects is fixed with `coerceAtLeast(0f)`.
- **D-509 THE SLIDER BELOW THE PREVIEW:** the size slider moved from the
  left panel's bottom to the RIGHT pane, directly under the editable
  preview ("the size adjustment bar was supposed to be shown below the
  preview … but it was not"), still bound to the selected element with the
  live % readout.
- **D-510 THE REAL-BUTTON HEADER:** the studio's Back / Reset / Save are
  FilledTonal / Outlined / Filled-primary circles (the bare IconButtons
  "not proper" per the round), and on the settings screen the Customize +
  Shuffle preview actions became an OutlinedButton + a filled Button with
  Material paddings and icon spacing ("an actual button kind of feel").
- **D-511 THE RICH OPTIONS:** `PosterElementLayout` grew six
  backward-compatible fields (defaults reproduce the factory look; old
  saved JSON parses untouched): `fontKey` (sans/condensed/serif/mono),
  `bold: Boolean?` (null = the element's factory weight), `italic`,
  `shadow` (the D-499 soft shadow becomes a toggle), `chipBgArgb` (a tag's
  background color), `labelOverride` (custom tag text; a comma on the audio
  row renders SEVERAL tags — "Subbed, Dubbed"). The sidebar's new OPTIONS
  section exposes them per selected element; the composer's ABSOLUTE mode
  applies every field through the ONE shared `PosterDrawing.typefaceFor`
  resolver (WYSIWYG holds — the flow mode keeps the factory look and gains
  the title visibility gate).
- **D-512 THE HARD PER-LINE ELLIPSIS:** `wrappedLines` now ellipsizes ANY
  line wider than its column (a single long word previously overflowed) —
  long titles truncate to the space available ("only show the first half of
  the title and then show the dots"), never overlap the margins, down to
  one line or one word + "…".
- **D-513 THE DESIGN CANVAS + THE CARRIED SELECTION:** the studio renders
  BOTH SUB and DUB tags regardless of the sample's real availability (the
  real banner stays truthful), and opens on the settings screen's EXACT
  preview selection — `PosterCustomizeKey` became a
  @Serializable data class carrying mainId/title/episodeNumber/variant, and
  the Customize tap hands over the live selection (no more "when I go to
  the customized one, it picks another one").
- **D-514 THE SMART ADAPTIVE SCRIM:** the darkening returns — SMARTER. The
  background's average luminance (a 24×10 downsample, Rec. 709 weights)
  drives a smooth black veil: ≤0.40 luminance → no scrim at all, 0.60 →
  ≈15%, full white → 46% ceiling ("darkened a little bit … if it is already
  dark then it will not be darkened but if it is lighter then it will be
  made darker"). The SAME helper runs on the composer AND the studio canvas;
  every failure degrades silently to "no scrim".
- **D-515 THE BUTTON FEEL:** see D-510 (the settings-screen half).
- **D-516 THE TITLE BACK ON THE BANNER:** the D-503 empty-title experiment
  ends — the device round found the heads-up card showing nothing but the
  app header ("it was not expanded. I did not see the cover banner image
  itself; it only showed me the notification"). Both banner post paths now
  carry the one-line content title (`headline`, 40 chars + "…",
  surrogate-safe) and the short description "New episode available for "
  + the first two words (`shortDescription`) — the collapsed heads-up card
  is informative again, and expanding still shows the banner under the
  title (the BigPictureStyle stays clean: no bigContentTitle/summaryText,
  so no three-stack).

## Verification

- Implementation: Build APK run **35470221593** GREEN on the fix push
  `3cb5adcc` (the failed first run 35469866699 diagnosed from its logs:
  the single visibility error above).
- Release: the `v1.1.16` tag's Release APK run (see the tag + the release
  on GitHub) — arm64-v8a + the ABI splits per the workflow's gate, published
  stable + `--latest` with `SHA256SUMS.txt`.

## Device-round checklist (on THIS release APK, via the in-app updater)

1. The studio drag: move any element SLOWLY — it must glide with the finger
   and only stick (magnetically) right at a snap line, escaping easily.
2. The size slider sits directly BELOW the preview and drives the selected
   element.
3. The studio header: Back (tonal), Reset (outlined), Save (filled primary)
   read as real buttons; on the settings screen Customize (outlined) and
   Shuffle preview (filled) do too.
4. Select the content title → OPTIONS: switch the font family (Serif/Mono…),
   toggle Bold/Italic/Soft shadow; select a tag → change its background
   color, type a custom label ("Subbed, Dubbed"), toggle the label styles.
   Save and confirm the REAL notification renders the same.
5. A long title truncates with "…" inside its column — nothing paints past
   the banner edge.
6. The studio opens on the SAME content the settings preview was showing
   (shuffle, then Customize → same poster on the canvas), with BOTH tags.
7. The background art: a bright banner is gently darkened; an already-dark
   one stays untouched.
8. Send a test notification: the heads-up card now shows the content title
   + "New episode available for …"; expanding it shows the banner.
9. The saved layout still loads over v1.1.15 (old JSON untouched — the new
   styles default to the factory look).
