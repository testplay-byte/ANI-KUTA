# 23 — CS LINK FORMATTING: ONE SERVER, EVERY AUDIO VERSION, EVERY RESOLUTION

**Round 63 · D-551 · the v1.1.24 device round's verdict + its two demands.**

## 1. What the device round validated (DO NOT TOUCH)

The user's v1.1.24 verdict is the D-548/D-550 chain CONFIRMED working:

> "It loaded and it started to play properly on my mobile device with audio, properly and without any problems. … The audio folder was properly created too and the episodes folder was there too. … I checked out the data.json file and it was looking proper too, well formatted, and well handled. Good work with that. I am quite satisfied with the overall results."

Offline playback, the `episodes/` + `audio/` + `.dashmeta` layout, and data.json are all
LOCKED. This round touches none of that machinery — it fixes how links are NAMED and
GROUPED, and what the sheets DISPLAY about them.

## 2. The new demands (the MovieBox dual-audio episode)

The test episode resolves to TWO DASH links — one per AUDIO VERSION (each variant is its
own manifest, the aoneroom shape D-550 documented):

```
link #1: MovieBox (Hindi Audio) 1080p      type=DASH url=…/348982352472932496_1_4_1080_h265_231/index.mpd
link #2: MovieBox (Original Audio) 720p    type=DASH url=…/8750154580291377568_1_4_720_h265/index.mpd
```

Two symptoms, ONE root cause:

1. **Formatted mode showed TWO server options.** The user: "It should not show two
   options when formatted source is turned on. Instead it should show one option and in
   that option it should show the audio version options as Hindi and original."
2. **Only ONE audio version downloaded.** The user: "Apparently only one of its audio
   versions was downloaded. The issue, I feel, is that it was not managed properly." —
   D-550's sibling-audio pipeline (offline audio switching) never engaged.

### The root cause

The app's link-name vocabulary did not understand LANGUAGE-AUDIO decorations:

- `CsAudioTag.parse("MovieBox (Hindi Audio)")` → word pass (sub/dub/…) no, bracket pass
  (sub/dub vocabulary) no → **"Default"** for BOTH links.
- `serverNameOf` kept the bracket glued to the server → **"MovieBox (Hindi Audio)"** and
  **"MovieBox (Original Audio)"** were TWO servers (symptom 1).
- `CsDownloadRequestBuilder.siblingAudioVariants` matched siblings with
  `candidate.name == link.name` (RAW name equality) → the two names differ → **no
  siblings collected** → DashDownloader received zero AUDIO_VARIANT tracks (symptom 2).
  D-550's machinery was correct all along — it was simply never handed the variants.

## 3. The fix — one vocabulary, all consumers

### 3.1 The language-audio pass (CsAudioTag, :core:cs-player)

A new pass in `parse` (after the word pass, before the sub/dub decoration pass):

- **Bracketed**: `(<words> audio)` / `[<words> audio]` — "MovieBox (Hindi Audio)" →
  capture "Hindi" → label **"Hindi"**.
