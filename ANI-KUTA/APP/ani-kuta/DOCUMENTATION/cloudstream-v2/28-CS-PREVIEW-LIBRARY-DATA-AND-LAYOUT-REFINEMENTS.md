# 28 — CS: THE PREVIEW SHOWS YOUR LIBRARY + THE LAYOUT REFINEMENTS (round 68 / D-556)

Round 68 implements the v1.1.29 device round's verdict on D-555. The user tested
v1.1.29 end to end and returned a split verdict: the qualities/servers sheet is
"proper, clean, exactly like how I wanted" (asked for 0.65 → **0.67**, "maybe 66
or 67. Yes, I think 67 would be a great option"), the four layouts "are good,
unique, and different, and they do get applied to the actual details page
properly" — and then a detailed punch list on the preview, the selector, and
each layout. Everything below shipped in ONE commit on the mainline feature
branch (`12f31929`), CI run 35724365577 **GREEN on the FIRST run**.

## THE HEADLINE: the preview-image root cause was NEVER the scheme

The D-555 commit confidently "fixed" the D-554 empty thumbnails by prefixing the
base64 payload with `data:image/jpeg;base64,` — and the v1.1.29 device round
proved it STILL rendered nothing. This round's diagnosis is definitive: the
base64 constants are valid JPEGs (decoded + magic-byte verified in the sandbox),
and **Coil 3.0.4 has NO DataUriFetcher** — proven by unzipping
`coil-core-android-3.0.4.aar` and listing `coil3/fetch/*`: AssetUriFetcher,
BitmapFetcher, ByteArrayFetcher, ByteBufferFetcher, ContentUriFetcher,
DrawableFetcher, FileUriFetcher, JarFileFetcher, ResourceUriFetcher. That's all.
A `data:` URI has no fetcher to match in Coil 3 (it was a Coil 2 feature), so
`AsyncImage` silently drew nothing — D-554's bare payload AND D-555's scheme'd
payload were both dead on arrival, and the user kept seeing the null-thumbnail
number discs.

**The fix removes the data-URI layer entirely**: the two demo stills ship as
real JPEG drawable resources (`app/src/main/res/drawable-nodpi/
ep_preview_still_1/2.jpg`, 640×366 q82, generated this round) referenced through
`android.resource://<pkg>/<resId>` URIs — `ResourceUriFetcher`'s bread and
butter, offline, ~107KB total, and they render through the SAME `AsyncImage`
path the network thumbnails use. The two ~39KB base64 source constants died
with the round.

## D-556-A — the sheets: 0.65 → 0.67

The user's exact number. `CsLinksSheet` + `CsResolveSheet` (the D-554-A parity
pair) move to **0.67**; `CsEpisodesSheet` stays explicitly 0.70; the app-wide
default stays 0.70. History: 0.70 → 0.60 → 0.65 → 0.67.

## D-556-B — the live preview shows YOUR library

The user: "I was hoping for the live previews to show actual live previews from
my library … it will pick any random series … should have its episode list
loaded … with images … proper titles, proper descriptions, and other details
like the available audio versions marked on them, the release date marked on
them. If none of that is available … demo data."

`EpisodeListSettingsScreen` now loads REAL content on entry
(`loadLibraryPreviewItems`, `Dispatchers.IO`, zero network):

1. `DataCacheRepository.getAllEpisodeAudioAggregates()` — ONE batch query; a
   series qualifies only when its episode list is actually LOADED
   (`releasedCount >= 2` — the data-cache table is exactly that evidence).
2. `ContentRepository.getAllLibraryItems()` (newest first, distinct mainIds) →
   the qualifying set is **shuffled** ("it will pick any random series" — every
   visit can meet a different series).
3. Up to 8 candidates probed via `getEpisodeMetadata(mainId)`; the first whose
   two picked episodes carry real imagery (episode stills or the series cover
   `dataCoverUrl ?: extThumbnailUrl`) wins; a no-imagery candidate is the last
   resort over demo data.
