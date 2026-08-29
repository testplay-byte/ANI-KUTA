# 04 — CloudStream Extension Repository System (repo.json / plugins.json)

> **Doc owner**: research batch B1-d (agent 40-B1-d). Part of the CloudStream research program — see
> `00-RESEARCH-TRACKER.md`. Read after `01-ecosystem-overview.md` and `02-plugin-format.md`.
>
> **Scope**: the repository protocol — the two-level JSON index (`repo.json` → `plugins.json`),
> every field of a plugin entry, the app-side repository manager (add / browse / install / update /
> delete), the community repo registry (`cs-repos`), hosting patterns, and the security model.
> The `.cs3` binary itself is covered in `02-plugin-format.md`.
>
> **Path abbreviations used in citations** (all under `/home/z/ANI-KUTA-WORK/research/` unless noted):
> | Short | Path |
> |---|---|
> | `APP/` | `cloudstream/app/src/main/java/com/lagradost/cloudstream3/` |
> | `LIB/` | `cloudstream/library/src/commonMain/kotlin/com/lagradost/cloudstream3/` |
> | `RES/` | `cloudstream/app/src/main/res/` |
> | `EXT/` | `extensions/` (recloudstream/extensions clone) |
> | `PHISHER/` | `phisher-builds/` (phisher98/cloudstream-extensions-phisher @ builds clone) |
> | `CSREPOS/` | `cs-repos/` (recloudstream/cs-repos clone) |
> | `CSDOCS/` | `csdocs/` (recloudstream/csdocs clone) |
> | `GRADLE/` | `cloudstream3/gradle/` sources of **github.com/recloudstream/gradle** (master tarball fetched 2026-08-29 to `/tmp/cs-gradle/` — this doc's author fetched it; B1-b had left it as an open item) |
>
> **Confidence markers**: `[verified]` = read directly in source (path:line) · `[verified-net]` = fetched
> live over network · `[docs]` = from official csdocs · `[inferred]` = reasoned, needs verification.

---

## 1. The two-level index model

A CloudStream "repository" is **two levels of JSON indirection**, deliberately split so that the
repo metadata (name/description) and the plugin list can live in different places and be updated
independently:

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Level 0 — what the user adds (a URL)                                       │
│   e.g. https://raw.githubusercontent.com/recloudstream/extensions/         │
│        master/repo.json                                                    │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               ▼ GET (5-min HTTP cache)
┌────────────────────────────────────────────────────────────────────────────┐
│ Level 1 — repo.json (the repository manifest)                              │
│   { name, description, iconUrl?, manifestVersion, pluginLists[] }          │
│   pluginLists = ARRAY of URLs → a repo can aggregate several plugin lists  │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               ▼ GET each URL in parallel (amap), 5-min cache
┌────────────────────────────────────────────────────────────────────────────┐
│ Level 2 — plugins.json (each entry in pluginLists)                         │
│   [ SitePlugin, SitePlugin, … ]  — one entry per installable extension     │
│   url → the actual .cs3 file (downloaded + SHA-256 verified on install)    │
└──────────────────────────────┬─────────────────────────────────────────────┘
                               ▼ GET .cs3 → hash check → atomic move → load
                          the plugin (.cs3 zip, see doc 02)
```

App-side code that implements this chain (`APP/plugins/RepositoryManager.kt`):

- Level 1 fetch: `parseRepository()` — `app.get(convertRawGitUrl(url), cacheTime = 5, cacheUnit = TimeUnit.MINUTES).parsedSafe<Repository>()` `[verified]` (RepositoryManager.kt:161-167)
- Level 2 fetch: `parsePlugins()` — same 5-minute cache; on any parse/network error it logs and returns `emptyList()` `[verified]` (RepositoryManager.kt:169-178)
- Join: `getRepoPlugins(repositoryData)` — parses the repo, then maps **every** `pluginLists` URL in parallel (`amap`), flattening the results into `List<PluginWrapper>` (each wrapper carries the repo object + the stored `RepositoryData` + the `SitePlugin`) `[verified]` (RepositoryManager.kt:183-191)

### 1.1 Real example — level 1: the official repo.json

The official repository's manifest (file at `EXT/repo.json:1-8`, identical on both `master` and `builds`
branches — the registry entry points at the master copy, `CSREPOS/repos-db.json:3`):

```json
{
    "name": "Cloudstream providers repository",
    "description": "Cloudstream extension Repository",
    "manifestVersion": 1,
    "pluginLists": [
      "https://raw.githubusercontent.com/recloudstream/extensions/builds/plugins.json"
    ]
}
```

A community repo adds one optional field — `iconUrl` (repo icon shown in the repo list UI), from
`PHISHER/repo.json:1-9`:

```json
{
    "name": "Phisher Repo",
    "iconUrl": "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/RepoIcon.png",
    "description": "Phisher Repository",
    "manifestVersion": 1,
    "pluginLists": [
      "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/plugins.json"
    ]
}
```

### 1.2 Real example — level 2: one full plugin entry

The full first entry of the official plugins.json (fetched live 2026-08-29 from
`https://raw.githubusercontent.com/recloudstream/extensions/builds/plugins.json` — 5 entries total,
all `status: 1`, all five safe providers listed in doc 01) `[verified-net]`:

```json
{
    "iconUrl": "https://www.google.com/s2/favicons?domain=www.dailymotion.com&sz=%size%",
    "jarFileSize": 49791,
    "fileHash": "sha256-9036525a64e8b3c8fe04f94e9fe89a744c13d69af684ed0d2ad2a7eb0f332cd9",
    "apiVersion": 1,
    "repositoryUrl": "https://github.com/recloudstream/extensions",
    "fileSize": 11472,
    "status": 1,
    "authors": [
        "Luna712"
    ],
    "tvTypes": [
        "Others"
    ],
    "version": 4,
    "internalName": "DailymotionProvider",
    "jarUrl": "https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.jar",
    "description": "Watch content from Dailymotion",
    "url": "https://raw.githubusercontent.com/recloudstream/extensions/builds/DailymotionProvider.cs3",
    "name": "DailymotionProvider",
    "jarHash": "sha256-542c325f2e1e1f61db60952d161325b62ff58712b1b2020e6df50727fdd03bf2"
}
```

Note what is **not** there: no `language` (all 5 official entries omit it — it is nullable), no
`requiresResources`, no `fileMimeType`. Field census across the two real plugins.json files studied
(official = 5 entries; phisher = 80 entries): the union of keys is exactly 17 —
`apiVersion, authors, description, fileHash, fileSize, iconUrl, internalName, jarFileSize, jarHash,
jarUrl, language, name, repositoryUrl, status, tvTypes, url, version` `[verified]`
(`PHISHER/plugins.json` python census + `/tmp/official-plugins.json` census). `languageFromUrl` and
`fileMimeType` do **not** exist in the wild `[verified]`.

---