- **Whole-segment**: a full ` - ` segment that IS `<words> Audio` — "MovieBox - Hindi
  Audio - 1080p" → label **"Hindi"** (the separator regex mirrors `CsServerNames.
  SEPARATOR` — kept in sync by contract).
- **Normalization**: common abbreviations map to display names ("eng"→"English",
  "jap"→"Japanese", "orig"→"Original", ~35 entries); anything else is first-letter
  capitalized ("bhojpuri"→"Bhojpuri").
- **NOT a language** (stays "Default" / a decoration): the multi-audio family
  ("Multi Audio", "Mixed Audio" — the pre-D-551 behavior), the sub/dub families (the
  word pass already caught them), "audio"/"track"/"unknown"/"none".
- **No free-form matching**: "MovieBox Audio Server" stays Default (no brackets, no
  segment boundary → no match). A prose-length bracket ("(watch in the original audio
  track)") is too long for the ≤24-char capture → no match.

The regexes are PUBLIC (`LANGUAGE_AUDIO_BRACKET`, `LANGUAGE_AUDIO_SEGMENT`) — the
server-name derivation strips with the EXACT regexes the label parse recognizes (single
source of truth; a decoration can never land in both tiers).

### 3.2 The server-name derivation MOVED (CsServerNames, :core:cs-player)

`serverNameOf` moved out of :feature:cs-watch:impl into `CsServerNames.of(name)` because
a THIRD consumer needs the identical function:

| consumer | module | use |
| --- | --- | --- |
| the sheets' grouping (Server → AudioVersion → Quality) | :feature:cs-watch:impl | `groupServers` + the debug report (the module keeps a delegating `serverNameOf` — zero call-site churn) |
| the download enqueue's sibling matcher | :app | `siblingAudioVariants` |
| (future) anything that needs "which server is this link?" | any | `CsServerNames.of` |

`of()` strips the language-audio decorations (bracketed + whole-segment) on top of the
Task 57 vocabulary. "MovieBox (Hindi Audio)" → **"MovieBox"**.

### 3.3 The sibling matcher repaired (:app)

```kotlin
// before: candidate.name == link.name          (raw names — never equal)
val server = CsServerNames.of(link.name)       // "MovieBox"
… CsServerNames.of(candidate.name) == server … // matches the Original variant
```

The D-550 pipeline finally receives the sibling: DashDownloader fetches + plans the
"Original Audio" manifest, downloads its audio sets as extra groups, and the composed
sidecar manifest labels both sets ("Hindi" via `task.videoAudio`, "Original" via the
variant's lang) — the offline audio selector offers BOTH, exactly like the online one.

### 3.4 Every available resolution (the sheet probe)

The user: "If it is possible to show all the available video resolutions, then I would be
quite happy with it, like 1080p, 720p, and 480p, because for this one there were these
options available when the video started playing."

- `DashManifestHeights` (:core:common, NEW, pure) — parses an MPD's video-representation
  heights (audio sets skipped via contentType/ContentComponent/mimeType guards; distinct
  + descending; null on ANY failure). Same hardened DOM factory discipline as
  DashManifestPruner (the D-546 lesson: NEVER touch isXIncludeAware).
- `CsDashQualityProbe` (:feature:cs-watch:impl, NEW, impl-private) — the network half:
  fetches the manifest with the LINK'S OWN merged headers (the MovieBox manifest answers
  only with the extension's CloudFront-Policy cookie + UA — a generic fetch 403s),
  bounded (5s connect/read, 8s total, 2 MiB cap), silent by contract.
- `CsVideoLink.availableQualities: List<Int>?` (additive, default null) — the parsed
  heights ride the link.
- `CsResolveSheet` — after each resolver snapshot, one probe per not-yet-probed DASH URL
  in a SupervisorJob scope that dies with the sheet (dispose) or the retry (remember
  keys); results merge into a SnapshotStateMap; `pickableLinks` is a STABLE enriched
  view (`remember(links, probeQualities.size)`) — a per-recomposition new list would
  reset the accordion's expanded-server remember every frame.
- `CsServerCard` — a version whose single DASH link carries heights renders
  `Available: 1080p · 720p · 480p` under its chip (the same list the player's per-stream
  quality section shows). Presentation only: the chip stays the pick target — ABR serves
  every listed resolution from that one manifest.
- The enriched list rides `PreResolvedSeed` — the in-player links sheet inherits the
  lines for free.
- Deliberately DASH-only this round; the field is type-agnostic so an HLS master-playlist
  probe can ride the same field later.

## 4. What deliberately did NOT change

- The resolver's flow (snapshots, dedup, headers, logging) — byte-identical; the probe
  is presentation-layer and off the resolve path.
- RAW mode — one row per stream, raw labels (the user uses formatted mode; raw stays raw).
- The player, the downloaders, the queue, the scanner, the publish layout, data.json's
  schema — untouched. (`videoAudio` in data.json now records "Hindi"/"Original" instead
  of "Default" for language-versioned links — the same field, richer truth.)
- COMBINED sub/dub mode — explicit `audioTag`s still win over name parsing.
- The feature line stays 1.1.20/10120 (the D-430 discipline); the release line bumps.

## 5. Failure matrix (what the probe/sibling paths do when things go wrong)

| failure | behavior |
| --- | --- |
| probe transport error / non-200 / oversized | probe returns null → no "Available:" line → the row is exactly pre-D-551 |
| manifest parses to zero video heights | null → no line |
| sibling variant fetch/plan fails in DashDownloader | D-550's best-effort: skipped, the picked variant never pays |
| a provider names variants in an unknown pattern | language pass no-ops → "Default" labels → the pre-D-551 presentation (never worse) |
| sheet dismissed mid-probe | the probe scope is cancelled with the composition — nothing leaks |