4. `pickPreviewEpisodes` takes the lowest-numbered episodes WITH thumbnails
   (thumbnails are the preview's point); the reconstruction mirrors
   `DetailsViewModel.fetchEpisodes`' cache-restore path FIELD FOR FIELD
   (SEpisode url/number/name/date/scanlator/summary/preview_url + the full
   D-190 `EpisodeMetadata`), so the preview rows are byte-for-byte what the
   details screen would draw.
5. Audio pills: the cached `scanlator` when present; otherwise the app's OWN
   per-series aggregates speak through an honest token hint (`audioScanlatorHint`
   → "SUB DUB" / "SUB" / "DUB" / "HSUB") riding the same
   `parseAudioAvailability` path — nothing fabricated.
6. Nothing qualifies (fresh install) → the demo samples render: two episodes
   with real dates, full synopses, the SUB/DUB/HSUB vocabulary, and the new
   drawable-resource stills.

## D-556-C — the preview is INTERACTIVE

- **Watched**: slot 1 boots fresh (40% progress bar), slot 2 boots watched (the
  dim/grayscale treatment) — the default experience the user described — and a
  swipe (long-press on GRID) flips the state via the shared
  `SwipeToToggleWatched` / GRID long-press, through a local
  `watchedOverride` map keyed by episode URL. "I am able to swipe right or
  left on the live previews, but apparently their state never changes … I do
  want the option to manually change it" — fixed.
- **Download**: every tap of the control/badge advances a state machine across
  ALL EIGHT `EpisodeDownloadState` variants in demonstration order —
  NotDownloaded → Resolving → Queued → Downloading(35) → Paused →
  Downloading(72) → Error → Retrying → Downloaded → wraps. "It should cycle
  between all the possible states of the download options" — delivered.
- The rows stay inert for playback (settings is not a player).

## D-556-D — the scroll collapse (the second episode stays)

"Only this much that the first episode list is hidden, and the second one will
remain there and will always be shown properly. When the user scrolls to the
very top again, then both of them will start to show up again."

A custom layout modifier reports `contentHeight − collapse × (row1Height +
gap)` and places the content shifted up under a `clipToBounds` — the collapse
fraction tracks the options list's own scroll (0 at the very top; 1 after a
200dp ramp; past the first item or at list-end → 1 so a short options list
still completes the collapse; a list that fits entirely NEVER collapses on
entry). GRID is exempt ("not for the grid layout because it is already
compressed enough"). The first row's height is measured with `onSizeChanged`,
so the shift is exact per layout.

## D-556-E — the selector: descriptions die, the pill slides

- The "Four completely different designs" line and the per-option identity line
  (`layoutIdentityLine`) are GONE — the live preview IS the description.
- `SegmentedToggle` (the shared control — 8 call sites) now animates its
  selection: the primary pill is ONE rounded rect drawn behind the segments
  (`drawBehind`, height = the row's own measured height, width derived from the
  real segment geometry via `BoxWithConstraints`), sliding on a slightly
  under-damped spring; label colors crossfade. "If I move from the cinema
  layout to the timeline layout, then the selection should move smoothly" —
  delivered.
- The D-555 bottom "More" pointer card is DELETED ("most definitely not
  needed").

## D-556-F — the layouts get their refinements

- **CINEMA**: the huge white-alpha ghost number is replaced by a **themed EP
  badge** — the details page's own accent (`MaterialTheme.colorScheme.primary`
  surface under the per-anime theme) with `EP` + the number in `onPrimary`,
  floating top-end with a soft shadow, readable over any imagery ("the episode
  numbers should actually be in the theme color of the details page"). The
  ghost number survives exactly one place: the no-thumbnail placeholder (there
  it is the plate, not an overlay fighting an image — and the unit lock on
  `ghostEpisodeNumber` stays honest).
- **TIMELINE**: the date node's offset STAYS (the user explicitly wanted it
  incorporated, not corrected, and NOT centered). A **blob merge** now wraps
  the node + date: a same-color-as-the-card union of a soft left capsule
  (asymmetric corners) and a narrower neck sliding UNDER the card's left edge
  — drawn between the spine and the node/label children, with the card drawn
  after, so the junction is seamless (same fill, no border) and the date
  visually lives inside the card's material. "A blob kind of effect which
  merges that section, that circle, that area with the right side smoothly,
  like a proper abstract style."
- **GRID**: RECREATED ("try to recreate the whole grid view again … make it
  much better and much more proper"). Still a two-column wall — that is its
  identity — but the cell is rebuilt: title burned OVER the image on a bottom
  scrim (the wall reads as posters), themed EP pill, ringed watched check
  (the bare centered check read as noise), 4dp progress bar, hairline border,
  larger radius, and the meta line becomes the capsule chips (horizontally
  scrollable when four capsules outgrow a half-width cell).
- **TAGS** (CLASSIC + GRID): the flat outlineVariant surfaces with
  dot-separated labels die. New `EpisodeDateChip` / `EpisodeAudioChip`
  capsules: quiet surface date capsule; SUB = primary-tinted, DUB =
  tertiary-tinted, HSUB = outline-tinted; 10sp SemiBold with letter-spacing.
  The `pillsRowVisible` visibility algebra is untouched (unit locks hold).

## The lessons

1. **[MISTAKE, round-defining] A data URI without a fetcher is just a string** —
   the D-555 "fix" traded one empty-image bug for the same empty-image bug
   because the scheme was checked against a fetcher registry that has no
   `data:` entry. WHEN AN IMAGE LOAD FAILS SILENTLY, ENUMERATE THE FETCHERS
   (or use a path with a guaranteed fetcher — bundled resources).
2. **[INSIGHT] Confidence about a fix is not evidence of it** — the D-555 commit
   message described the data-URI mechanism as "PROPER" and shipped; one
   `unzip -l` on the Coil AAR would have falsified it in seconds. Verify
   library capabilities against the actual artifact, not the migration notes.

## Untouched BY DESIGN

The prefs keys, the CLASSIC row's structure (only its chips changed), the swipe
algebra, the download pipeline, the resolve flow, the season/organize systems,
the details-page list structure (the outer LazyColumn remains the virtualizer),
`CsEpisodesSheet` (0.70), and the app-wide sheet default (0.70).

## The device-round checklist (on v1.1.30)

1. The CS qualities/servers sheet → 67% (slightly taller than v1.1.29's 65%).
2. Settings → Appearance → "Episode list" → the preview shows TWO episodes from
   a REAL library series (thumbnails, titles, descriptions, dates, audio
   pills); no library data → the two demo stills actually VISIBLE this time.
3. Swipe episode 1 → watched treatment appears; swipe back → fresh. Long-press
   on GRID cells toggles. Tap the download button repeatedly → the state walks
   the full cycle (spinner → 35% → pause → 72% → error → retry → downloaded).
4. Scroll the options down → the preview's first episode slides out, the second
   stays pinned; scroll back → both return. GRID never collapses.
5. Flip the four layouts → the preview glides to its new size; the selector's
   pill slides with a spring; no description text anywhere.
6. CINEMA: the themed EP badge top-end (no ghost number). TIMELINE: the date
   node melts into the card. GRID: titles on the image, capsule chips below.
   CLASSIC: capsule chips on the rows.
7. Regression sweep (the details-page list in all four layouts, the CS sheets,
   downloads, resolve flow).