## 2. The `repo.json` format

### 2.1 Every field

App-side model — the exact declaration the JSON is parsed into (`APP/plugins/RepositoryManager.kt:30-40`):

```kotlin
/**
 * Comes with the app, always available in the app, non removable.
 */
@Serializable
data class Repository(
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String?,
    @JsonProperty("name") @SerialName("name") val name: String,
    @JsonProperty("description") @SerialName("description") val description: String?,
    @JsonProperty("manifestVersion") @SerialName("manifestVersion") val manifestVersion: Int,
    @JsonProperty("pluginLists") @SerialName("pluginLists") val pluginLists: List<String>,
)
```

| Field | Type (app model) | Required by app | Semantics |
|---|---|---|---|
| `name` | `String` (non-null) | **yes** (parse fails otherwise) | Repository display name, shown in the repo list UI (`RepoAdapter.kt:69`) `[verified]` |
| `description` | `String?` | no | Shown in the generated community list (`CSREPOS/create_markdown.py:19-20`); the app model keeps it but the in-app repo row shows the **URL** as subtitle, not the description (`RepoAdapter.kt:70,109`) `[verified]` |
| `iconUrl` | `String?` | no | Repo icon; shown in repo list with GitHub-logo fallback (`RepoAdapter.kt:71-82,111-122`) `[verified]`. Not in the official repo.json; present in phisher's. Note: the **repo** iconUrl gets **no** `%size%` substitution (unlike plugin iconUrl — §3.3) `[verified]` |
| `manifestVersion` | `Int` (non-null) | **yes** (parse fails otherwise) | Declared for forward compat, **currently unused**: `parseRepository` comment says "Take manifestVersion and such into account later" (RepositoryManager.kt:163) and csdocs says "currently unused, may be used in the future for backwards compatibility" `[docs]` (CSDOCS/devs/create-your-own-json-repository.md:23). The value in the wild is always `1` (both repo.json files read) `[verified]` |
| `pluginLists` | `List<String>` (non-null) | **yes** | URLs of plugins.json files. **All of them are fetched** (parallel `amap` + flatten, RepositoryManager.kt:185-189) — so multiple lists are fully supported and are the intended way to aggregate `[verified]`. The official guide's template shows exactly one `[docs]` (CSDOCS/devs/create-your-own-json-repository.md:15-17) |

Rules:
- **Single vs multiple pluginLists**: multiple is allowed and merged by the app (see above). Real-world repos use exactly 1 (official: `EXT/repo.json:5-7`; phisher: `PHISHER/repo.json:6-8`) `[verified]`.
- **Where hosted**: anywhere a plain `GET` returns the JSON (GitHub raw, GitLab raw, Gitea raw, any static host — see §6). No host allowlist, no extension check on the URL (registry contains `CS.json` and even an extension-less `repo` filename — `CSREPOS/repos-db.json:11,16`) `[verified]`.
- **Parsing**: `parsedSafe<Repository>` — any parse failure (missing non-null field, bad JSON) → `null` → "No repository found" toast in the add-repo UI (`ExtensionsFragment.kt:298-301`). This nullability is the *only* de-facto schema validation the app performs `[verified]` `[inferred]`.
- `manifestVersion` is a *repo* index version, unrelated to the plugin-entry `apiVersion` (§3.2).

---

## 3. The `plugins.json` entry format

`plugins.json` is a **JSON array** of plugin entries. The app parses it with
`parsed<Array<SitePlugin>>().toList()` (RepositoryManager.kt:173) — a non-array (or any error)
yields an empty list, never a crash `[verified]`. Unknown keys are ignored (kotlinx/Jackson
deserialization ignores extras — which is why `jarUrl`/`jarHash`/`jarFileSize` in the wild are silently
dropped, see §3.4) `[inferred]`.

### 3.1 The app-side model — `SitePlugin` (`APP/plugins/RepositoryManager.kt:42-76`)

Quoted in full, comments included — the comments are the official field documentation:

```kotlin
/**
 * Status int as the following:
 * 0: Down
 * 1: Ok
 * 2: Slow
 * 3: Beta only
 */
@Serializable
data class SitePlugin(
    // Url to the .cs3 file
    @JsonProperty("url") @SerialName("url") val url: String,
    // Status to remotely disable the provider
    @JsonProperty("status") @SerialName("status") val status: Int,
    // Integer over 0, any change of this will trigger an auto update
    @JsonProperty("version") @SerialName("version") val version: Int,
    // Unused currently, used to make the api backwards compatible?
    // Set to 1
    @JsonProperty("apiVersion") @SerialName("apiVersion") val apiVersion: Int,
    // Name to be shown in app
    @JsonProperty("name") @SerialName("name") val name: String,
    // Name to be referenced internally. Separate to make name and url changes possible
    @JsonProperty("internalName") @SerialName("internalName") val internalName: String,
    @JsonProperty("authors") @SerialName("authors") val authors: List<String>,
    @JsonProperty("description") @SerialName("description") val description: String?,
    // Might be used to go directly to the plugin repo in the future
    @JsonProperty("repositoryUrl") @SerialName("repositoryUrl") val repositoryUrl: String?,
    // These types are yet to be mapped and used, ignore for now
    @JsonProperty("tvTypes") @SerialName("tvTypes") val tvTypes: List<String>?,
    // Most often a language tag like "en" or "zh-TW"
    @JsonProperty("language") @SerialName("language") val language: String?,
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String?,
    // Automatically generated by the gradle plugin
    @JsonProperty("fileSize") @SerialName("fileSize") val fileSize: Long?,
    @JsonProperty("fileHash") @SerialName("fileHash") val fileHash: String?,
)
```

### 3.2 Field-by-field semantics

