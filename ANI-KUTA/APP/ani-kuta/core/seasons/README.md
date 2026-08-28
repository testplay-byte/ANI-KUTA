# :core:seasons — Season Management Engine

D-312 · Promoted from the old `:core:common` `SeasonDetector` (D-307) into its
own Gradle module per the user's requirement: *"a separate module which
processes things separately and allows me to easily and properly configure
things so that I can easily change it and make it adapt to various extensions,
various formats."*

## What it does

Turns raw extension episode names (+ optional provider metadata) into season
structure:

| API | Purpose |
|---|---|
| `SeasonDetector.parseSeasonTag(name)` | Parse ONE episode name → `SeasonTag` (season, episode-in-season, clean title, pattern id) or `null`. |
| `SeasonDetector.analyze(names, hints?)` | Whole-list analysis → `SeasonAnalysis` (per-episode assignments, season inventory, confidence, activation decision). |
| `SeasonDetector.detectSeasons(names)` | Convenience: distinct name-tagged season numbers. |

## Supported name formats (v2)

| Pattern id | Matches | Example |
|---|---|---|
| `season-episode` | `Season N <sep> Episode M [sep] Title` (optional parens, flexible separators `- – — : .`, case-insensitive) | `( Season 5 - Episode 12 - The Black Cat )` |
| `compact` | `S5E12`, `S5 EP 12`, `S5 Episode 12` (+ optional parens/separator) | `S5E12 - The Black Cat` |
| `season-only` | `Season N` with no episode number | `( Season 5 ) The Black Cat` |

Patterns are tried **in order — first match wins** (most specific first).

## Provider-hint fusion

`analyze()` accepts an optional parallel list of `ProviderSeasonHint`
(AniZip/Kitsu `seasonNumber` / `episodeNumberInSeason`, keyed by episode
number). Fusion rules:

1. An explicit **name tag always wins** — the extension says so itself.
2. Otherwise a hint with `seasonNumber > 0` assigns the season.
3. Otherwise the episode stays unassigned (the UI's "Other" bucket).

Activation (`isMultiSeason`): **≥2 distinct seasons AND ≥50% coverage** — a
couple of mislabeled episodes in a 1000-episode list must not hijack the list
into season mode. The user's "Organize episodes by" settings toggle is the
manual override on top of this.

## Adding a new format (the whole point of this module)

1. **Verify against real data** — pull episode names from the episode-list
   dumper logs (`adb logcat -s Anikuta:EpisodeDump`, or the debug console with
   an `EpisodeDump` tag filter). Do not invent examples.
2. Add a `SeasonPattern` to `SeasonPatterns.DEFAULT` (specific above loose),
   or `SeasonPatterns.register(...)` for runtime/per-extension configuration.
3. Nothing else changes — consumers only ever see `SeasonTag`s.

## Consumers

- `:core:common` → `EpisodeTitleParser.parseTitle` (clean titles for tagged names)
- `:feature:anime-details:impl` → `EpisodeListProcessor.analyzeEpisodeSeasons`
  (one pass: season buckets + per-episode assignments + per-season display
  numbers, with provider hints wired in `DetailsScreen`; feeds the season
  selector, the per-season slice tags, and the multi-season-only "S-3/E-5"
  compound tag — D-324: that tag never renders for no-season / single-season
  lists)
- Episode-list dump logs carry `patternId` so format coverage is verifiable
  from device logs

## Rules

- **Zero dependencies** (pure Kotlin) — keep it that way; this module must stay
  trivially portable and testable.
- No Android imports, no Compose, no coroutines.
