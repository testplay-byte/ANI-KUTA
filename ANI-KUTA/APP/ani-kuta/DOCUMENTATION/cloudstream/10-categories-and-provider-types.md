# 10. Categories & Provider Types — How CloudStream Categorizes Extensions and Content

> Research doc 10/10 (batch B2-e). Scope: the **categorization system** as a whole — the three
> metadata layers (repo manifest → provider declaration → per-response type), the app-side
> filtering/configuration UI that consumes them (Settings → Providers, home provider picker, search
> filter, extension browsing chips), NSFW gating, the language system, per-type behavior switches,
> genre browsing, auto-download modes — and what ANI-KUTA should replicate.
>
> The `TvType` **enum value table** (all 18 values + semantics) is already cataloged in
> `05-data-models.md` §6.1 — this doc does NOT re-catalog it. `03-mainapi-reference.md` §2.1 lists
> the provider-class fields; here we go deeper into who *consumes* them. Repo-index metadata
> (plugins.json field census) is in `04-extension-repositories.md` §3 — cited, not repeated.
>
> Sources (read-only): `research/cloudstream/` app + library, `research/storm-ext/`,
> `research/CakesTwix-ext/`, `research/phisher-builds/plugins.json`.
>
> Marker conventions: `[verified]` = read in source with line numbers; `[inferred]` = reasoned from
> code but not directly observed; `[docs]` = cited from a previous doc in this series;
> `[recommendation]` = design advice for ANI-KUTA (this doc's §8 only).

**File abbreviations** (all under `research/cloudstream/` unless noted):
`MA` = `library/.../MainAPI.kt` · `ACU` = `app/.../utils/AppContextUtils.kt` ·
`HF` = `app/.../ui/home/HomeFragment.kt` · `SF` = `app/.../ui/search/SearchFragment.kt` ·
`RVM2` = `app/.../ui/result/ResultViewModel2.kt` · `MainActivity` = `app/.../MainActivity.kt` ·
`PM` = `app/.../plugins/PluginManager.kt` · `RM` = `app/.../plugins/RepositoryManager.kt` ·
`PV`/`PF`/`PA` = `app/.../ui/settings/extensions/PluginsViewModel.kt` / `PluginsFragment.kt` /
`PluginAdapter.kt` · `DSH` = `app/.../utils/DataStoreHelper.kt` · `GP` =
`app/.../ui/player/GeneratorPlayer.kt` · `CS3IP` = `app/.../ui/player/CS3IPlayer.kt` ·
`SWM` = `app/.../services/SubscriptionWorkManager.kt` · `SubH` =
`library/.../utils/SubtitleHelper.kt` · `SP` = `app/.../ui/settings/SettingsProviders.kt` ·
`SU` = `app/.../ui/settings/SettingsUpdates.kt`.

---

## 0. The model in one paragraph

CloudStream categorizes along **three independent layers**. **Layer 1 (repo manifest)**: every
plugins.json entry carries `tvTypes: List<String>` (TvType enum *names* as strings) and
`language: String?` — this is *install-time/repo-browsing* metadata; it drives the extension-store
filter chips, NSFW visibility in the store, and the auto-download filters, but is **never compared
against the running provider**. **Layer 2 (provider runtime declaration)**: each `MainAPI` instance
declares `supportedTypes: Set<TvType>` (what the provider *offers*), `providerType`
(`MetaProvider` vs `DirectProvider`), `lang` (BCP-47-ish tag, default `"en"`), `vpnStatus`, and
`supportedSyncNames`; these drive *app-side provider filtering* — home/search provider lists,
preferred-media filtering, VPN warnings, library-opener routing. **Layer 3 (content)**: every
`LoadResponse` carries its own `type: TvType` — the *actual* category of that piece of content,
which can differ from the provider's declared `supportedTypes`; layer 3 alone drives *page layout
and player behavior* (movie vs episode list, torrent flow, custom-media, live). There is no
cross-validation between layers: a provider can declare `{Movie}` and return `Anime` — filtering
uses layer 2, behavior uses layer 3, nothing reconciles them. [verified]

```
Layer 1  plugins.json        tvTypes[], language          → WHICH EXTENSIONS you see / auto-install
             │  (strings; never parsed into the runtime provider)
             ▼
Layer 2  MainAPI instance    supportedTypes, providerType,→ WHICH PROVIDERS appear in home/search/
             │                lang, vpnStatus, syncNames     library pickers + result-page badges
             ▼
Layer 3  LoadResponse.type   actual TvType per item        → HOW the page & player behave
        (also SearchResponse.type? — optional card badge)
```

---

## 1. The three categorization layers

### 1.1 Layer 1 — plugin manifest / repo metadata (`plugins.json`)

Model (app side, parsed by `RepositoryManager`):

```kotlin
// These types are yet to be mapped and used, ignore for now     ← STALE COMMENT, they ARE used
@JsonProperty("tvTypes") @SerialName("tvTypes") val tvTypes: List<String>?,
// Most often a language tag like "en" or "zh-TW"
@JsonProperty("language") @SerialName("language") val language: String?,
```
`RM:68-71` [verified]

- `tvTypes` holds **TvType enum names as raw strings** (`"Movie"`, `"TvSeries"`, `"Live"`,
  `"NSFW"`, …). Unknown values are tolerated — they simply never match a filter chip (see §2.5).
  Fresh census of phisher's 80 entries: 13 distinct values, of which **two are non-enum** — the
  sentinel `"All"` (AllWish, Ultima) and a malformed comma-joined single string
  `"Movie,Anime,Cartoon"` (Megakino, i.e. someone hand-joined an array); both are filter-inert.
  (Doc 04:233 called `"Cartoon"` non-enum — that's an error in doc 04: `Cartoon` IS a TvType,
  `MA:1124`.) [verified]
- **No `languageFromUrl` field exists** — grep over the whole research snapshot returns 0 hits,
  and doc 04:124-130's census of both real plugins.json files (official 5 entries + phisher 80)
  confirms it does not exist in the wild either. (The task brief mentioned it; it is not in this
  codebase version.) [verified]
- The official repo's 5 entries omit `language` entirely (nullable); phisher's 80 entries always
  include both fields. [docs — 04:124-130]

**What layer 1 drives** (all install/browse-time; none of it reaches the runtime provider object):
1. Extension-store TvType filter chips — `PF:193-204` → `PV.filterTvTypes()` (`PV:226-233`). [verified]
2. NSFW visibility in the store — `PV:204-214` (NSFW plugins hidden unless the *preferred media
   types* setting includes NSFW — see §3). [verified]
3. NSFW marker badge on rows — `PA:97`. [verified]
4. Language filter in the store — `PV.filterLang()` `PV:235-243` + `PF:86-118` (menu dialog).
5. Auto-download eligibility (startup plugin auto-install) — `PM:388-408`. [verified]
6. Language flag display on rows — `PA:165-170`.

**Practical conventions** (phisher-builds, 80 plugins — computed census, [verified]):
- Languages seen: `en`×32, `hi`×27, `de`×4, `id`×4, `mx`×2, `zh`×2, `bn`×2, `ta`×2, `fr`×1,
  `te`×1, `pt-br`×1, `ko`×1. Note **`mx` is a country code, not a language tag** and `pt-br` is
  full BCP-47 — tags are dirty in the wild (see §4).
- Most common `tvTypes` arrays: `["Movie","TvSeries"]`×20, `["AnimeMovie","Anime","Cartoon"]`×11,
  `["AnimeMovie","Anime","OVA"]`×6, `["Live"]`×4, `["AsianDrama","TvSeries","Movie"]`×3,
  `["All"]`×2. Providers self-describe narrowly (anime providers declare the anime trio; drama
  sites declare `AsianDrama`).

### 1.2 Layer 2 — provider runtime declaration

All fields are `open`/`open var` on `MainAPI`, i.e. **optional overrides with defaults**:

```kotlin
/** The language as an IETF BCP 47 conformant tag. … */
open var lang = "en"                                                    // MA:536-546

open val supportedSyncNames = setOf<SyncIdName>()                        // MA:604-616

open val supportedTypes = setOf(                                         // MA:618-624
    TvType.Movie,
    TvType.TvSeries,
    TvType.Cartoon,
    TvType.Anime,
    TvType.OVA,
)

open val vpnStatus = VPNStatus.None                                      // MA:626
open val providerType = ProviderType.DirectProvider                      // MA:627
```

Supporting enums:

```kotlin
/** enum class determines provider type:
 *  MetaProvider: When data is fetched from a 3rd party site like imdb
 *  DirectProvider: When all data is from the site */
enum class ProviderType { MetaProvider, DirectProvider }                 // MA:881-890

/** enum class determines VPN status (Non, MightBeNeeded or Torrent) */
enum class VPNStatus { None, MightBeNeeded, Torrent }                    // MA:892-897
```

Defaults matter: a provider that declares **nothing** claims
`{Movie, TvSeries, Cartoon, Anime, OVA}` + `en` + `DirectProvider` + no VPN warning — the
"general video site" profile. [verified]

Also in this layer: `MainAPI.settingsForProvider: SettingsJson` — a companion-level global the app
pushes provider-relevant settings into (`SettingsJson(enableAdult = false)`, `MA:405-408`;
companion at `MA:495-498`). Despite living in the library, its `enableAdult` flag has **exactly one
consumer** (§3). [verified]

**Real declarations** (quote-check):

```kotlin
// storm-ext DoramasFlixProvider.kt:23-32 — narrow drama provider
override var mainUrl = "https://doramasflix.co"
override var name = "Doramasflix"
override var lang = "mx"
…
override val supportedTypes = setOf(TvType.AsianDrama)

// storm-ext AnimeflvnetProvider.kt:22-37 — anime trio, also lang = "mx"
override var lang = "mx"
override val supportedTypes = setOf(TvType.AnimeMovie, TvType.OVA, TvType.Anime)

// CakesTwix BambooUAProvider.kt:36-42 — anime + drama, Ukrainian
override var lang = "uk"
override val supportedTypes = setOf(TvType.Anime, TvType.AsianDrama)

// CakesTwix HentaiUkrProvider.kt:32-37 — NSFW-ONLY provider
override var lang = "uk"
override val supportedTypes = setOf(TvType.NSFW)

// CakesTwix KlonTVProvider.kt:14-26 — broad video provider
override var lang = "uk"
override val supportedTypes = setOf(TvType.Anime, TvType.TvSeries, TvType.Cartoon, TvType.Movie)

// library metaproviders/TmdbProvider.kt:53-65 — meta provider (default supportedTypes)
override val hasMainPage = true
override val providerType = ProviderType.MetaProvider
```
All [verified].

`vpnStatus` overrides seen in the wild: `MightBeNeeded` (storm-ext CinecalidadProvider.kt:20 and
EntrepeliculasyseriesProvider.kt:30, both commented "Due to evoload sometimes not loading").
No in-tree example of `VPNStatus.Torrent` — it exists in the enum and gets a distinct result-page
label, but the value is declared only by external plugins. [verified] / [inferred]

### 1.3 Layer 3 — content-level type

Every detail-page response carries its own type — `LoadResponse.type: TvType` is a **required**
interface member (`MA:1814-1818`), and the specialized responses default it to their domain:
`TorrentLoadResponse(… type: TvType = TvType.Torrent …)` (`MA:2277`), `LiveStreamLoadResponse(…
type: TvType = TvType.Live …)` (`MA:2426`), `AnimeLoadResponse(… type: TvType …)` (`MA:2328`).
Search cards carry an *optional* `type: TvType?` (`MA:1410`) used only for the "series vs movie"
badge on cards (`ACU:155`, `SearchResultBuilder.kt:262`). [verified]

**Content type can differ from the provider's declared `supportedTypes`** — e.g. a provider
declaring `{Movie, TvSeries}` is free to return `AnimeLoadResponse(type = Anime)`; nothing
validates the response against the declaration. Filtering (layer 2) decides whether the provider is
*listed*; the response type (layer 3) decides how the page/player *behaves*. [verified — no
cross-check exists anywhere in `APIRepository`/`ResultViewModel2`]

### 1.4 Consumer map — who reads which layer

| Consumer | Layer(s) read | Where |
|---|---|---|
| Home provider picker list | 2 (`supportedTypes`, `lang`, `hasMainPage`) | `HF:384-387, 480-497` |
| "Valid chip" computation (which TvType chips to even show) | 2 (`supportedTypes` union) | `HF:544`, `SF:208, 362` |
| Preferred-media provider filter | 2 (`supportedTypes`, `lang`) | `ACU.filterProviderByPreferredMedia` `ACU:447-479` |
| Search provider set | 2 (`lang` via `getApiSettings`; `supportedTypes`) | `ACU:375-382`, `SF:180-195` |
| Extension store chips/NSFW/language | 1 (`tvTypes`, `language`) | `PV:204-243`, `PF:86-204`, `PA:97,165-170` |
| Startup auto-install filter | 1 (`tvTypes`, `language`) + NSFW master toggle | `PM:388-408` |
| Result-page type badge / plot header | 3 (`type`) | `RVM2:308-329` |
| Result-page VPN + "meta provider" badges | 2 (`vpnStatus`, `providerType`) | `RVM2:335-343` → `ResultFragmentPhone.kt:937-941` |
| Play-button label (movie / series / torrent / live) | 3 | `RVM2:1918-1930` |
| Episode-list construction (incl. dub maps) | 3 (response class) | `RVM2:2178-2365` |
| Player: torrent gate, skip-OP UI, watch-state save skip | 3 (`tvType`) | `CS3IP:1808-1831`, `GP:1773`, `GP:1724-1729` |
| CustomMedia "don't open player" | 3 | `RVM2:1553-1568` |
| Download folder layout + download list grouping | 3 (`isEpisodeBased`) | `DownloadFileManagement.kt:112`, `DownloadViewModel.kt:361-364` |
| Library-opener provider list (per tracker ID) | 2 (`supportedSyncNames`) | `LibraryFragment.kt:212-231` |
| Subscription new-episode checks | 3 (`EpisodeResponse`) + DubStatus maps | `SWM:134-167` |

---

## 2. App-side provider management UI

### 2.1 Settings → Providers screen

`SettingsProviders` (`SP:23-143`) inflates `settings_providers.xml` — five rows total
(`res/xml/settings_providers.xml:4-30`): [verified]

| Row | Widget | Key (actual pref name) | Type / default | Behavior |
|---|---|---|---|---|
| Provider language | Preference → multi-dialog | `provider_lang_key` | `StringSet`, default `{"universal"}` (`ACU:399-410`) | Pick from the *distinct provider `lang` values* + an "All Languages" entry; each option is rendered "flag-emoji␣name" (`SP:112-141`) |
| Preferred media type | Preference → multi-dialog | `prefer_media_type_key` → pref name **`prefer_media_type_key_2`** (`donottranslate-strings.xml:61`) | `StringSet` of **TvType ordinals as strings**, default = all ordinals **except NSFW** (`SP:81-83`) | On save, resets the home page: `DataStoreHelper.currentHomePage = null` (`SP:105`) |
| Display subbed/dubbed | Preference → multi-dialog | `display_sub_key` | `StringSet` of `DubStatus` names, default = all (`ACU:384-397`) | Writes `APIRepository.dubStatusActive` for the anime dub/sub toggle (`SP:52`) |
| Enable NSFW on supported Extensions | **SwitchPreference** | `enable_nsfw_on_providers_key` | Boolean, default **false** (`settings_providers.xml:18-23`) | Summary "apply on restart"; pushes into `MainAPI.settingsForProvider` at next `MainActivity.onCreate` (`MainActivity:1192-1196`) |
| Test extensions | Preference | — | — | Navigates to the provider-test screen (shows a flag per provider via `getFlagFromIso(api.lang)`, `TestResultAdapter.kt:73`) |

Note the subtlety: there are **two independent NSFW controls** — the switch above (which only
affects auto-download, §3) and the *preferred media types* set (which is the real visibility
filter everywhere else). [verified]

### 2.2 First-run setup

Two setup steps mirror the settings: `SetupFragmentProviderLanguage`
(`SetupFragmentProviderLanguage.kt:28-79` — multi-select language list with flags, writes
`provider_lang_key`) and `SetupFragmentMedia` (`SetupFragmentMedia.kt:26-76` — multi-select of all
TvType **names**, stored as **ordinals** into `prefer_media_type_key`, resets
`currentHomePage`). The language list is built from the *currently loaded* providers'
`lang` values (`SetupFragmentProviderLanguage.kt:39-43`) — during setup, before repos are added,
this typically shows just the built-ins. [verified]

### 2.3 Home provider picker — the real category switcher

`Context.selectHomepage(...)` (`HF:383-552`) — bottom sheet opened from the home toolbar. This is
the UI that most directly answers "categorize extensions by movies / TV / Asian drama":

1. Base list = `filterProviderByPreferredMedia()` (language + preferred-media + `hasMainPage`
   filter, §2.6), then **`noneApi` and `randomApi` pseudo-providers are prepended**
   (`HF:386-387`; display names "None"/"Random" at `HF:117-119`; definitions at
   `APIRepository.kt:37-46` — both pseudo-providers declare `supportedTypes = emptySet()` and
   `lang = ""`; behavior per doc 06 §4/§7 — single-provider home, persisted in
   `DataStoreHelper.currentHomePage` = `"$account/home_api_used"`, `DSH:187-196`). [verified]/[docs]
2. A **TvType chip row** (same `tvtypesChips` component as search) whose groups are hardcoded:
   `getPairList` maps 10 chips → TvType lists (`HF:280-305`) —
   Movies→`[Movie]`, TV Series→`[TvSeries]`, Anime→`[Anime, OVA, AnimeMovie]`,
   Asian→`[AsianDrama]`, Cartoons→`[Cartoon]`, Documentaries→`[Documentary]`,
   Livestreams→`[Live]`, Torrents→`[Torrent]`, NSFW→`[NSFW]`, Others→`[Others]`.
   Only chips matching *some installed provider's* `supportedTypes` are even visible
   (`validTypes = validAPIs.flatMap { it.supportedTypes }.distinct()`, `HF:544`). [verified]
3. Chip state is persisted to `DataStoreHelper.homePreference` (DataStore key `home_pref_homepage`,
   default `[Movie, TvSeries]`, `DSH:130-138`) and live-filters the provider list:
   ```kotlin
   it.hasMainPage && (isPinned || it.supportedTypes.any(preSelectedTypes::contains))  // HF:494-496
   ```
   So **yes — the home "switch provider" dialog filters by what supports the currently selected
   categories**, and the categories are user-toggleable inside the dialog itself. [verified]
4. **Pinned providers**: long-press a provider to (un)pin (`HF:526-539`, stored
   `DataStoreHelper.pinnedProviders` = `user_pinned_providers`, `DSH:60, 827-829`). Pinned
   providers float to the top in reverse-pin order, then the rest alphabetical (`HF:504-514`).
   **There is no `providersRank`** — repo-wide grep for `providersRank|providerRank` = 0 hits;
   pinning *is* the ranking mechanism, and it is user-set but binary+order-of-pinning, not a
   drag-rank. [verified]
5. NSFW nicety: a pinned provider whose `supportedTypes` are **all** NSFW is hidden from the list
   unless the NSFW chip is on — "NSFW is distracting when not chosen" (`HF:489-492`). [verified]
6. Per-provider **settings gear** appears inline if the plugin defined `openSettings`
   (`HF:450-463`).
7. When more than one language (or "All") is selected, each provider name is prefixed with its
   flag emoji (`HF:516-519`). [verified]

### 2.4 Search filter

The search screen has (a) an always-visible TvType chip row bound the same way
(`SF:202-218`, persisted as `DataStoreHelper.searchPreferenceTags`, key `search_pref_tags`,
default `[Movie, TvSeries]`, `DSH:120-128`) and (b) a filter bottom sheet (`SF:284-405`) with
TvType chips + a **multi-select provider list** (persisted `searchPreferenceProviders`, key
`search_pref_providers`, `DSH:96-118`). The actual search fuses all three filters:

```kotlin
val settings = ctx.getApiSettings()                       // language-filtered provider names (ACU:375-382)
val notFilteredBySelectedTypes = selectedApis.filter { name -> settings.contains(name) }
    .map { name -> name to getApiFromNameNull(name)?.supportedTypes }
    .filter { (_, types) -> types?.any { preferredTypes.contains(it.ordinal) } == true }  // preferred media
searchViewModel.searchAndCancel(query,
    providersActive = notFilteredBySelectedTypes
        .filter { (_, types) -> types?.any { selectedSearchTypes.contains(it) } == true } // chip filter
        .ifEmpty { notFilteredBySelectedTypes }                                           // fallback: ignore chips
        .map { it.first }.toSet())
```
`SF:168-197` [verified] — note the graceful fallback: if the chip filter would eliminate every
provider, it is ignored.

### 2.5 Extension-store (repo browsing) filtering

Per-repo plugin screen (`PluginsFragment`) — TvType chip row (`PF:193-204`, bound with all 18
`TvType.entries` — including NSFW) + toolbar language multi-dialog (`PF:86-118`) + fuzzy search.
Filtering in `PluginsViewModel`:

```kotlin
private fun List<PluginViewData>.filterTvTypes(): List<PluginViewData> {
    if (tvTypes.isEmpty()) return this
    return this.filter {
        (it.pluginWrapper.plugin.tvTypes?.any { type -> tvTypes.contains(type) } == true) ||
                (tvTypes.contains(TvType.Others.name) && (…tvTypes ?: emptyList()).isEmpty())
    }
}
```
`PV:226-233` [verified] — string-equality against the manifest; the special case: selecting the
"Others" chip also shows plugins with an **empty** `tvTypes` array. An `"All"` manifest value never
matches any chip (filter-inert). Language filter is lowercase string equality with a `"none"`
bucket for missing language (`PV:235-243`); the store **pre-seeds** the language filter from the
provider-language setting so the store opens pre-narrowed (`PF:57-63`). Search is Levenshtein
partial-ratio > 80 on name, then first-64-chars of description, sorted by score (`PV:245-269`).
This is doc 04 §4.3's UI side. [verified]/[docs]

### 2.6 The core filter function + key inventory

`Context.filterProviderByPreferredMedia(hasHomePageIsRequired = true)` is the single funnel most
provider lists go through (`ACU:447-479`):

```kotlin
val default = TvType.values().sorted().filter { it != TvType.NSFW }.map { it.ordinal }
…
val langs = this.getApiProviderLangSettings()
val hasUniversal = langs.contains(AllLanguagesName)
val allApis = apis.filter { api ->
    (hasUniversal || langs.contains(api.lang)) && (api.hasMainPage || !hasHomePageIsRequired)
}
return if (currentPrefMedia.isEmpty()) allApis
else allApis.filter { api -> api.supportedTypes.any { currentPrefMedia.contains(it.ordinal) } }
```
[verified] — three orthogonal dimensions: language (OR-sentinel), `hasMainPage`, and
`supportedTypes ∩ preferredTypes ≠ ∅`. It even contains a workaround for a
`ClassCastException` caused by plugin classloaders ("classloader fuckery", `ACU:448-459`) —
TvType is compared by **ordinal** (Int) precisely to survive cross-classloader enum identity
issues. [verified]

**SharedPreferences keys** (all in the default shared prefs; string values from
`donottranslate-strings.xml`):

| Key string | Type | Default | Effect |
|---|---|---|---|
| `provider_lang_key` | StringSet | `{"universal"}` | Provider language filter (home/search/store pre-filter) |
| `prefer_media_type_key_2` | StringSet (ordinals) | all minus NSFW | Preferred media types — the master category filter |
| `display_sub_key` | StringSet (DubStatus names) | all | Which dub tracks to show/enable |
| `enable_nsfw_on_providers_key` | Boolean | `false` | NSFW master toggle (auto-download only!) |
| `auto_download_plugins_key2` | Int | `0` (Disable) | Plugin auto-install mode (§7) |
| `auto_update_plugins` | Boolean | `true` | Auto-update installed plugins (`MainActivity:1359-1369`) |
| `filter_sub_lang_key` | Boolean | `false` | Subtitle-list language filter (reuses `provider_lang_key`, `GP:2264-2273`) |
| `search_type_list` | StringSet | all | **DEAD** — read by `getApiTypeSettings()` (`ACU:412-428`) which has zero callers |

**DataStore keys** (`DataStoreHelper`):

| Key string | Type | Default | Effect |
|---|---|---|---|
| `home_pref_homepage` | List<TvType names> | `[Movie, TvSeries]` | Home chip selection (`DSH:130-138`) |
| `search_pref_tags` | List<TvType names> | `[Movie, TvSeries]` | Search chip selection (`DSH:120-128`) |
| `search_pref_providers` | List<String> | empty → all preferred | Search provider selection (`DSH:96-118`) |
| `"$account/home_api_used"` | String? | null | Current home provider (`DSH:187-196`) |
| `user_pinned_providers` | Array<String> | `[]` | Pinned providers, in pin order (`DSH:60, 827-829`) |

All [verified].

---

## 3. NSFW handling

NSFW is gated at **three different points with two different switches**:

1. **Plugin auto-install (uses `enable_nsfw_on_providers_key`)**: at startup,
   `MainActivity.onCreate` copies the switch into `MainAPI.settingsForProvider.enableAdult`
   (`MainActivity:1192-1196`). Its **only** consumer is the auto-download filter:
   ```kotlin
   //Omit NSFW, if disabled
   if (!settingsForProvider.enableAdult) {
       if (tvtypes.contains(TvType.NSFW.name)) return@mapNotNull null
   }
   ```
   `PM:393-398` [verified]. With `AutoDownloadMode.NsfwOnly` the polarity flips — *only* NSFW
   plugins install (`PM:388-392`).
2. **Everything visible (uses `prefer_media_type_key_2`)**: NSFW's ordinal is excluded from the
   preferred-media **default set** (`ACU:454-457`, `SP:81-83`, `SF:173-174`), so out of the box:
   - extension store hides NSFW plugins (`PV:204-214` — `isAdult` = "NSFW ∈ preferred types"),
   - home/search provider filtering excludes NSFW-only providers (§2.6 — `supportedTypes.any { … }`
     never matches when NSFW ordinal isn't selected… for NSFW-*only* providers; providers with
     mixed types still pass),
   - the home picker additionally hides pinned NSFW-only providers (`HF:489-492`).
   The user "enables" NSFW by adding it in *Preferred media type* — the dedicated switch
   (point 1) does NOT affect any of these. [verified]
3. **Player (hardcoded)**: watch progress is never saved for NSFW content —
   ```kotlin
   // Don't save NSFW data
   if ((currentMeta as? ResultEpisode)?.tvType == TvType.NSFW) return
   ```
   `GP:1728-1729` [verified]. NSFW also gets its own card/type label string (`RVM2:321`) and its
   own store badge (`PA:97`). No per-provider NSFW override exists anywhere. [verified — grep
   `NSFW` across app returns only the sites listed here]

---

## 4. Language system

**Convention**: `lang` is documented as "an IETF BCP 47 conformant tag" with KDoc linking CLDR,
IANA, Android locale_config and ISO 639-3 (`MA:536-546`); default `"en"`. The same subtitle-helper
stack resolves it: `getLanguageDataFromCode` looks the tag up in five indexes (IETF / ISO 639-1 /
ISO 639-3 / ISO 639-2B / OpenSubtitles) and returns null on miss (`SubH:158-170`); tag well-formedness
has a regex checker (`SubH:234-250`).

**Reality is dirtier**: `"mx"` (a country code) is used by **all 27 Spanish-language storm-ext
providers** (e.g. `DoramasFlixProvider.kt:26`, `AnimeflvnetProvider.kt:27`,
`MonoschinosProvider.kt:12` — grep `lang = "mx"` = 27 files, zero use `"es"`) and
appears twice in phisher's index; `pt-br` is full BCP-47; CakesTwix uses clean `"uk"`. Because the
lookup returns null, the UI degrades to the raw string: `getNameNextToFlagEmoji(lang) ?: lang`
(`SP:116`, `PA:169`). [verified]

**Sentinel**: `AllLanguagesName = "universal"` (`MA:91`) — stored *in* the prefs set to mean "no
language filtering"; `getApiProviderLangSettings` defaults to it (`ACU:399-410`), and every filter
short-circuits when present (`ACU:470`, `PM:404`).

**Display**: `getFlagFromIso` maps tag→flag emoji via a `lang2country` table + country subtag of
the tag itself (`SubH:256-277` — incl. the `"qt"`→🦍 easter egg at `SubH:272-273`);
`getNameNextToFlagEmoji` returns `"flag␣localized-name"` (`SubH:284-286`), and every language
dialog sorts by the name *after* the flag (`SP:117`, `PF:94-96`,
`SetupFragmentProviderLanguage.kt:42`). Flags appear: on extension rows (`PA:165-170`), in
language pickers, next to provider names in home/search pickers **only when the user selected
>1 language or "All"** (`HF:516-519`, `SF:343-349` — clever: with one language the flag is noise),
and in plugin details (`PluginDetailsFragment.kt:84`).

**Per-language provider counts**: NOT displayed anywhere — the language dialogs show names only,
no "(12 providers)" counts. [verified by absence — grep of all language-dialog call sites]

**Cross-pollination**: the subtitle language filter reuses `provider_lang_key` —
`filter_sub_lang_key` (default false) enables filtering the player's subtitle list by the
provider-language selection, converted to English language names (`GP:2264-2273`). Note its
fallback default is `mutableSetOf("en")`, **inconsistent** with the setting's own default
`{"universal"}` — a latent oddity if the pref were somehow unset. [verified]

---

## 5. Category-driven behavior differences (layer 3 switch points)

Grouping helpers (all `MA`, table in doc 05 §6.1): `isMovieType()` = `AnimeMovie, Live, Movie,
Torrent, Video` (`MA:1159-1169`); `isAudioType()` = `Audio, AudioBook, Music, Podcast`
(`MA:1174-1183`); `isLiveStream()` (`MA:1188-1190`); `isAnimeOp()` = `Anime, OVA`
(`MA:1195-1197`); `isEpisodeBased()` = `Anime, AsianDrama, Cartoon, TvSeries` (`MA:2165-2174`).

- **Movie-like** (`isMovieType`): single play button, no episode list — `postMovie()` picks the
  button label: Torrent→"play_torrent_button", TvSeries→"play_full_series_button",
  Live→"play_livestream_button", movie→"play_movie_button" (`RVM2:1918-1937`). Player next-up
  logic checks `isMovieType` to decide episode continuity (`GP:564, 572, 1653`). [verified]
- **Torrent**: the magnet/torrent string becomes the episode `data` (`RVM2:2339-2360`); the player
  gates on **two** conditions — Torrent must be in preferred media types
  (`CS3IP:1827-1831`, error string `torrent_preferred_media`) AND the user must have accepted the
  per-session torrent consent dialog (`CS3IP:1833-1837`, `Torrent.hasAcceptedTorrentForThisSession`,
  `Torrent.kt:20`); playback then runs through the embedded TorrServer localhost rewrite (doc 09
  §1). `vpnStatus = Torrent` produces a distinct "use VPN" badge (`RVM2:335-341`). [verified]/[docs]
- **Live**: play-button label (above); watch position is never saved —
  `if ((currentMeta as? ResultEpisode)?.tvType?.isLiveStream() == true) return`
  (`GP:1724-1726`); live player error ladder in doc 09 §1. No EPG anywhere — no electronic program
  guide exists in the codebase [verified by absence]; "Live" is just movie-shaped playback without
  resume.
- **Anime / OVA** (episode-based + `isAnimeOp`):
  - Episodes arrive as a **`MutableMap<DubStatus, List<Episode>>`** (`MA:2333`) — dub and sub are
    separate lists under the same show. The app builds one flat episode list with
    **ID offsetting per dub-status** (`id = mainId + episode + idIndex * 1_000_000 + season*10_000`,
    `RVM2:2183-2189`), then surfaces a **dub/sub chip selector** built from whichever keys exist
    (`RVM2:2368-2375` → `ResultFragmentPhone.kt:1365`); the per-show choice is persisted
    (doc 05 §6.2: `DataStoreHelper.setResultDub`). [verified]/[docs]
  - Skip-opening UI only for `isAnimeOp` types (`GP:1773`); AniSkip intro-stamps are MAL-id based
    (doc 09 §4).
  - **Tracker targeting via layer 2**: `supportedSyncNames` decides which providers can open a
    given AniList/MAL/IMDb/… list entry — the library-opener dialog offers exactly the providers
    whose `supportedSyncNames` contains that `SyncIdName` (`LibraryFragment.kt:212-231`), and
    `SyncRedirector.redirect` turns a sync ID into a provider URL at load time
    (`RVM2:2643`, contract in doc 03 §2.12). [verified]/[docs]
- **NSFW**: hidden by default (§3); no watch-state saving (`GP:1728-1729`).
- **CustomMedia**: "Won't load the built in player, make your own interaction" (`MA:1136-1137`).
  The app's only concession: clicking play runs `generateLinks` **without navigating to the
  player** — the provider's own UI (registered via player actions) takes over
  (`RVM2:1553-1568`). The provider-test harness treats a CustomMedia load as *pass* without
  playing (`TestingUtils.kt:208`). [verified]
- **Episode-based vs not** additionally controls: download folder layout
  (`TvType/Show/"S N E - Name.mp4"` vs flat, `DownloadFileManagement.kt:112` + doc 09 §5),
  download-list grouping/sort (`DownloadViewModel.kt:361-364, 465`), click behavior
  (`DownloadFragment.kt:266`), episode title formatting in the player (`GP:1904`), and
  subscription eligibility (`RVM2:1959-1969`, §7). [verified]
- **Audio/Music/Podcast**: `isAudioType()` exists in the library but has **zero app-side callers**
  (grep `isAudioType()` in app = 0 hits) — there is no audio player path; audio types behave like
  video/movie content. Label-only category. [verified by absence]
- **MetaProvider vs DirectProvider** (layer 2): the only UI effect is a small "meta provider"
  info badge on the result page (`metaText`, `RVM2:342-343`, bound at
  `ResultFragmentPhone.kt:938`); the real significance is architectural — meta providers
  (TMDb/Trakt/MyDramaList, `providerType = ProviderType.MetaProvider` overrides) supply metadata
  that *other* providers' links get attached to via sync IDs (docs 03 §6, 07 §8). [verified]/[docs]

---

## 6. Genre browsing

There is **no genre filter API** — `MainPageRequest` carries a literal
`//TODO genre selection or smth` (`MA:420`), and no `getFilterList` analog exists (doc 06 §1/§5,
repo-wide grep). Genre browsing is therefore **encoded in `mainPage` rows**: the provider declares
N named rows, each with an opaque `data` token, and the row *name* is the genre label. Doc 06 §5
documents this in depth with two full examples: [docs]

- **AllCalidadProvider (storm-ext)**: 18 rows = 3 content-type rows + 15 genre rows, where `data`
  is a `"postType:genreId"` pair appended as `&genres=$id` to the API call (doc 06:854-880).
- **Uakino/BambooUA (CakesTwix)**: rows are URL prefixes; one row's results are even re-sorted
  client-side by sniffing a "Жанр:" (genre) label (doc 06:949-951).

Smaller-scale examples verified in this pass: Monoschinos declares
`mainPageOf("" to "Últimos capítulos", "animes" to "Catálogo")`
(`MonoschinosProvider.kt:24-26`); KlonTV `mainPageOf("$mainUrl/filmy/page/" to "Фільми", …)`
(`KlonTVProvider.kt:29-31`). Tags shown on detail pages (`LoadResponse.tags`, `RVM2:295`) are
chips, not a browsable dimension. The consequence: **"browse by genre" in CS3 = scroll the
provider's chosen home rows**; categories (TvType) are the only *systematic* filter dimension.
[verified]/[docs]

---

## 7. Auto-download & subscriptions

Two unrelated features share the "auto-download" name — worth separating clearly:

### 7.1 Plugin auto-install — `AutoDownloadMode`

```kotlin
enum class AutoDownloadMode(val value: Int) {
    Disable(0), FilterByLang(1), All(2), NsfwOnly(3);
    companion object { infix fun getEnum(value: Int) = entries.firstOrNull { it.value == value } }
}
```
`MA:1144-1154` [verified]

- **Where set**: Settings → Updates → "automatic_plugin_download_mode_title" — a single-select
  dialog with labels Disable / Filter by language / All / NSFW (`SU:257-277`;
  `res/values/array.xml:50-55`), stored as Int in `auto_download_plugins_key2` (default 0).
  Included in backups (`BackupUtils.kt:112`). [verified]
- **Where used**: at every app start, after plugin update/load, MainActivity reads the mode and —
  unless `Disable` — calls `___DO_NOT_CALL_FROM_A_PLUGIN_downloadNotExistingPluginsAndLoad`
  (`MainActivity:1371-1383`). That function walks all repo plugins and installs every not-yet-
  installed one that survives the category filters (`PM:340-418`):
  1. skip blank `url` / blank `repositoryUrl` (`PM:373-379`);
  2. skip already-installed (path exists, `PM:381-385`);
  3. **`NsfwOnly`**: keep *only* plugins whose manifest `tvTypes` contains `"NSFW"`
     (`PM:388-392`);
  4. **NSFW master toggle**: drop NSFW-tagged plugins unless `enableAdult` (`PM:393-398`);
  5. **`FilterByLang`**: drop plugins whose manifest `language` is null or not in the
     provider-language setting (unless "universal") (`PM:400-408`).
  So **categories drive install eligibility at two levels** — TvType (NSFW) and language.
  `All` skips both filters (3/5) but still respects the NSFW toggle (4). [verified]

### 7.2 Episode subscriptions (new-episode notifications)

`SubscriptionWorkManager` is a 6-hour periodic WorkManager job (`SWM:38-66`) that loads every
subscribed show, and for each: resolves the provider, calls `api.load(url)` with a 60s timeout
(`SWM:140-142`), picks the dub preference — **per-show** stored dub status first, else the global
`display_sub_key` setting (Dubbed if present, else Subbed) (`SWM:144-151`) — then compares
`getLatestEpisodes()[dubPreference]` against the stored `lastSeenEpisodeCount` and posts a
notification when it grew (`SWM:153-207`). Subscription eligibility itself is **episode-based
only** (`postSubscription`, `RVM2:1959-1969`), i.e. layer-3 type gates it; no language or TvType
filter applies to notifications. There is **no automatic episode *downloading*** — notifications
only (downloads are always user-initiated per doc 09 §5). [verified]

---

## 8. Configuration design recommendations for ANI-KUTA

Current ANI-KUTA state (for mapping): our aniyomi-style `AnimeExtension` model already carries
`lang: String?`, `isNsfw: Boolean`, `isTorrent: Boolean`, `isEnabled: Boolean`
(`data/extension/.../AnimeExtension.kt:20-60`); our `ExtensionsSettingsScreen` already has a
D-298 language filter (single-select chips over distinct `lang` values, in-memory
`langFilter`/`showNsfw` state defaulting showNsfw=**true**, sort modes incl. LANGUAGE and
NSFW-first, plus reorder mode — `ExtensionsSettingsScreen.kt:138-205, 423-515`); our content side
has `CanonicalGenres` and a content `isNsfw` flag (`core/content/.../ContentModels.kt:199`). No
category/type dimension exists on extensions yet, because aniyomi extensions are anime-only by
construction. [verified]

**[recommendation] Replicate (high value):**

1. **The three-layer split, verbatim** — manifest `tvTypes[]`+`language` in our repo index
   (browse/install-time only, never trusted at runtime), provider-level `supportedTypes`+`lang`
   (filtering), response-level type (behavior). This is the single most load-bearing design in
   CS3's categorization and it composes cleanly with our existing aniyomi layer (which already
   separates manifest metadata from runtime sources).