| Field | Type | Req. | Semantics (with evidence) |
|---|---|---|---|
| `url` | `String` | **yes** | **Direct URL to the `.cs3` file** (comment: "Url to the .cs3 file", RepositoryManager.kt:51-52). Must be non-blank to be auto-downloadable (blank → skipped, PluginManager.kt:374-376). For **local** plugins the app reuses this field to carry a *file path* instead ("On local plugins page the filepath is provided instead of url", PluginAdapter.kt:120-121) `[verified]` |
| `status` | `Int` | **yes** | Provider lifecycle / kill-switch. `0=Down, 1=Ok, 2=Slow, 3=Beta only` (RepositoryManager.kt:42-48). Constants: `PROVIDER_STATUS_BETA_ONLY=3, PROVIDER_STATUS_SLOW=2, PROVIDER_STATUS_OK=1, PROVIDER_STATUS_DOWN=0` (LIB/MainAPI.kt:391-395); an older library comment adds flavour: "0 = Site not good, 1 = All good, 2 = Slow, heavy traffic, 3 = restricted, must donate 30 benenes to use" (MainAPI.kt:385-390). **Only `0` has behavioral effect** — see §3.5. All 4 values are rendered as text ("Down/Ok/Slow/Beta") in the plugin details sheet via `R.array.extension_statuses` (PluginDetailsFragment.kt:75-76; RES/values/array.xml:329-335) `[verified]` |
| `version` | `Int` | **yes** | Monotonic integer; **higher = newer**; "Integer over 0, any change of this will trigger an auto update" (RepositoryManager.kt:55-56). Update predicate: `onlineData.plugin.version > savedData.version \|\| onlineData.plugin.version == PLUGIN_VERSION_ALWAYS_UPDATE(-1)` (PluginManager.kt:229-230). Stored per-plugin in DataStore on load (the value is refreshed from the plugin's own internal `manifest.json` `version` at load time, PluginManager.kt:626-637). Special values: `PLUGIN_VERSION_ALWAYS_UPDATE = -1` (always update), `PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE` (placeholder, PluginManager.kt:108-112) `[verified]` |
| `apiVersion` | `Int` | **yes** | **Unused at runtime.** App comment: "Unused currently, used to make the api backwards compatible? Set to 1" (RepositoryManager.kt:57-59) — the only reference to apiVersion in the entire app+library source is this model field (grep-verified; also confirmed by B1-a). The *generator* hardcodes it as a `val` (cannot be overridden by plugin devs): `val apiVersion = 1` (GRADLE/CloudstreamExtension.kt:11). Compat is instead enforced at build time via Kotlin ABI validation in the library (see doc 01 §distribution) `[verified]` |
| `name` | `String` | **yes** | Display name ("Name to be shown in app", RepositoryManager.kt:60-61). UI strips a trailing `"Provider"` suffix when rendering (`name.removeSuffix("Provider")`, PluginAdapter.kt:79; PluginDetailsFragment.kt:63) `[verified]` |
| `internalName` | `String` | **yes** | Stable identity key: "Name to be referenced internally. Separate to make name and url changes possible" (RepositoryManager.kt:62-63). It (a) keys the installed file name, (b) matches installed↔online during update checks (PluginManager.kt:291), (c) resolves an API name to a plugin (`internalName.replace("provider","",ignoreCase=true) == apiName`, PluginManager.kt:246-250). Generator always sets `internalName = name` (GRADLE/WriteCacheEntryTask.kt:61-62), and in the wild `name == internalName` for **all 85** entries checked (5 official + 80 phisher, python census) — the separation exists in the protocol but is unused today `[verified]` |
| `authors` | `List<String>` | **yes** (non-null list; may be empty) | Author names; joined with ", " in the details sheet (PluginDetailsFragment.kt:71-74). Generator default: empty list (GRADLE/CloudstreamExtension.kt:83) `[verified]` |
| `description` | `String?` | no | One-liner shown as row subtitle (HTML-rendered, PluginAdapter.kt:206-207) and in the details sheet (PluginDetailsFragment.kt:65); also used in fuzzy search matching (Levenshtein, first 64 chars, PluginsViewModel.kt:256-263) `[verified]` |
| `repositoryUrl` | `String?` | no | **Human-facing source link** ("Might be used to go directly to the plugin repo in the future", RepositoryManager.kt:66-67). Actually already used: details-sheet GitHub button opens it in a browser (PluginDetailsFragment.kt:86-90). Plus a **functional gate in auto-download**: entries with null/blank `repositoryUrl` are **never auto-downloaded** (PluginManager.kt:377-379) `[verified]` |
| `tvTypes` | `List<String>?` | no | Category tags matching `TvType` enum names (`"Movie"`, `"TvSeries"`, `"Live"`, `"NSFW"`, `"Others"`, …). App comment is stale ("These types are yet to be mapped and used, ignore for now", RepositoryManager.kt:68-69) — they ARE used: UI filter chips (PluginsFragment.kt:193-204), NSFW gating (PluginsViewModel.kt:212-214; PluginManager.kt:388-398), NSFW marker badge (PluginAdapter.kt:97), display in details (PluginDetailsFragment.kt:77-80). Real values in phisher include a non-enum `"Cartoon"` (PHISHER/plugins.json:18) — unknown values are tolerated as filter-inert strings `[verified]` |
| `language` | `String?` | no | BCP-47-ish language tag ("Most often a language tag like 'en' or 'zh-TW'", RepositoryManager.kt:70-71). Real values seen: `en, hi, de, id, zh, bn, mx, ta, te, fr, pt-br, ko, fil` (phisher census). Used for language filter chips ("none" bucket for missing, PluginsViewModel.kt:39-52,235-243), flag-emoji rendering (PluginAdapter.kt:165-170), and `AutoDownloadMode.FilterByLang` matching (PluginManager.kt:400-408). Official repo omits it entirely (5/5 entries) `[verified]` |
| `iconUrl` | `String?` | no | Plugin icon URL; supports `%size%` / `%exact_size%` placeholders (§3.3) `[verified]` |
| `fileSize` | `Long?` | no | Byte size of the `.cs3` (comment "Automatically generated by the gradle plugin", RepositoryManager.kt:73-74). Display-only (`formatShortFileSize`, PluginAdapter.kt:192-197; details sheet PluginDetailsFragment.kt:66-70) — never used as a download precondition `[verified]` |
| `fileHash` | `String?` | no | SHA-256 of the `.cs3` in the exact format `"sha256-<64 lowercase hex>"` (generator: GRADLE/WriteCacheEntryTask.kt:85-97; app verifier: RepositoryManager.kt:107-122). **Verified on every repo download** (§7). `null` → download proceeds **unverified** (only-if-non-null check, RepositoryManager.kt:214) `[verified]` |

### 3.3 The `iconUrl` `%size%` placeholder mechanism

Plugin icons can carry two placeholders the app substitutes at render time with the wanted pixel size:

```kotlin
val url = metadata.iconUrl?.replace(
    "%size%",
    "$iconSize"
)?.replace(
    "%exact_size%",
    "$iconSizeExact"
)
```
`[verified]` — PluginAdapter.kt:146-152 (list rows); identical logic in PluginDetailsFragment.kt:59-60 (details sheet).

- `%size%` → a **power-of-two-snapped** size: `findClosestBase2(target, 16, 512)` where target = `32.toPx` in the list (PluginAdapter.kt:215-219,232-235) and `50.toPx` in the details sheet (PluginDetailsFragment.kt:36-45) — i.e. typically 32→32, 50→64.
- `%exact_size%` → the exact px value (`32` / `50`).
- The canonical use is Google's favicon service: `https://www.google.com/s2/favicons?domain=<host>&sz=%size%` (all 5 official entries, fetched plugins.json) — favicons scaled server-side by URL parameter `[verified-net]`. Non-placeholder URLs (e.g. phisher's raw PNG icons, PHISHER/plugins.json:20) pass through unchanged `[verified]`.

### 3.4 The `jar*` fields — and who actually consumes them