2. **One shared category enum as the single source of truth** for all three layers, compared by
   **ordinal/name string** — CS3's ordinal comparison exists to survive plugin classloader enum
   identity issues (`ACU:448-459`); we will hit the same problem with .cs3 plugins, so compare
   primitives, not enum references, across the plugin boundary.
3. **Chip→types grouping map** like `getPairList` (`HF:280-305`): 8-10 user-facing chips mapping
   to type lists (Anime = Anime+OVA+AnimeMovie is exactly the grouping users expect). Reuse the
   chip component on Home-browse, Search, and the Extensions screen so one selection model is
   learned once.
4. **Graceful chip fallback**: if chip filters would empty the provider set, ignore the chips
   rather than showing nothing (`SF:192-194`) — cheap, avoids the "empty screen" support burden.
5. **NSFW: one master toggle, persisted, default OFF** — unlike CS3's accidental *two* switches
   (§3), pick ONE (say `cs3NsfwEnabled`) and have it gate: store visibility, provider filters,
   auto-install, *and* preferred-media defaults. Keep CS3's player-side "don't save NSFW watch
   progress" rule (`GP:1728-1729`) — privacy-positive and one line. Our current in-memory
   `showNsfw = true` default (ExtensionsSettingsScreen.kt:141) is the opposite polarity — flip to
   persisted-false when the CS3 store lands.
6. **Persist the browse/search filters** in DataStore (CS3's `search_pref_tags` /
   `home_pref_homepage` pattern, `DSH:120-138`) instead of our current `remember { }` — users
   re-enter the extensions screen often enough that re-selecting the language filter every time is
   friction.
7. **Language: tolerate dirty tags.** CS3's `SubH.getLanguageDataFromCode` five-index lookup with
   raw-string fallback (`SubH:158-170`, `PA:169`) is the right shape; "mx" proves real repos emit
   junk. Multi-select + an "All" sentinel value stored in the set (CS3's "universal", `MA:91`) is
   simpler than null-vs-set logic. Flag-emoji-only-when-multilingual (`HF:516-519`) is a nice
   touch worth copying.

**[recommendation] Simplify / defer:**

8. **Skip `AutoDownloadMode` initially.** Auto-installing extensions by language/NSFW at every
   launch (`PM:340-418`) is power-user behavior with real surprise risk (NSFW plugins appearing
   after a settings flip; dozens of plugins appearing because "All" was chosen). Our repo browsing
   + "install" flow suffices; revisit `FilterByLang` only if users ask. If we do add it, keep
   CS3's rule that the NSFW master toggle overrides `All`.
9. **Defer `vpnStatus`** to a badge-only string on the details screen (its entire CS3 effect,
   `RVM2:335-341`) — no enforcement logic needed.
10. **`providerType` (MetaProvider) can wait** until we do tracker/metadata enrichment — but keep
    the field in our provider interface from day one (one boolean, zero cost) so metaprovider
    extensions don't need a breaking change later.
11. **Don't build audio/podcast behavior** — CS3 itself never implemented an audio player path
    (`isAudioType()` has zero app callers, §5); audio types are labels. Accept them as categories,
    play them as video.
12. **Torrent consent flow** (preferred-types gate + per-session accept, `CS3IP:1808-1837`) —
    only if/when we ship torrent extensions; our `AnimeExtension.isTorrent` flag is already the
    right hook.

**[recommendation] Mapping onto our screens:**

13. Extensions screen: add a TvType chip row *above* the D-298 language chips
    (PluginsFragment's layout order: type chips in a scroll row, language as a toolbar dialog —
    `PF:86-118, 193-204`); keep our section-based list (Installed/Available/…) and apply the type
    filter across sections like CS3 applies it across the flat list.
14. Home/browse: our anime-browse feature gets the category chips; the CS3 insight to copy is that
    the *provider picker and the category chips live in the same dialog* and the provider list
    re-filters live (`HF:541-550`) — category and source selection are one mental operation.
15. Search: adopt `filterProviderByPreferredMedia`'s shape (language OR-sentinel ∩ hasMainPage ∩
    type-intersection) as a single testable function in our extension manager rather than
    scattering three predicates across UI code.