The wild JSON contains three fields the **Android app model does not have**: `jarUrl`, `jarHash`,
`jarFileSize` (SitePlugin has no such fields — RepositoryManager.kt:50-76; also grep: zero `jarUrl`
references anywhere under `APP/` or `LIB/`). This confirms B1-b's finding and closes its open item:

- **Producer** (who writes them): the official gradle plugin. Its generator model explicitly carries
  them under a comment "For cross-platform" (GRADLE/entities/PluginEntry.kt:19-22) and emits
  `jarFileSize = jar?.length()`, `jarUrl = rawLink("${name}.jar")`, `jarHash = sha256(jar)` — only
  when the plugin built a cross-platform `.jar` (i.e. `isCrossPlatform = true` in the gradle
  `cloudstream{}` block), and with **nulls excluded** from the JSON (GRADLE/tasks/WriteCacheEntryTask.kt:51,72-74,77-82).
  That is why official entries (all 5 cross-platform) always have jar fields, while only 47/80 phisher
  entries do (33 lack them — matches B1-b's `isCrossPlatform` census) `[verified]`.
- **Consumer** (who reads them): **not the Android app** — it only ever downloads `url` (the `.cs3`);
  the `.jar` is the plain-JVM twin for non-Android hosts (the library's KMP jvm/web targets and
  third-party tooling — e.g. desktop/web CloudStream ports) `[inferred]`. For ANI-KUTA the `.jar`
  path is irrelevant; we mirror the app and ignore the jar fields.
- Hash format is identical for both (`sha256-<hex>`), so a future consumer gets the same guarantee `[verified]`.

### 3.5 `status` — all values, where each is consumed

| Value | Constant | Meaning | Where consumed in app code |
|---|---|---|---|
| `0` | `PROVIDER_STATUS_DOWN` | **Kill-switch / remote disable** | `OnlinePluginData.isDisabled` (PluginManager.kt:231) → auto-update **unloads** it (PluginManager.kt:306-308), manual update unloads it (PluginManager.kt:855-860); installing/downloading it downloads **without loading** (`loadPlugin = status != DOWN` — PluginsViewModel.kt:133,177); list row dims to α=0.6 + "(disabled)" label (PluginAdapter.kt:78-83,199-204) `[verified]` |
| `1` | `PROVIDER_STATUS_OK` | Normal | The only value with full install+load behavior; default in both real plugins.json files (84/85 entries; the one exception is a single phisher entry with `status: 2`) `[verified]` |
| `2` | `PROVIDER_STATUS_SLOW` | Slow site | **Display-only**: indexed into the status string array (PluginDetailsFragment.kt:75-76). No behavioral difference from 1 `[verified — no other consumer found by grep]` |
| `3` | `PROVIDER_STATUS_BETA_ONLY` | Beta | Display-only (same array). Note the *gradle* extension's default is `status = 3` (GRADLE/CloudstreamExtension.kt:84) — new plugins are Beta until the dev sets 1; both repos' entries ship 1 because devs overrode it `[verified]` |

Kill-switch effect summary: setting `status: 0` in plugins.json does **not** uninstall anything — at the
next update check the plugin's registered APIs/providers are removed from memory (`unloadPlugin`,
PluginManager.kt:689-731 removes API mappings) and it will not be loaded again; the file stays on disk.
`[verified]`

### 3.6 Generator-side notes (how plugins.json is produced)

For completeness (full build pipeline in doc 02): the gradle plugin writes **one JSON fragment per
plugin** (cache entry, WriteCacheEntryTask.kt:77-82) and `makePluginsJson` concatenates all fragments
into the final array file (GRADLE/tasks/MakePluginsJsonTask.kt:27-37; task registered at root:
GRADLE/tasks/Tasks.kt:17-18). Entry `url` is derived from a repo raw-link template
`{user}/{repo}/%branch%/{filename}` (github default, GRADLE/CloudstreamExtension.kt:32; wiring
Tasks.kt:185-186). The build branch defaults to `"builds"` (GRADLE/CloudstreamExtension.kt:19). CI then
force-pushes the artifacts to that branch, which `repo.json.pluginLists` points at. `[verified]`

---

## 4. App-side repository management

### 4.1 Storage keys (exact DataStore key names)

| Key | Literal | Type | Written by | Read by |
|---|---|---|---|---|
| `REPOSITORIES_KEY` | `"REPOSITORIES_KEY"` | `Array<RepositoryData>` (iconUrl?, name, url) | `addRepository`/`removeRepository` (RepositoryManager.kt:252,266) | repo list everywhere (RepositoryManager.kt:243; ExtensionsViewModel.kt:54,89; PluginManager.kt:281,359,838) `[verified]` — declared at `APP/ui/settings/extensions/ExtensionsViewModel.kt:30` |
| `PREBUILT_REPOSITORIES` | `"PREBUILT_REPOSITORIES"` | `Array<RepositoryData>` | **nothing in current source** (grep: only `getKey`, RepositoryManager.kt:100-102) | appended after user repos in every repo aggregation | 
| `PLUGINS_KEY` | `"PLUGINS_KEY"` | `Array<PluginData>` (internalName, url?, isOnline=true, filePath, version) | install/update (PluginManager.kt:125-136) | loader/update checker `[verified]` — declared at PluginManager.kt:70 |
| `PLUGINS_KEY_LOCAL` | `"PLUGINS_KEY_LOCAL"` | `Array<PluginData>` (side-loaded) | local install (PluginManager.kt:133) | loader `[verified]` — PluginManager.kt:71 |
| jsDelivr toggle | `"jsdelivr_proxy_key"` | `Boolean` | first-run connectivity probe + settings toggle (MainActivity.kt:1328-1342; SettingsGeneral.kt:365-368) | `convertRawGitUrl` (RepositoryManager.kt:126) `[verified]` — literal at RES/values/donottranslate-strings.xml:50 |
| auto-update toggle | `"auto_update_plugins"` | `Boolean` (default **true**) | settings | startup branch (MainActivity.kt:1359-1369) `[verified]` — donottranslate-strings.xml:8 |
| auto-download mode | `"auto_download_plugins_key2"` | `Int` (`AutoDownloadMode`) | settings | startup (MainActivity.kt:1372-1383) `[verified]` — donottranslate-strings.xml:9 |

`PREBUILT_REPOSITORIES` is a vestigial mechanism: read via `getKey(...) ?: emptyArray()` and **never
written anywhere in the current codebase** (grep across `app/src/main` finds only the single getKey at
RepositoryManager.kt:101) → the app effectively ships with **zero bundled repos**, matching the
docs posture (doc 01 §legality: no built-in providers) `[verified]`.

The in-memory plugin-list cache is a plain singleton map keyed by repo URL, with no TTL of its own
(`PluginsViewModel.repositoryCache`, PluginsViewModel.kt:63,79-87) — freshness comes from the
5-minute HTTP cache in §1 `[verified]`.

### 4.2 Adding a repository — every entry path

**(a) Settings UI** (`Settings → Extensions`, `APP/ui/settings/extensions/ExtensionsFragment.kt`):
1. FAB "Add repository" opens a dialog with two inputs — repo URL + optional name
   (`addRepositoryClick`, ExtensionsFragment.kt:255-263). If the clipboard holds text of the form
   `"<name> : <url>"` (the share format repos copy to clipboard — `SHAREABLE_REPO_SEPARATOR = " : "`,
   RepoAdapter.kt:129, set by long-press on a repo row, RepoAdapter.kt:102-107) both inputs are
   pre-filled (ExtensionsFragment.kt:264-278) `[verified]`.
2. `RepositoryManager.parseRepoUrl(input)` normalizes it (ExtensionsFragment.kt:290):
   - direct `http(s)://…` → used as-is;
   - `cloudstreamrepo://…` or `https://cs.repo/?…` prefix → stripped, `https://` prepended if no scheme remains (RepositoryManager.kt:136-141);
   - a bare shortcode matching `^[a-zA-Z0-9!_-]+$` → resolved via a URL shortener: `!code` → `https://py.md/<code>`, plain `code` → `https://cutt.ly/<code>`, following the `Location` header (RepositoryManager.kt:142-157);
   - anything else → null → "Invalid data" toast `[verified]`.
3. `parseRepository(url)` fetches repo.json (5-min cache) and parses it; failure → "No repository found" toast (ExtensionsFragment.kt:295-301) `[verified]`.
4. `RepositoryData(repository.iconUrl, fixedName, url)` is stored via `addRepository` — Mutex-guarded, dedup `distinctBy { it.url }` (ExtensionsFragment.kt:303-306; RepositoryManager.kt:248-254). The name defaults to repo.json's `name`; a user-typed name wins `[verified]`.
5. `getRepoPlugins` is tried; empty → "No plugins found" toast; otherwise a confirmation dialog "Open repository / Dismiss" (ExtensionsFragment.kt:312-320; `addRepositoryDialog`, APP/utils/AppContextUtils.kt:543-571) `[verified]`.

**(b) Deep link `cloudstreamrepo://`** — manifest intent-filter (AndroidManifest.xml:195-202). Handler
in `MainActivity.handleAppIntentUrl`: `str.replaceFirst("cloudstreamrepo", "https")` → `loadRepository(url)`
(MainActivity.kt:337-340). `loadRepository` = parse + add + toast + `afterRepositoryLoadedEvent` + the
same confirmation dialog (AppContextUtils.kt:523-541) `[verified]`. This is the one-tap install path
used by the community list (§5.3) and by csdocs links (e.g. CSDOCS/Integrations/stemiorelated.md:15:
`cloudstreamrepo://raw.githubusercontent.com/recloudstream/cloudstream-extensions/builds/repo.json`) `[docs]`.

**(c) Short link `https://cs.repo/?…`** — `https://cs.repo` + `?` + real URL → `"https://" + str.substringAfter("?")` → `loadRepository` (MainActivity.kt:294-298) `[verified]`.

**(d) In-app WebView bridge** — any page opened in the app's WebView can call the injected JS
interface `RepoApi.installRepo(repoUrl)` (WebviewFragment.kt:47,64-69); navigations to repo-intent
URLs are also intercepted via `shouldOverrideUrlLoading` → `handleAppIntentUrl` (WebviewFragment.kt:28-42) `[verified]`.

**(e) First-run setup** — `SetupFragmentExtensions` lists repos (user + prebuilt) with download
buttons to batch-install (SetupFragmentExtensions.kt:50-68) `[verified]`.

### 4.3 How plugins are listed / browsed

- Repo list screen: `RepoAdapter` rows = icon + name + **URL** subtitle; click → per-repo plugin
  screen; delete icon (hidden for prebuilt) → confirm dialog → `removeRepository` (ExtensionsFragment.kt:116-150) `[verified]`.
- Per-repo plugin screen (`PluginsFragment`): toolbar with search + language filter + (debug-only)
  "download all"; TvType chip row filters; NSFW entries hidden unless the NSFW media-type setting is
  enabled (PluginsFragment.kt:79-205; filter at PluginsViewModel.kt:212-214). Search is Levenshtein
  fuzzy over name then description (score > 80), sorted by score (PluginsViewModel.kt:245-269) `[verified]`.
- Plugin rows (`PluginAdapter`): icon (%size%-substituted), `v<version>`, language flag, NSFW marker,
  file size, action button = download-or-delete toggle, tap → details bottom sheet (votes UI is
  disabled — "the vote api is down", PluginAdapter.kt:174-190) `[verified]`.
- Top-level Extensions screen also aggregates all repos into stats (total/downloaded/disabled/not
  downloaded) for a progress bar (ExtensionsViewModel.kt:53-87) and shows downloaded plugins across
  all repos with repository names (`showRepositoryNames = true`, ExtensionsFragment.kt:199-203) `[verified]`.
- Aggregation dedup rule everywhere: **`distinctBy { it.plugin.url }`** (e.g. PluginManager.kt:286,363,842) — the same plugin URL listed by two repos collapses to one; but the same `internalName` from two different repos is **two separate installs** (paths are salted per repo, §4.4) `[verified]`.

### 4.4 Plugin install flow from a repo (download → storage → verification → load)

1. User taps download → `PluginsViewModel.handlePluginAction` (toggle semantics: file exists → delete; else download) (PluginsViewModel.kt:157-201) `[verified]`.
2. `PluginManager.downloadPlugin(activity, url, fileHash, internalName, repositoryUrl, loadPlugin)` — two overloads: one resolves the target path from (internalName, repositoryUrl), one takes an explicit file (PluginManager.kt:757-767) `[verified]`.
3. Target path: `getPluginPath(context, internalName, repositoryUrl)` = `filesDir/Extensions/<sanitized(repoUrl) + "." + repoUrl.hashCode()>/<sanitized(internalName) + "." + internalName.hashCode()>.cs3` (PluginManager.kt:747-755; sanitizer at :737-742; comment: "This should not be changed as it is used to also detect if a plugin is installed!"). **The repo URL salt is what allows the same plugin name from multiple repos** (comment at :779) `[verified]`.
4. `RepositoryManager.downloadPluginToFile`: download to a **temp file in cacheDir**; if `expectedFileHash != null` compute `sha256(tempFile)` and **throw** `IllegalStateException("Extension hash mismatch …")` on mismatch (temp file deleted); then **atomic move** (REPLACE_EXISTING + ATOMIC_MOVE, with non-atomic fallback) into place (RepositoryManager.kt:193-240). Both the URL fetch and the temp file go through `convertRawGitUrl` (jsDelivr proxy if enabled, RepositoryManager.kt:206) `[verified]`.
5. A `PluginData(internalName, url, isOnline = true, filePath, version = NOT_SET)` row is stored; if `loadPlugin` → `unloadPlugin(old path)` then `loadPlugin(...)` (PluginManager.kt:782-800) `[verified]`.
6. `loadPlugin`: file set read-only (Android 14+ dex requirement), `PathClassLoader(filePath, context.classLoader)` **parent-first**, reads `manifest.json` **as a classloader resource**, `loadClass(manifest.pluginClassName).newInstance()`, persists `version` from the internal manifest, optional `requiresResources` → `AssetManager.addAssetPath` trick, registers in `plugins`/`classLoaders`/`urlPlugins` maps, then `load(context)` or `load()` (PluginManager.kt:593-687; full analysis in doc 02) `[verified]`.