---

## 9. Unverified / open questions

- `VPNStatus.Torrent` is declared and gets a distinct label, but no in-tree or sampled plugin
  declares it — whether any real-world plugin uses it (and whether any fork adds behavior beyond
  the badge) is unverified. [inferred]
- Whether any real plugin relies on the `"All"` tvTypes manifest sentinel meaning something
  (it matches nothing in any filter; possibly a legacy or aspirational value). [inferred]
- Doc 03:240 cites a `supportedTypes = setOf(TvType.Live)` example at
  `extensions/TwitchProvider/.../TwitchProvider.kt:30` — that path is not in this research
  snapshot (no `extensions/` module in the tree); the citation is inherited from doc 03 and not
  re-verified here. [docs]
- The `search_type_list` pref (`getApiTypeSettings`, `ACU:412-428`) being dead code is verified by
  grep (zero callers) — but it may be live in some fork; "dead" is only claimed for this snapshot.
- Behavior of `AutoDownloadMode.NsfwOnly` *combined with* `enableAdult=false` is read as
  contradictory-but-harmless (NSFW-only mode ignores the toggle; `PM:388-398` checks NsfwOnly
  first) — actual upstream intent unverified. [inferred]
- Home chip visibility: chips are limited to types that *installed providers* declare
  (`HF:544`) — with zero providers with `hasMainPage`, the dialog shows only None/Random; this
  edge case was reasoned from code, not run. [inferred]