Batch installs: "download all" per repo (PluginsFragment.kt:82-84 → `PluginsViewModel.downloadAll`,
PluginsViewModel.kt:92-150) and startup auto-download modes `AutoDownloadMode { Disable(0), FilterByLang(1), All(2), NsfwOnly(3) }` (LIB/MainAPI.kt:1144-1148), which skip blank `url`, blank `repositoryUrl`, existing files, and NSFW/language mismatches (PluginManager.kt:368-418) `[verified]`.

### 4.5 Update detection (how the app decides an update is available)

There is **no version in repo.json and no separate update-index** — the check compares plugins.json
entries against the locally stored `PluginData`:

```kotlin
// Helper class for updateAllOnlinePluginsAndLoadThem
data class OnlinePluginData(
    val savedData: PluginData,          // what's installed (DataStore)
    val onlineData: PluginWrapper,      // what the repo offers (plugins.json)
) {
    val isOutdated =
        onlineData.plugin.version > savedData.version || onlineData.plugin.version == PLUGIN_VERSION_ALWAYS_UPDATE
    val isDisabled = onlineData.plugin.status == PROVIDER_STATUS_DOWN

    fun validOnlineData(context: Context): Boolean {
        return getPluginPath(
            context,
            savedData.internalName,
            onlineData.repositoryData.url
        ).absolutePath == savedData.filePath
    }
}
```
`[verified]` — PluginManager.kt:224-240.

The full startup check (`___DO_NOT_CALL_FROM_A_PLUGIN_updateAllOnlinePluginsAndLoadThem`,
PluginManager.kt:274-338, gated by the `auto_update_plugins` setting, default true —
MainActivity.kt:1358-1369):

1. Load all already-installed online plugins **first** (fast start), :278-279.
2. Fetch repos + all pluginLists, flatten, `distinctBy { plugin.url }`, :281-286.
3. Pair each installed plugin with online entries by **`internalName`** (not URL), keep only pairs
   whose stored file path equals the expected repo-salted path (`validOnlineData` — catches renamed
   files / moved repos), dedup by online URL, :288-297.
4. For each pair: `isDisabled` → `unloadPlugin` (kill-switch); else `isOutdated` → re-download
   (same hash-verified path) + reload, :305-322.
5. Post a silent low-priority notification listing updated plugin names, :324-330.

Key semantics:
- **Higher `version` = newer; equality = no update.** There is no downgrade protection — a *lower*
  remote version is simply ignored (`>` comparison), so version rollback is impossible via repos
  (the manual "check for updates" path below ignores this and force-reinstalls) `[verified]` `[inferred]`.
- `version == -1` (`PLUGIN_VERSION_ALWAYS_UPDATE`) forces an update on every check — an escape hatch for repos (PluginManager.kt:111-112,229-230) `[verified]`.
- The installed version is not taken from plugins.json but refreshed from the plugin's own internal
  `manifest.json` on every load (`setPluginData(data.copy(version = version))`, PluginManager.kt:626-637) `[verified]`.
- Hashes are **not** consulted for update detection — only for download integrity (§7).
- **Manual update**: settings button `manual_update_plugins` → `___DO_NOT_CALL_FROM_A_PLUGIN_manuallyReloadAndUpdatePlugins`
  (SettingsUpdates.kt:279-284; PluginManager.kt:830-899) — this one **deletes and re-downloads every
  valid online plugin regardless of version** (delete existing file first, :862-863) and unloads
  disabled ones. I.e. "check for updates" is really "reinstall everything from repos" `[verified]`.

### 4.6 Uninstall & repository deletion

- Uninstall a plugin = the same toggle: file exists → `PluginManager.deletePlugin(file)` — deletes the
  file, unloads it (removes its registered APIs/providers/extractors), deletes its `PluginData` rows
  (PluginManager.kt:807-821; unload internals :689-731) `[verified]`.
- Delete a repository: confirm dialog warns plugins will be deleted too (string
  `delete_repository_plugins`, ExtensionsFragment.kt:144-148) → `removeRepository`: removes the
  `REPOSITORIES_KEY` row (by URL), unloads every plugin file under `filesDir/Extensions/<repoSalt>/`,
  then `deleteRepositoryData` deletes all `PluginData` rows whose path contains the repo folder and
  `deleteRecursively()` the folder (RepositoryManager.kt:259-284; PluginManager.kt:151-162) `[verified]`.
- A related hygiene step: `deleteAllOatFiles` wipes generated `oat` dirs under Extensions to recover
  from SIGSEGV after app updates (PluginManager.kt:164-175) `[verified]`.

### 4.7 Safe mode (the `safe` file)

- `checkSafeModeFile()`: true if **any file named `safe` (case-insensitive) exists in
  `<externalStorage>/Cloudstream3/`** (PluginManager.kt:575-588; folder constant :186-187) `[verified]`.
- `isSafeMode() = checkSafeModeFile() || lastError != null` — i.e. the file, **or** the app crashed
  during the previous plugin load (PluginManager.kt:570-573) `[verified]`.
- Effect at startup: safe-mode toast shown and **the entire plugin load/update pipeline is skipped**
  (MainActivity.kt:1346-1356 `if (PluginManager.checkSafeModeFile()) … else …`) — the user removes the
  file (or the failing plugin) to recover. A kill switch of last resort owned by the user, not the repo `[verified]`.

### 4.8 jsDelivr proxy (GitHub-raw resilience)

- Setting: `jsdelivr_proxy_key` (toggle in General settings, RES/xml/settings_general.xml:75).
- First-run auto-probe: `checkGithubConnectivity()` GETs
  `https://raw.githubusercontent.com/recloudstream/.github/master/connectivitycheck` (timeout 5 s,
  expects body `"ok"`); failure → proxy auto-enabled with a reversible snackbar (MainActivity.kt:1327-1342,2066-2074) `[verified]`.
- When enabled, every `https://raw.githubusercontent.com/<user>/<repo>/<rest>` URL used for
  **repo.json, plugins.json and .cs3 downloads** is rewritten to `https://cdn.jsdelivr.net/gh/<user>/<repo>@<rest>`
  (regex + rewrite at RepositoryManager.kt:103-130; applied at :164,172,206) `[verified]`.

---

## 5. The community repo ecosystem (`cs-repos`)

`recloudstream/cs-repos` is the community **registry** — a DB of repo.json URLs plus a CI validator
plus a generated human-readable list. The app does **not** read this registry; it exists purely as a
discovery/trust surface for humans `[verified — no reference to cs-repos in APP/ source; inferred role]`.

### 5.1 `repos-db.json` — the registry

26 entries; **25 plain URL strings + 1 object** (`CSREPOS/repos-db.json:1-31`):

```json
[
    {
        "url": "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
        "verified": true
    },
    "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
    …24 more plain strings…
]
```

- **`verified: true` exists only on the official repo entry** (repos-db.json:2-5). It is a pure
  marker: `ci_check.py` and `create_markdown.py` never read it (grep-verified — it appears exactly
  once in the whole repo). The rendered list's warning text carries the semantics instead: "The first
  repo is constantly audited by the app developers so you can probably trust it" (create_markdown.py:5) `[verified]`.
- File supports `//` line comments (custom `JSONWithCommentsDecoder` strips them before parsing, ci_check.py:9-15) `[verified]`.
- Entries are a string **or** an object with a `url` key; empty url → validation error (ci_check.py:43-48) `[verified]`.

### 5.2 What `ci_check.py` enforces (the full rule list)

Read in full (CSREPOS/ci_check.py, 66 lines). For every repo in the DB, concurrently:

1. **DB entry shape** — string, or object with non-empty `url` (ci_check.py:43-48).
2. **repo.json fetch** — `GET <url>` with `follow_redirects=True`; must return parseable JSON (ci_check.py:28-37).
3. **`name` must be truthy** — `assert data['name']` (ci_check.py:32).
4. **`manifestVersion` must be truthy** — `assert data['manifestVersion']` (ci_check.py:33). (So `manifestVersion: 0` would fail CI even though the app would accept it.)
5. **Every pluginLists URL** must return parseable JSON (ci_check.py:34,21-24).
6. **Every plugin entry's `url` must HEAD-respond HTTP 200** (ci_check.py:17-19) — i.e. every `.cs3` file in every listed plugin list must be live.
7. **Failure semantics** — any exception is collected; a non-empty error list → `SystemExit(1)` (CI red) and **no list regeneration**; only a fully green run calls `write_markdown` (ci_check.py:56-65).

Notable non-rules (as relevant as the rules): **no https enforcement**, **no hash validation**, **no
status/version/language checks**, **no field-completeness checks** beyond `name`/`manifestVersion`, and
redirects are followed `[verified]`.

### 5.3 How repos get added / published

- Mechanism: open a PR adding your repo.json URL to `repos-db.json` — CI (`check_repos.yml`, runs on
  push + PR) executes `ci_check.py`; on merge to the default branch an extra step publishes the
  regenerated `list.md` to **rentry.org/cs3-repos** using a stored rentry password secret
  (`CSREPOS/.github/workflows/check_repos.yml`, steps "Run checks" + "Update Rentry") `[verified]`.
- `list.md` is generated by `create_markdown.py`: one note-block per repo —
  `[<name>](cloudstreamrepo://<repo.json url>)` + description, preceded by the arbitrary-code-execution
  warning (create_markdown.py:1-21). Clicking a repo name in that web list fires the app's deep link
  (§4.2b) `[verified]`.
- The user-facing docs no longer host the list themselves — "See the CloudStream Wiki page for an up
  to date list of extensions" (CSDOCS/Repositories.md:7) `[docs]`.

---

## 6. Hosting patterns (what the ecosystem actually does)

From the 26 registry entries (`CSREPOS/repos-db.json`, census computed by this agent):

| Dimension | Breakdown |
|---|---|
| Host | **24/26 `raw.githubusercontent.com`**, 1 `gitlab.com/<u>/<r>/-/raw/<branch>/repo.json` (repos-db.json:19), 1 self-hosted Gitea `git.disroot.org/<u>/<r>/raw/branch/<branch>/repo.json` (repos-db.json:12) `[verified]` |
| Branch (GitHub entries) | 11 use the explicit `refs/heads/<branch>` path form, 7 use the short `/<branch>/` form, 6 `master` — i.e. **the "artifacts on a dedicated `builds` branch" pattern dominates but is not universal**; `master`/`main` also occur `[verified]` |
| Index filename | 24/26 `repo.json`; exceptions: `CS.json` (repos-db.json:11) and an extension-less `repo` (repos-db.json:16) — the app never inspects the filename, only the URL `[verified]` |
| Two URL spellings for the same host | `…/extensions/builds/plugins.json` (official repo.json:6) vs `…/cloudstream-extensions-phisher/refs/heads/builds/plugins.json` (PHISHER/repo.json:7) — both resolve identically on raw.githubusercontent.com `[verified]` |

Generator-side support: the gradle plugin's `setRepo` knows raw-link templates for **github, gitlab,
codeberg, `gitlab-<domain>` and `gitea-<domain>`** (e.g. github:
`https://raw.githubusercontent.com/{user}/{repo}/%branch%/%filename%`; gitlab:
`https://gitlab.com/{user}/{repo}/-/raw/%branch%/%filename%`) — GRADLE/CloudstreamExtension.kt:26-45.
So the officially-blessed hosting set is *any git host with a raw-file URL*; GitHub is simply where
the ecosystem lives. Client-side resilience for GitHub-blocked regions is the jsDelivr rewrite (§4.8),
and the app accepts literally any `http(s)` host for repo.json/plugins.json/.cs3 (no allowlist —
`parseRepoUrl`/`downloadPluginToFile` accept any URL) `[verified]`.

---

## 7. Security model

1. **Hash verification at download time only.** Every repo-mediated `.cs3` download is SHA-256
   verified against plugins.json's `fileHash` (format `sha256-<hex>`), computed streamingly over the
   temp file, **throwing** on mismatch so a corrupted/truncated/poisoned download never reaches disk
   (RepositoryManager.kt:107-122,214-220). Caveats: if `fileHash` is null/absent the download proceeds
   **unverified** (only-if-non-null check); **side-loaded local plugins are never hashed**
   ("No file hash for local plugins… expensive to compute", PluginManager.kt:102-103); the hash binds
   file↔repo-listing, **not** repo↔trust — the repo author writes the hash, so this is integrity,
   not authenticity `[verified]`.
2. **Manual, per-repo opt-in trust.** No repo ships with the app (`PREBUILT_REPOSITORIES` is never
   populated — §4.1); every repo is user-added via URL/deep-link. The registry's own preamble states
   the threat model plainly: extensions "can execute arbitrary code inside the app… treat them with
   the same level of scrutiny you treat any apps. Extensions can also read all of the Cloudstream's
   data" (CSREPOS/create_markdown.py:3-5) `[verified]`.
3. **No transport guarantee.** No https-only check exists anywhere in the chain (`parseRepoUrl`
   accepts `http://` too, RepositoryManager.kt:134-135); no certificate pinning for repo hosts. With
   https the hash check closes the loop; with plain http both index and binary are tamperable
   (though the hash would still catch a binary mismatch unless the index is also tampered) `[verified]` `[inferred]`.
4. **`status: 0` is the remote kill-switch** — repo-side disable of an installed plugin: unload at
   next update check + never load again (§3.5). It is the only repo-writable field with runtime
   behavioral teeth besides `version` `[verified]`.
5. **Code-level trust = classloader parent-first + safe mode.** Loaded plugins run in-app with full
   app data access (no sandbox — the preamble quote above), but cannot shadow host classes
   (parent-first `PathClassLoader`, PluginManager.kt:611 — see doc 02), and a crash loop or a
   user-created `safe` file disables all loading (§4.7) `[verified]`.

---

## 8. Comparison notes vs our aniyomi repo system (preview — deep dive is doc 14)

Our current system: `ANI-KUTA/APP/ani-kuta/data/extension/src/main/java/com/confused/anikuta/data/extension/repo/`
(`ExtensionRepo.kt`, `ExtensionRepoApi.kt`, `ExtensionRepoRepository.kt`). Preview-level contrast only:

- **Index shape — one level vs two**: ours is a **single-level** `index.json` (list of extension
  entries at `<baseUrl>/index.json`, ExtensionRepo.kt:9-13) with a path-convention layout
  (`/icon/<pkg>.png`, `/apk/<apkName>`); CS3 is **two-level** (repo.json → pluginLists[] → plugins.json),
  with every artifact URL absolute inside the JSON. CS3's indirection supports multi-list aggregation;
  ours supports none (one index per repo) `[verified]`.
- **Entry identity**: ours keys on `pkg` + `apk` filename with `version` as a *string* (`1.4.x`) and
  separate `code: Long` (ExtensionRepoApi.kt:139-152); CS3 keys on `internalName` with a single Int
  `version` and absolute URLs `[verified]`.
- **Compatibility gate**: ours filters entries by **lib version range 12.0–16.0** parsed from the
  version string (ExtensionRepoApi.kt:29-31,88-91,150-151) — the aniyomi `libVersion` contract; CS3's
  analogous `apiVersion` is **dead code at runtime** (§3.2) — compat is a build-time ABI concern `[verified]`.
- **Repo metadata**: ours reads an **optional** `<baseUrl>/repo.json` (name/website only,
  ExtensionRepoApi.kt:93-102) — coincidentally the same filename as CS3's *primary* manifest with a
  completely different schema (`meta.name`/`meta.website` vs top-level `name`/`pluginLists`) — a
  naming collision our integration must not confuse `[verified]`.
- **Storage**: ours persists repos in SharedPreferences (`anikuta_extension_repos`/`repos_json`,
  ExtensionRepoRepository.kt:26-27) as a StateFlow-exposed list; CS3 uses its generic DataStore
  (`REPOSITORIES_KEY`) `[verified]`.
- **Trust/verification**: ours gates adds through `verifyRepo` (fetch + parse + lib-version filter +
  non-empty check, ExtensionRepoApi.kt:60-106) but has **no hash verification of downloaded APKs**
  (none present in the repo layer); CS3 verifies SHA-256 on every download but validates repo adds
  only by "did repo.json parse" `[verified]`.
- **Updates**: ours compares `code` (analogous integer) in the installer layer (doc 14); CS3 compares
  `version` with `>` + the `-1` always-update escape hatch, and refreshes the installed version from
  the plugin's internal manifest `[verified]`.
- **Repo browsing**: ours has no per-repo browser UI filters equivalent to CS3's TvType chips +
  language filter + fuzzy search; CS3's NSFW gating via `tvTypes` is a ready-made pattern `[verified]` `[inferred]`.
- **No defaults**: both ecosystems ship zero repos (ours by decision D-043, ExtensionRepo.kt:14; CS3
  de-facto, §4.1) `[verified]`.

---

## 9. Quick-reference templates (for implementers)

Minimal valid `repo.json` (every field the app model requires + the one field CI requires):

```json
{
    "name": "My Repo",
    "description": "Optional description",
    "manifestVersion": 1,
    "pluginLists": [
        "https://example.com/plugins.json"
    ]
}
```
(`iconUrl` optional at top level; `manifestVersion` must be a truthy non-null Int or the app's parser
returns null and cs-repos CI fails; `pluginLists` may contain several URLs — all fetched and merged.)

Minimal valid `plugins.json` (one entry — only non-null app-model fields; the jar* trio is optional
and ignored by the Android app):

```json
[
    {
        "url": "https://example.com/MyProvider.cs3",
        "status": 1,
        "version": 1,
        "apiVersion": 1,
        "name": "MyProvider",
        "internalName": "MyProvider",
        "authors": ["Me"],
        "description": "What it provides",
        "repositoryUrl": "https://github.com/me/my-repo",
        "language": "en",
        "tvTypes": ["Movie", "TvSeries"],
        "iconUrl": "https://www.google.com/s2/favicons?domain=example.com&sz=%size%",
        "fileSize": 12345,
        "fileHash": "sha256-<64 lowercase hex of the .cs3 file>"
    }
]
```

Bumping an update = increment `version` (any higher Int) and update `url`/`fileHash`/`fileSize` after
rebuilding. Kill-switch an entry = set `"status": 0`. Force update on every check = `"version": -1`.

Install deep link: `cloudstreamrepo://<full-https-repo.json-url>` (app rewrites the scheme to https).

---

## 10. Could not verify / open items

- **Who consumes `jarUrl` in production**: no consumer exists in the Android app or library
  (grep-verified). The generator's "For cross-platform" comment (GRADLE/entities/PluginEntry.kt:19) is
  the only authoritative hint; the actual JVM/desktop/web consumers (if live) are outside our clones.
  `[inferred]`
- **`AutoDownloadMode` settings UI labels** — the enum values are verified (MainAPI.kt:1144-1148) but
  we did not audit the localized label array (`R.array.auto_download_plugin`) `[not verified]`.
- **The `cs.repo` short-link service itself** — its server-side behavior (redirect vs interstitial) is
  not documented in our sources; only the client handling is verified (MainActivity.kt:294-298) `[verified client-side only]`.
- **cs-repos `verified` flag workflow** — how/whether the maintainers flip it for other repos
  (historically it has only ever been the official repo) `[inferred from repos-db.json state]`.
- **Whether any fork re-enables `PREBUILT_REPOSITORIES`** — in *this* source snapshot it is never
  written; we cannot rule out that release builds inject it via another channel (none found) `[verified absent in repo]`.
- **Vote API** (`VotingApi`) is wired in the details sheet but disabled in the list ("the vote api is
  down", PluginAdapter.kt:176) — out of scope for this doc; noted for doc 13 `[verified]`.
