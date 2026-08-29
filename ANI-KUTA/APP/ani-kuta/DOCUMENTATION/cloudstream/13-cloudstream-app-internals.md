# 13 — CloudStream App Internals: Startup, Orchestration, Persistence & Data Model

> Research doc 13/… of the ANI-KUTA CloudStream (CS3) program. Snapshot: recloudstream/cloudstream @ `efc1915` (shallow clone, 2026-08-28), paths under `research/cloudstream/`. App-module sources under `app/src/main/java/com/lagradost/cloudstream3/` (abbreviated **`APP/`** below); library module under `library/src/commonMain/kotlin/com/lagradost/cloudstream3/` (abbreviated **`LIB/`**).
>
> This is the "how it all hangs together" doc: process startup → plugin orchestration → provider registry → **the complete DataStore key inventory** → favorites/library model → subscriptions/notifications → result caching → accounts/sync → settings IA → crash handling/safe mode → ANI-KUTA mapping preview.
>
> Companion docs (do not re-derive, cited as `doc NN`): **02** plugin format & classloader, **04** repo formats & install flow, **06** home/search driving, **08** link generators & request caches, **09** player events & download pipeline, **10** provider filtering keys, **11** plugin settings & the `rebuild_preference` storage layer.

---

## 0. Headline findings

1. **There is no Application-level plugin loading.** `CloudStreamApp.onCreate()` only installs a crash handler and a debug flag (`CloudStreamApp.kt:72-87`). ALL plugin loading is driven from `MainActivity.onCreate()` in three `ioSafe` blocks (online → auto-download → local), and the *homepage provider* is loaded first and separately (`loadSinglePlugin`) so home renders before the rest finish (`MainActivity.kt:1350-1391`).
2. **"AcraApplication" is a lie**: it's a deprecated 78-line alias shim for `CloudStreamApp` kept for plugin compat; **ACRA itself is gone** — crash handling is a 27-line `ExceptionHandler` that writes `filesDir/last_error` (with the currently-loading extension name!) and relaunches the app (`AcraApplication.kt:12`, `CloudStreamApp.kt:41-68`).
3. **The entire user data model is JSON blobs in one SharedPreferences file** (`rebuild_preference`, doc 11 §3): favorites/bookmarks/subscriptions each store a **full flattened `SearchResponse` snapshot** per item, keyed by a **URL hash id**, in per-account folder keys. No database anywhere (`DataStoreHelper.kt:263-491`).
4. **There is no per-provider enable/disable switch.** "Disabling" a plugin = repo-side `PROVIDER_STATUS_DOWN` → `unloadPlugin()` on next update pass (`PluginManager.kt:305-308`); UI-level "filtering" (lang / preferred media / dub) happens at read time (doc 10 §"filtering"). The `enabledExtensions` DataStore key the brief asked about **does not exist** at this commit.
5. **The library screen is literally a sync-API.** `LocalList : SyncAPI` renders the local DataStore blobs as one more tracker alongside MAL/AniList/Kitsu/Simkl (`LocalList.kt:19-91`) — local and remote lists share one `SyncAPI.Page` rendering path.
6. **Subscriptions = WorkManager, 6 h, network-constrained**, foreground `CoroutineWorker` that force-loads ALL plugins, `api.load()`s each subscribed show with 60 s timeout, and compares `getLatestEpisodes()` vs `lastSeenEpisodeCount` per dub preference (`SubscriptionWorkManager.kt:38-66, 101-224`).
7. Six notification channels, each owned by a dedicated subsystem (`cloudstream3.extensions/subscriptions/backups/general/download.queue/updates` — §6.3).
8. Two caches that matter: the **APIRepository 20-entry / 10-min LoadResponse ring cache** (per-process, force-cleared on plugin reload) (`APIRepository.kt:52-64, 92-117`) and the **download header/episode metadata caches** used to render the Downloads screen without hitting providers (`DownloadManager.kt:1900-1930`, doc 09).
9. Local plugins are **rebuilt from disk every start** — `PLUGINS_KEY_LOCAL` is wiped and re-derived from `/sdcard/Cloudstream3/plugins` (`.zip`/`.cs3`) copied into app-external storage (`PluginManager.kt:530-559`).
10. Crash-loop escape is a **file named `safe` in `/sdcard/Cloudstream3/`** or any previous crash (`last_error` present) → skip all plugin loading and show a dialog (`PluginManager.kt:570-588`, `MainActivity.kt:1346-1411`).

---

## 1. Startup & initialization sequence

### 1.1 Process start → Application.onCreate

`CloudStreamApp` is the `Application` (and a Coil `SingletonImageLoader.Factory`). `onCreate` does exactly two things `[verified]`:

```kotlin
// CloudStreamApp.kt:72-87
override fun onCreate() {
    super.onCreate()
    ExceptionHandler(filesDir.resolve("last_error")) {
        val intent = context!!.packageManager.getLaunchIntentForPackage(context!!.packageName)
        startActivity(Intent.makeRestartActivityTask(intent!!.component))
    }.also {
        exceptionHandler = it
        Thread.setDefaultUncaughtExceptionHandler(it)
    }
    AppDebug.isDebug = BuildConfig.DEBUG
}
```

- `attachBaseContext` stashes the app context into a `WeakReference` + `com.lagradost.api.setContext` (used by multiplatform library code) (`CloudStreamApp.kt:89-92`).
- `newImageLoader` is lazy — Coil builds on first image load (`CloudStreamApp.kt:94-97`).
- The companion object re-exposes `getKey/setKey/removeKey(s)` over the weak context — this is the **plugin-facing storage API** (doc 11 §3) (`CloudStreamApp.kt:99-165`).
- **No plugin loading, no WorkManager, no account init at Application level** `[verified]`. (The old `AcraApplication` name survives only as a `@Deprecated(level = ERROR)` compat shim — `AcraApplication.kt:7-12`.)

### 1.2 MainActivity.onCreate — ordered sequence

`MainActivity` (an `AppCompatActivity`, doc 11 §1) is the real orchestrator. Ordered from `MainActivity.kt:1183-2054` `[verified]`:

| # | Step | Where (file:line) | Notes |
|---|------|-------------------|-------|
| 1 | Init OkHttp clients (`app`/`insecureApp`), the insecure one ignores SSL | `MainActivity.kt:1184-1186` | |
| 2 | `setLastError(this)` — read `filesDir/last_error` if previous run crashed; deletes the file | `MainActivity.kt:1190`; impl `MainActivity.kt:208-218` | Sets static `MainActivity.lastError` used by safe mode |
| 3 | Push NSFW pref into `MainAPI.settingsForProvider` (global static read by providers + auto-download) | `MainActivity.kt:1192-1196` | Key `enable_nsfw_on_providers_key`, doc 10 §4 |
| 4 | `loadThemes`, edge-to-edge, locale, `super.onCreate`, Cast session manager | `MainActivity.kt:1198-1210` | |
| 5 | **App-version backup**: on version change write `VERSION_NAME` key → `backup(this)` + `PluginManager.deleteAllOatFiles` (recompiles dex on app update to dodge stale-oat SIGSEGV) | `MainActivity.kt:1215-1231`; `PluginManager.kt:168-175` | Auto-backup paranoia: "I don't trust myself to not boot lock users" |
| 6 | Inflate TV/phone binding **inside try/catch** — inflate failure = toast, `binding = null`, app continues (never boot-loop) | `MainActivity.kt:1233-1284` | |
| 7 | Biometric gate (phone layout, no tracker accounts, PIN lock set) | `MainActivity.kt:1308-1325` | |
| 8 | jsDelivr proxy auto-probe if GitHub raw unreachable | `MainActivity.kt:1327-1342` | |
| 9 | `SafeFile.check` (storage perms) | `MainActivity.kt:1344` | |
| 10 | **Safe-mode branch** (see §10): if `PluginManager.checkSafeModeFile()` OR `lastError != null` → skip ALL plugin loading, show crash/safe dialog | `MainActivity.kt:1346-1411` | |
| 11 | (else) `ioSafe`: `loadSinglePlugin(currentHomePage)` → fires `mainPluginsLoadedEvent` so Home can render ASAP | `MainActivity.kt:1351-1356`; `PluginManager.kt:244-259` | Home provider first — time-to-first-home optimization |
| 12 | `ioSafe`: if `auto_update_plugins_key` (default **true**) → `___DO_NOT_CALL_FROM_A_PLUGIN_updateAllOnlinePluginsAndLoadThem` else plain `loadAllOnlinePlugins`; then auto-download pass by `AutoDownloadMode` | `MainActivity.kt:1358-1384` | §2.3 |
| 13 | `ioSafe`: `___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(context, false)` | `MainActivity.kt:1386-1391` | §2.4 |
| 14 | Account/library init: observe `LibraryViewModel.currentApiName` → swap nav Library icon to tracker logo | `MainActivity.kt:1631-1653` | "we need to run this after we init all apis" |
| 15 | `SearchResultBuilder.updateCache` (poster UI flags cache) | `MainActivity.kt:1655`; `SearchResultBuilder.kt:36-43` | |
| 16 | `ioSafe { initAll(); apis = allProviders.distinctBy { it } }` — calls `MainAPI.init()` on every provider, dedupes registry | `MainActivity.kt:1657-1661`; `MainAPI.kt:117-124` | |
| 17 | `setUpBackup()`, `CommonActivity.init(this)` (locale, TV mode, `AccountManager.initMainAPI()`, NewPipe downloader, activity-result launcher, POST_NOTIFICATIONS permission ask) | `MainActivity.kt:1664-1666`; `CommonActivity.kt:244-284` | |
| 18 | Nav controller / bottom-nav / rail wiring, long-press scroll-to-top | `MainActivity.kt:1667-1874` | |
| 19 | `loadCache()` (preloads `android.net.NetworkCapabilities` class — micro-opt) | `MainActivity.kt:1876`; `AppContextUtils.kt:693-698` | |
| 20 | `handleAppIntent(intent)` (deep links `cs.repo`, `cloudstreamapp://…` schemes) | `MainActivity.kt:1980`; `MainActivity.kt:281-316` | |
| 21 | `ioSafe { runAutoUpdate() }` — GitHub-release APK update check | `MainActivity.kt:1982-1984`; `InAppUpdater.kt:86-98, 250` | |
| 22 | `FcastManager().init`; `APIRepository.dubStatusActive = getApiDubstatusSettings()` | `MainActivity.kt:1986-1988` | |
| 23 | Delete legacy exoplayer caches | `MainActivity.kt:1990-1997` | |
| 24 | `ioSafe { migrateResumeWatching() }` — one-time re-key from `result_resume_watching` → `result_resume_watching_2` | `MainActivity.kt:2000-2002`; `DataStoreHelper.kt:548-565` | |
| 25 | TV channel create; legacy `home_api_used` → account-scoped migration; first-run → setup graph / no plugins → extensions setup | `MainActivity.kt:2004-2035` | `HAS_DONE_SETUP` |
| 26 | `DownloadQueueManager.init(this)` — restores the persisted download queue | `MainActivity.kt:2053` | §6.4 |

Also: `onResume` re-registers `afterPluginsLoadedEvent += ::onAllPluginsLoaded` (`MainActivity.kt:640-642`), which re-instantiates **custom cloned providers** from the `user_custom_sites` key after each plugin batch (`MainActivity.kt:810-844`).

### 1.3 Lazy vs eager summary

- **Eager at Activity create**: online plugins (with update pass), local plugins, account caches (via `AccountManager` companion `init` on first touch — `AccountManager.kt:94-110`), download queue restore.
- **Lazy**: Coil loader, every UI fragment's data, tracker API calls, `apis` name-map rebuild (`initMap` on first lookup — `MainAPI.kt:148-153`).
- **Reactive**: `Event<T>` hand-rolled observer sets (`utils/Event.kt:3-26`) — `afterPluginsLoadedEvent`, `mainPluginsLoadedEvent`, `afterRepositoryLoadedEvent`, `bookmarksUpdatedEvent`, `reloadHomeEvent`, `reloadLibraryEvent`, `reloadAccountEvent` (`MainActivity.kt:254-275`). Consumers: `HomeViewModel` (457-460), `SearchFragment` (152), `ResultFragment{,Phone,Tv}`, `APIRepository` (74), `LibraryViewModel` (139).

### 1.4 Plugin load failure handling

`loadPlugin` catches `Throwable`, toasts `plugin_load_fail` with the file name, returns `false` — one bad plugin never aborts the batch (`PluginManager.kt:677-686`). The currently-loading plugin name is tracked in `PluginManager.currentlyLoading` (`PluginManager.kt:191`) and written into the crash file by the exception handler (`CloudStreamApp.kt:56`) — so a crash during plugin `load()` names the culprit and triggers safe mode next start.

---

## 2. Plugin lifecycle orchestration

### 2.1 Persisted state

| Key (exact) | Store | Type | Meaning |
|---|---|---|---|
| `PLUGINS_KEY` | rebuild_preference | `Array<PluginData>` | **Online** (repo-installed) plugins registry (`PluginManager.kt:70`, `125-136`) |
| `PLUGINS_KEY_LOCAL` | rebuild_preference | `Array<PluginData>` | **Local** (sideloaded) registry — **wiped & rebuilt every start** (`PluginManager.kt:71`, `536`) |
| `REPOSITORIES_KEY` | rebuild_preference | `Array<RepositoryData>` | Added repo list (doc 04; `ExtensionsViewModel.kt:30`) |

`PluginData(internalName, url, isOnline, filePath, version)` (`PluginManager.kt:78-85`) is the app's *installation record*, distinct from the repo-side `SitePlugin` (`toSitePlugin()` adapter at 86-105). In-memory registries: `plugins: Map<filePath, BasePlugin>`, `urlPlugins: Map<url, BasePlugin>`, `classLoaders: Map<PathClassLoader, BasePlugin>`, plus flags `loadedOnlinePlugins` / `loadedLocalPlugins` (`PluginManager.kt:191-208`). A `Mutex lock` serializes registry writes (`PluginManager.kt:116`).

Online plugin files live at `filesDir/<ONLINE_PLUGINS_FOLDER>/<sanitized repoUrl-hash>/<sanitized internalName-hash>.cs3` — path is identity, "used to also detect if a plugin is installed" (`PluginManager.kt:744-755`, comment at 745). Local plugins are discovered from `/sdcard/Cloudstream3/plugins` (`LOCAL_PLUGINS_PATH`, `PluginManager.kt:186-189`) and **copied into `getExternalFilesDir("plugins")`** before loading because Android 14 rejects writable dex (`PluginManager.kt:527-559`).

### 2.2 The state machine

```
             downloadPlugin (repo UI / auto-download)
 repo.json ──────────────────────────────────────► [downloaded .cs3 on disk]
                                                        │ loadPlugin() (PluginManager.kt:593-687)
                                                        ▼
                 ┌──────────────► LOADED ──────────────┐
                 │   registerMainAPI/registerExtractorAPI (BasePlugin.kt:20-35, doc 02)
                 │   → APIHolder.allProviders + apis  │
                 │                                    │
   (update pass, outdated) downloadPlugin → unload old → load new (PluginManager.kt:790-796)
                 │                                    │
                 │  (repo status == PROVIDER_STATUS_DOWN
                 │   during update pass) unloadPlugin (PluginManager.kt:305-308)
                 ▼                                    ▼
              UNLOADED  ◄────── unloadPlugin(filePath) (PluginManager.kt:689-731)
                 │                  beforeUnload → deregister apis/extractors/clickActions
                 │                  → drop classloader + maps (NO fragment cleanup — doc 11 §7)
                 │ deletePlugin: File.delete() → unloadPlugin → deletePluginData
                 └──────────────► DELETED (PluginManager.kt:807-821)
```

Key semantics `[verified]`:

- **Load** = read `manifest.json` from the dex → reflectively construct the `BasePlugin` subclass → register in 3 maps → `plugin.load(context)` (Android `Plugin`) or `load()` (multiplatform `BasePlugin`) → providers self-register into `APIHolder` via `registerMainAPI` (`PluginManager.kt:611-673`; doc 02 §3). Classloader internals are doc 02's job; here only the orchestration matters.
- **Duplicate load** (same filePath already in `plugins`): logs "already exists", returns `true` — idempotent no-op (`PluginManager.kt:639-642`).
- **Unload** (`unloadPlugin`) runs `beforeUnload()` in try/catch, then removes (a) that plugin's `MainAPI`s from `APIHolder.apis`, (b) from `APIHolder.allProviders`, (c) its `ExtractorApi`s from `extractorApis`, (d) its `VideoClickAction`s, (e) the classloader + map entries (`PluginManager.kt:697-730`). So an unloaded plugin is **fully deregistered**, not merely hidden.
- **Disable** (repo-side): `OnlinePluginData.isDisabled = onlineData.plugin.status == PROVIDER_STATUS_DOWN` (`PluginManager.kt:231`); the update pass responds by `unloadPlugin`ing it (305-308) and the manual pass too (854-860). There is **no local "enabled=false" flag**: delete the plugin to fully remove; uninstall is not "off".
- **Update**: `isOutdated = repoVersion > savedVersion || repoVersion == -1` (`PLUGIN_VERSION_ALWAYS_UPDATE`, `PluginManager.kt:111-112, 229-230`). Update flow = load-all-first (fast startup), then diff against repos, download outdated in parallel (`amap`), notify via the Extensions channel (`PluginManager.kt:274-338`).
- **Manual update** (Settings → Updates → "Update plugins"): `___DO_NOT_CALL_FROM_A_PLUGIN_manuallyReloadAndUpdatePlugins` — force-deletes and re-downloads **every** installed online plugin, not just outdated ones (`PluginManager.kt:830-899`).
- **Hot reload** (dev only): `hotReloadAllLocalPlugins` unloads all locals then reloads with `forceReload=true` (`PluginManager.kt:485-494`).
- **Delete**: `deletePlugin(file)` = file delete → `unloadPlugin` → remove `PluginData` rows (`PluginManager.kt:807-821`).
- Anti-recursion guard `assertNonRecursiveCallstack()` throws if any caller is itself inside `loadPlugin` — a plugin calling these would recurse infinitely (`PluginManager.kt:447-452`); hence the `___DO_NOT_CALL_FROM_A_PLUGIN_` prefix (also `@InternalAPI`).

### 2.3 Auto-download of missing plugins (AutoDownloadMode)

`enum class AutoDownloadMode { Disable(0), FilterByLang(1), All(2), NsfwOnly(3) }` (`MainAPI.kt:1144-1154`, LIB). Selection lives in AndroidX pref `auto_download_plugins_key` (Int, default 0=Disable — `MainActivity.kt:1372-1377`, `settings_updates.xml`).

`___DO_NOT_CALL_FROM_A_PLUGIN_downloadNotExistingPluginsAndLoad` (`PluginManager.kt:352-445`):
1. Fetch all repo indexes (user repos + `PREBUILT_REPOSITORIES`), flatten, distinct-by-url.
2. Skip: blank url, no `repositoryUrl`, already on disk, NsfwOnly-without-`TvType.NSFW`, NSFW-when-`enableAdult` false, and (FilterByLang) lang not in `provider_lang_key` selection.
3. Download all survivors in parallel, notify "N plugins downloaded" via Extensions channel.

`[note]` Doc 10 §"auto-install" documents the filter interplay; the **only** NSFW master toggle is `enable_nsfw_on_providers_key` (doc 10 table line 240).

### 2.4 Local plugin rebuild-on-start

`___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(context, forceReload)` (`PluginManager.kt:506-568`): `removeKey(PLUGINS_KEY_LOCAL)` (comment: "local can be removed at any time without app knowing, hence the local are getting rebuilt on every app start" — `PluginManager.kt:69`), mkdirs, copy-if-changed to app-external dir (length+mtime compare), `maybeLoadPlugin` per file (only `.zip`/`.cs3` — `PluginManager.kt:210-221`), sorted alphabetically "for reproducible results" (`PluginManager.kt:522-538`), then `loadedLocalPlugins = true; afterPluginsLoadedEvent.invoke(forceReload)`.

---

## 3. The provider registry (APIHolder) and the APIRepository wrapper

### 3.1 Where MainAPI instances live

`APIHolder` (in the **library** module, `MainAPI.kt:109-172`) holds:

```kotlin
// LIB MainAPI.kt:115,131-132
val allProviders = atomicListOf<MainAPI>()     // master registry
var apis: AtomicList<MainAPI> = atomicListOf() // name-lookup subset (indexed)
var apiMap: Map<String, Int>? = null           // name → index memo
```

- Providers self-register at plugin `load()` time via `BasePlugin.registerMainAPI` → `allProviders.add(element)` + `addPluginMapping` (`BasePlugin.kt:20-25`; `MainAPI.kt:134-139`).
- `MainActivity.onAllPluginsLoaded` re-sorts this out after every batch: re-instantiates **custom clone sites** (from `user_custom_sites`, matching by `::class.simpleName`, overriding name/lang/mainUrl, `canBeOverridden = false`) and reassigns `apis = allProviders.distinctBy { it }` (`MainActivity.kt:810-844`; second dedupe pass at 1657-1661 "No duplicates (which can happen by registerMainAPI)").
- Lookups: `getApiFromNameNull(name)` — apiMap hit, fallback linear scan of `allProviders` by `name`; `getApiFromUrlNull(url)` — first provider whose `mainUrl` prefixes the url (`MainAPI.kt:155-172`). **These are THE lookup functions used across the app** (ResultViewModel2, RepoLinkGenerator, CS3IPlayer, DownloadManager, SubscriptionWorkManager, HomeFragment, QuickSearch — doc 06/08/09 callers).
- `initAll()` calls `api.init()` on every provider (provider-defined hook) and nulls the map (`MainAPI.kt:117-124`).
- **No per-account provider isolation**: the registry is global and static; "accounts" (§8) only namespace *data*, not providers `[verified]`.

### 3.2 APIRepository — why the wrapper exists

`class APIRepository(val api: MainAPI)` (`APP/ui/APIRepository.kt:27`) is the app-side adapter every ViewModel goes through (Home/Search/Result use `APIRepository(api)`, doc 06/07). It adds exactly five things `[verified]`:

1. **Timeout hard-kill**: every call wrapped in `withTimeout(getTimeout(...))` — `DEFAULT_TIMEOUT = 120_000L`, clamped to `[5 s, 8 min]`; per-call overrides from `api.loadTimeoutMs` etc. (`APIRepository.kt:29-33, 62-64`). Comment: "No real provider should take longer, so we hard kill them."
2. **`safeApiCall` Resource wrapping** — suspend provider calls become `Resource.Success/Failure` for the MVVM layer (mvvm package).
3. **Repository-level LoadResponse cache** (§7).
4. **Input sanitation**: `isInvalidData(data)` rejects `""`, `"[]"`, `"about:blank"` before calling `load`/`loadLinks` (`APIRepository.kt:48-50, 88, 210`).
5. **mainPage orchestration**: fan-out parallel `async` per `mainPage` row, or sequential mode with `sequentialMainPageDelay` + `waitForHomeDelay()` pacing (`APIRepository.kt:150-196`) — doc 06 §"driving".
6. Statics: `dubStatusActive` (fed from `display_sub_key` — `MainActivity.kt:1988`, `SettingsProviders.kt:52`), and placeholder providers `noneApi` ("None") / `randomApi` ("Random") used as pseudo-homepages (`APIRepository.kt:35-46`; `HomeViewModel.kt:519-547` persists them into `currentHomePage` as names).
7. Tag hygiene: strips blank tags from loaded responses (`APIRepository.kt:105-108`).

`[note]` The wrapper does **not** retry, does not queue, does no per-account keying of requests. It exists to make plugin calls safe + cached + Resource-shaped.

---

## 4. DataStore key inventory — THE TABLE

### 4.1 The two stores

1. **`rebuild_preference`** (SharedPreferences via `utils.DataStore`, values JSON-serialized by Jackson/kotlinx): app state, plugin state, all library/user data, plugin-authored settings. Full mechanics in doc 11 §3 (`DataStore.kt:26`). Keys with a `/` are "folder" keys → one SharedPreferences entry each (`getFolderName`, doc 11 §3.1).
2. **AndroidX default prefs** (`PreferenceManager.getDefaultSharedPreferences(this)` — `MainActivity.kt:1188`): all Settings-screen preferences (UI, player, providers, updates…). Enumerated in §9; filter keys documented in doc 10 §"keys".

Account scoping: `DataStoreHelper.currentAccount = selectedKeyIndex.toString()` (`DataStoreHelper.kt:180-181`); most user-state keys are prefixed `"$currentAccount/"` (via `UserPreferenceDelegate` at `DataStoreHelper.kt:62-82` or manually). **Switching accounts = different key namespace, zero data copy** (`setAccount` — `DataStoreHelper.kt:198-210`).

### 4.2 Plugin & repository state (global)

| Key | Type | Default | Written by | Read by |
|---|---|---|---|---|
| `PLUGINS_KEY` | `Array<PluginData>` | `[]` | `setPluginData/deletePluginData` (PM:125-149) | `getPluginsOnline` (PM:178) — startup, Extensions UI |
| `PLUGINS_KEY_LOCAL` | `Array<PluginData>` | `[]` (rebuilt each start) | `loadAllLocalPlugins` (PM:536) | `getPluginsLocal` (PM:182), `loadSinglePlugin` |
| `REPOSITORIES_KEY` | `Array<RepositoryData>` | `[]` | Extensions UI (doc 04) | update/auto-download passes (PM:281,359,838) |

### 4.3 Accounts & profile (global)

| Key | Type | Default | Written by | Read by |
|---|---|---|---|---|
| `data_store_helper/account` | `Array<Account>` | `[]` | account UI (`DSH:179`; `Account{keyIndex,name,customImage,defaultImageIndex,lockPin}` DSH:163-176) | `getAccounts/getCurrentAccount` (DSH:212-241), MainActivity biometric gate (MA:1309-1312) |
| `data_store_helper/account_key_index` | Int | 0 | `setAccount` (DSH:198-210) | `currentAccount` (DSH:181) — prefixes everything |
| `auth_tokens` → `auth_tokens/<prefix>/<account>` | `Array<AuthData>` | `[]` | `AccountManager.updateAccounts` (AM:48-54) | login state, `cachedAccounts` (AM:94-110) |
| `auth_ids` → `auth_ids/<prefix>/<account>` | Int | -1 (`NONE_ID`) | `updateAccountsId` (AM:56-62) | multi-account-per-tracker selection (AM:77-92) |
| `<idPrefix>_sync` → `<idPrefix>_sync/<id>` | String (url) | — | `DataStoreHelper.addSync` (DSH:817-819) | sync bookkeeping `getSync` (DSH:821-825) — maps local id → tracker url |

### 4.4 Watch / library state (per-account, folder keys → one entry per item id)

Item id = **stable hash of the provider-relative URL**: `url.replace(mainUrl,"").replace("/","").hashCode()` (`ResultViewModel2.kt:370-379`) — same id keys *every* per-show table below `[verified]`.

| Key (prefix `"$currentAccount/"`) | Leaf key | Type | Default | Written by | Read by |
|---|---|---|---|---|---|
| `video_pos_dur` | episode id | `PosDur(position,duration)` ms | absent | `setViewPos` (DSH:691-695; skips <30 s) | player resume, ResultEpisode building, resume row |
| `video_watch_state` | episode id | `VideoWatchState` (`Watched` only; `None` = key removed) | absent | `setVideoWatchState` (DSH:758-771) | episode watched styling (doc 09) |
| `result_watch_state` | show id | Int `WatchType.internalId` (0-4; 5=NONE deletes) | absent→NONE | `setResultWatchState` (DSH:782-789) | bookmarks grouping (`getAllWatchStateIds` DSH:515-520, LocalList:30-34) |
| `result_watch_state_data` | show id | `BookmarkedData` (full snapshot, §5) | absent | `setBookmarkedData` (DSH:616-620) | `getAllBookmarkedData` (DSH:627-631), LocalList library page |
| `result_subscribed_state_data` | show id | `SubscribedData` (§6.1) | absent | `setSubscribedData/updateSubscribedData` (DSH:648-661) | `getAllSubscriptions` (DSH:633-637), subscription worker, LocalList |
| `result_favorites_state_data` | show id | `FavoritesData` (§5) | absent | `setFavoritesData` (DSH:680-684) | `getAllFavorites` (DSH:668-672), LocalList favorites page |
| `result_resume_watching_2` | parent id | `ResumeWatching(parentId,episodeId,episode,season,updateTime,isFromDownload)` | absent | `setLastWatched` (DSH:567-588) | `getLastWatched` (DSH:600-606) — "continue watching" |
| `result_resume_watching` (OLD) | parent id | same | absent | legacy | `migrateResumeWatching` (DSH:541-565), runs at startup (MA:2000-2002) |
| `result_resume_watching_migrated` | — | Boolean | false | migration (DSH:550) | [inferred] unused guard (migration is unconditional in this snapshot) |
| `result_episode` | show id | Int? | null | `setResultEpisode` (DSH:813-815) | last-selected episode on result screen |
| `result_season` | show id | Int? | null | `setResultSeason` (DSH:805-807) | last-selected season |
| `result_dub` | show id | Int (DubStatus ordinal; -1 absent) | null | `setDub` (DSH:778-780) | dub preference per show (`getDub` DSH:773-776; subscription worker SWM:145-151) |
| `search_history` | search text | (entry) | — | SearchViewModel (SVM:214) | history list (SVM:86, SearchFragment:560-585) |
| `last_sync_api` | — | String | first available | LibraryViewModel setter (LV:54-57) | which tracker's list the Library tab shows (LV:50-53) |
| `library_folder/<syncName>` | per-sync key | `LibraryOpener` | Default | LibraryFragment dialog (LF:267-271) | "open library item with…" behavior (LF:233-246) |

### 4.5 Per-account user preferences (`UserPreferenceDelegate` — DSH:62-82)

| Key (prefix `"$currentAccount/"`) | Type | Default | Consumer |
|---|---|---|---|
| `search_pref_providers` | `List<String>` | `[]` → falls back to preferred-media-filtered providers (DSH:109-118) | search provider preselection (doc 06) |
| `search_pref_tags` | `List<String>` (TvType names) | `["Movie","TvSeries"]` (DSH:120-122) | search type chips |
| `home_pref_homepage` | `List<String>` (TvType names) | `["Movie","TvSeries"]` (DSH:130-132) | home preferred-media rows |
| `home_bookmarked_last_list` | `IntArray` | `[]` (DSH:140-143) | home bookmarks tab restore |
| `playback_speed` | Float | 1.0 (DSH:145) | player |
| `resize_mode` | Int | 0 (DSH:146) | player aspect |
| `library_sorting_mode` | Int (ListSorting ordinal) | `AlphabeticalA` (DSH:147-150; enum LV:19-29) | library sort FAB (LV:83, 123-133) |
| `results_sorting_mode` | Int (EpisodeSortType ordinal) | `NUMBER_ASC` (DSH:152-155) | episode list sort |

### 4.6 Player / quality profiles (mostly per-account)

| Key | Type | Default | Consumer |
|---|---|---|---|
| `"$currentAccount/preferred_audio_language"` | String | null (CS3IPlayer:722-730) | audio track selection (CS3IPlayer:124) |
| `"$currentAccount/video_profile_name/<profile>"` | String | null (QDH:96-103) | quality-profile names (QDH:22) |
| `"$currentAccount/video_profile_types_2/<profile>"` | `Array<QualityProfileType>` | — (QDH:150-171) | profile→WiFi/Data/Download binding (QDH:31) |
| `"$currentAccount/video_profile_type/<profile>"` | `QualityProfileType` | — | @Deprecated single-type legacy (QDH:26-28) |
| `"$currentAccount/video_source_priority/<profile>/<extractorName>"` | Int | 1 (QDH:64-94; AUTO_SKIP_PRIORITY=10) | source ordering in player (QDH:21, 37) |
| `subs_auto_select` | String | null | subtitle auto-select language (SubtitlesFragment:62; GeneratorPlayer:210) |
| `video_player_alpha_key` | Float | — | player brightness (DataStore.kt:23) |

### 4.7 Downloads (global)

| Key | Type | Default | Consumer |
|---|---|---|---|
| `download_queue_key` | `Array<DownloadQueueWrapper>` | `[]` | the persistent queue; `_queue` StateFlow re-persisted on every change (DQM:33-58) |
| `download_resume_2` (folder, leaf=id) | `DownloadResumePackage` | — | resume interrupted downloads (VDM:197; getDownloadResumePackage VDM:1642) |
| `download_resume_queue_key` (folder, leaf=id) | (presence) | — | ids not yet started; drained into queue at init then `removeKeys` (VDM:203; DQM:82-102) |
| `download_info` | — | — | KEY_DOWNLOAD_INFO (VDM:198) download bookkeeping |
| `download_header_cache/<id>` | `DownloadHeaderCached{apiName,url,type,name,poster,id,cacheTime}` | — | Downloads screen poster/title without provider call (DataStore.kt:17; DM:1900-1912; shape DO:110-118) |
| `download_episode_cache/<resultId>/<episodeId>` | `DownloadEpisodeCached{...}` | — | per-episode download metadata (DataStore.kt:21; DM:1913-1930) |
| `BACKUP_download_header_cache` / `BACKUP_download_episode_cache` | backup copies | — | BackupUtils (DataStore.kt:18,22) |

### 4.8 Misc / system

| Key | Type | Default | Consumer |
|---|---|---|---|
| `VERSION_NAME` | String | — | app-update auto-backup trigger (MA:1217-1220) |
| `FILES_TO_DELETE_KEY` | `Set<String>` | `[]` | delete-on-exit registry (MA:220-237) |
| `HAS_DONE_SETUP` | Boolean | false | first-run → setup graph (SetupFragmentLanguage:23; MA:2020-2022) |
| `user_custom_sites` | `Array<CustomSite>` | null | custom cloned providers (DataStore.kt:25; MA:810-844; editor SettingsGeneral:190,263,284) |
| `user_pinned_providers` | `Array<String>` | `[]` | pinned providers in home/search provider sheets (DSH:60,827-829; HomeFragment:424-535; SearchFragment:500) |
| legacy `home_api_used` (unprefixed) | String | — | migrated into `"$currentAccount/home_api_used"` at MA:2015-2018 |
| `"$currentAccount/home_api_used"` | String? | null | **currentHomePage** — which provider the Home tab shows (DataStore.kt:24; DSH:187-196; setters HomeViewModel:522-547, SettingsProviders:105, SetupFragmentMedia:62) |
| `last_click_action` / `last_opened` | String | — | VideoClickAction result plumbing (CommonActivity:258-264) |
| `result_sort` (`KEY_RESULT_SORT`) | — | — | **declared but no read/write consumers found** — [inferred] vestigial; episode sorting actually persists via `results_sorting_mode` (DSH:59; grep shows only the declaration + an unrelated layout id) |

**Count**: ~45 distinct key names/families cataloged above (12 watch-state + 8 user-pref delegates + 7 player/quality + 7 downloads + 3 plugin + 5 account/auth + ~10 misc + search/library UI), split across 2 stores. Every name in the table is quoted exactly from source.

---

## 5. Favorites / library model

### 5.1 Storage shape — full SearchResponse snapshots

All three user lists persist the **same flattened snapshot** — a `LibrarySearchResponse` subclass (abstract base `DataStoreHelper.kt:263-305`) capturing every display field of a provider's `SearchResponse` plus timestamps:

```kotlin
// DataStoreHelper.kt:374-389 — BookmarkedData field list verbatim
// (@JsonProperty(...) annotations elided; @SerialName values are the stored key names)
@Serializable
data class BookmarkedData(
    @SerialName("bookmarkedTime") val bookmarkedTime: Long,
    @SerialName("id") override var id: Int?,
    @SerialName("latestUpdatedTime") override val latestUpdatedTime: Long,
    @SerialName("name") override val name: String,
    @SerialName("url") override val url: String,
    @SerialName("apiName") override val apiName: String,
    @SerialName("type") override var type: TvType?,
    @SerialName("posterUrl") override var posterUrl: String?,
    @SerialName("year") override val year: Int?,
    @SerialName("syncData") override val syncData: Map<String, String>? = null,
    @SerialName("quality") override var quality: SearchQuality? = null,
    @SerialName("posterHeaders") override var posterHeaders: Map<String, String>? = null,
    @SerialName("plot") override val plot: String? = null,
    @SerialName("score") override var score: Score? = null,
    @SerialName("tags") override var tags: List<String>? = null,
) : LibrarySearchResponse(...)
```

(`FavoritesData` at DSH:435-491 is field-identical modulo `favoritesTime`; `SubscribedData` at 310-369 adds `lastSeenEpisodeCount` — fields 311-312.)

- **Keying**: `"$currentAccount/result_favorites_state_data/<id>"` where `id` = `getLoadResponseIdFromUrl(uniqueUrl, apiName)` = hash of the provider-relative URL (`ResultViewModel2.kt:370-379`). One JSON entry per favorite; `favoritesTime` (when added) + `latestUpdatedTime` (when last re-saved) are the only app-added fields.
- **Semantics of the three lists**: *bookmarks* (`result_watch_state` + `result_watch_state_data`) = the 5-state WatchType model (WATCHING/COMPLETED/ONHOLD/DROPPED/PLANTOWATCH — `WatchType.kt:7-13`) — the user's "bookmaking" ✓; *favorites* = a separate heart-toggle list; *subscriptions* = §6. A show can be in all three simultaneously (three independent keys).
- `toLibraryItem()` adapters convert each snapshot into a `SyncAPI.LibraryItem` for the shared library renderer (`DSH:348-368, 409-429, 470-490`); legacy `rating`→`score` write-only shim on the base class (`DSH:291-304`); `WriteOnlySerializer` drops `rating` on output (`DSH:343-346`).
- **Write path**: `ResultViewModel2.toggleFavoriteStatus` — if already favorite: remove key. Else **duplicate check** (see below), then `setFavoritesData(currentId, FavoritesData(...))` built from the freshly-loaded `LoadResponse` (name/url/apiName/type/poster/year/syncData/plot/score/tags) (`ResultViewModel2.kt:935-997`). `updateWatchStatus` mirrors this for bookmarks (`ResultViewModel2.kt:736-811`). Both fire `MainActivity.bookmarksUpdatedEvent` + `reloadLibraryEvent`.
- **Duplicate handling**: before adding, `checkAndWarnDuplicates` matches candidates by imdb/tmdb/mal/anilist syncData ids OR normalized-name+year-compat, shows an AlertDialog, and on confirm deletes the duplicate ids first (`ResultViewModel2.kt:999-1079+`) — dedupe across *providers* is manual/user-driven, not enforced.

### 5.2 How the library screen consumes it

`LibraryFragment` + `LibraryViewModel`:
- `LibraryViewModel.currentSyncApi` = whichever tracker the Library tab points at — default = the **first *available** of `AccountManager.syncApis`** (order mal → kitsu → aniList → simkl → local; `isAvailable = !requiresLogin || authUser() != null` — `AuthRepo.kt:27`, so logged-in trackers win, `LocalList` with `requiresLogin = false` is the always-available fallback — `LocalList.kt:24`); selection persisted in `"$currentAccount/last_sync_api"` (`LibraryViewModel.kt:47-57`).
- `reloadPages` calls `currentSyncApi.library()` → for `LocalList` that's: read all `result_watch_state` ids, group `BookmarkedData` by `WatchType` (5 pages, NONE excluded), add a Favorites page from `getAllFavorites()`, add a Subscriptions page (hidden on TV layout) (`LocalList.kt:29-91`). Refresh is skipped unless `requireLibraryRefresh` (set true by every DSH write — e.g. `DSH:619,660,676,683`) (`LibraryViewModel.kt:91-95`).
- Sorting: `ListSorting` enum — Query(none), RatingHigh/Low, UpdatedNew/Old, AlphabeticalA/Z, ReleaseDateNew/Old (`LibraryViewModel.kt:19-29`); persisted as ordinal in `library_sorting_mode`; LocalList supports only the non-rating six (`LocalList.kt:79-89`).
- Page rendering: `ViewpagerAdapter`/`PageAdapter` per-list ViewPager; card click dispatches on the item's `syncId` through the per-sync **LibraryOpener** pref (`"$currentAccount/library_folder/<syncName>"` → Default/None/Browser/Search/provider — `LibraryFragment.kt:212-273, 299-309`), which is why a MAL library item can be opened with a chosen CS3 provider (doc 10 §"MetaProvider").
- Resume row: home "Continue watching" reads `result_resume_watching_2` + `video_pos_dur` and renders `ResumeWatchingResult` (a `SearchResponse` subclass with `watchPos`, `parentId`, `episode`, `season`, `isFromDownload` — `DSH:493-509`); progress bar drawn from `PosDur.fixVisual()` clamps (`DSH:249-258`; `SearchResultBuilder.kt:254-268`).
- Watched marking: player progress >90 % (`NEXT_WATCH_EPISODE_PERCENTAGE = 90`, `AbstractPlayerFragment.kt:28`) → `setViewPosAndResume` advances the resume pointer to the next episode or clears it on last-episode; `VideoWatchState.Watched` per-episode key set on explicit mark / cleared on rewatch (`DSH:697-751`; player wiring in doc 09).

`[gap → doc 15/17]` CS3 favorites need only `apiName+url` to re-open an item — the rest of the snapshot is *display cache* that goes stale (poster changes, episode counts don't update until the item is re-loaded and re-saved: only `updateSubscribedData` refreshes snapshots, and only for subscriptions — `DSH:648-655`).

---

## 6. Subscriptions & notifications

### 6.1 Subscription record shape

```kotlin
// DataStoreHelper.kt:309-313
data class SubscribedData(
    @SerialName("subscribedTime") val subscribedTime: Long,
    @SerialName("lastSeenEpisodeCount") val lastSeenEpisodeCount: Map<DubStatus, Int?>,
    @SerialName("id") override var id: Int?, // + all LibrarySearchResponse fields
```

Written by `toggleSubscriptionStatus` (only for `EpisodeResponse` shows — no movie subscriptions) with initial `lastSeenEpisodeCount = response.getLatestEpisodes()` (`ResultViewModel2.kt:836-904`). Key: `"$currentAccount/result_subscribed_state_data/<id>"`.

### 6.2 Checking mechanism — SubscriptionWorkManager

- **WorkManager** `PeriodicWorkRequest`, **6 hours**, constraint `NetworkType.CONNECTED`, unique name `work_subscription`, policy KEEP (`SubscriptionWorkManager.kt:37-66`). Enqueued **when the user subscribes** (MainActivity preview 1462-1477, `ResultFragmentPhone:628`, `ResultFragmentTv:648`) — not at app start; if zero subscriptions exist the worker cancels itself (`SWM:118-123`). No alarms, no exact repeating.
- `doWork` runs as a **foreground** CoroutineWorker (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`, progress notification id `938712897`) (`SWM:101-116`).
- It **force-loads all plugins** first (online + local) because WorkManager runs out-of-process from the UI lifetime (`SWM:130-132`).
- Per subscription: resolve api by name → `api.load(url) as EpisodeResponse` with **60 s timeout** → pick dub preference (`getDub(id)` → global `display_sub_key` → default Subbed) → compare `latestEpisodes[dub]` vs stored `lastSeenEpisodeCount[dub]` (fallback `DubStatus.None`) (`SWM:134-167`).
- Always `updateSubscribedData` (refresh snapshot + counts) (`SWM:169-173`); if new episodes: notification with poster (`getImageBitmapFromUrl` with stored `posterHeaders`), deep-link `PendingIntent` → `MainActivity` with `data=url` + `API_NAME_EXTRA_KEY` (`SWM:175-207`).
- Failure policy: catch-all returns `Result.success()` anyway — "android just crashes and this causes major battery usage as it retries inf times" (`SWM:218-224`).

### 6.3 Notification channels (complete list)

| Channel id | Name | Created/used by |
|---|---|---|
| `cloudstream3.extensions` | Extensions | plugin update/auto-download notices (`PluginManager.kt:73-75, 901-966`) — silent, LOW |
| `cloudstream3.subscriptions` | Subscriptions | `SubscriptionWorkManager.kt:29-33, 104-108` |
| `cloudstream3.backups` | Backups | `BackupWorkManager.kt:20-24, 81-85` (periodic backup, StorageNotLow constraint, interval from `automatic_backup_key` — `BWM:29-66`; manual backups via Settings) |
| `cloudstream3.general` | Downloads | per-download progress notifications (`DownloadManager.kt:109-111, 215-221`; doc 09) |
| `cloudstream3.download.queue` | Download queue service | queue foreground service, id `917194232` (`DownloadQueueService.kt:51-55, 150`) |
| `cloudstream3.updates` | (app updates) | APK installer service (`PackageInstallerService.kt:176`) |

POST_NOTIFICATIONS permission requested at first MainActivity create on 13+ (`CommonActivity.kt:268-283`). `VideoDownloadService` is a tiny intent-service that maps notification actions → `VideoDownloadManager.downloadEvent` (pause/resume/stop by id — `VideoDownloadService.kt:10-45`).

**One-liners — remaining services/receivers/widgets:**
- `services/PackageInstallerService.kt` (188 L) — session-based APK installer used by the in-app updater (`cloudstream3.updates` channel — `PackageInstallerService.kt:176`).
- `services/DownloadQueueService.kt` (279 L) — foreground `Service` that pops the persistent queue and drives `VideoDownloadManager` instances (`DownloadQueueService.kt:48-55`; started by `DownloadQueueManager.startQueueService` — `DQM:173-186`; pipeline in doc 09).
- `receivers/VideoDownloadRestartReceiver.kt` (17 L) — broadcast receiver wired to a `"restart_service"` action sent from `MainActivity.onDestroy` (`MainActivity.kt:723-726`); its body is **entirely commented out** — vestigial keep-alive stub `[verified]`.
- `widget/` (3 files, no home-screen app widgets): `CenterZoomLayoutManager.kt` (109 L, TV rail zoom effect), `FlowLayout.kt` (119 L, chip/tag wrapping layout), `LinearRecycleViewLayoutManager.kt` (30 L) — pure layout managers `[verified]`. TV "channels" (home-screen rows) are done via `TvChannelUtils` instead (`MainActivity.kt:2004-2013`).

### 6.4 The auto-download tie-in

Two distinct "auto-downloads" — don't conflate:
1. **Plugin** auto-download (`AutoDownloadMode`, §2.3) — installs *extensions*.
2. **Episode** auto-download on new subscription episodes: **NOT implemented at this commit** — the subscription worker only notifies (`SWM:175-207` has no download call). `[verified by absence; note for doc 15]` The download pipeline itself (queue → `DownloadQueueService` → `VideoDownloadManager`) is doc 09's scope; here only the persistence matters: `download_queue_key` + `download_resume_queue_key` are drained by `DownloadQueueManager.init` at startup, which skips work entirely **in safe mode** (`DownloadQueueManager.kt:60-66`).

---

## 7. Result caching

| Cache | Where | Capacity / TTL | Invalidation |
|---|---|---|---|
| **LoadResponse ring cache** | `APIRepository` companion — `atomicListOf<SavedLoadResponse>`, `CACHE_SIZE = 20`, 10-min TTL check on read (`APIRepository.kt:52-64, 92-117`) | 20 entries / 10 min (key = `Pair(api.name, fixedUrl)`) | TTL expiry on read; **fully cleared on `afterPluginsLoadedEvent(forceReload=true)`** (hot reload) (`APIRepository.kt:67-75`); process death |
| Search results | **none** — `search()`/`quickSearch()` hit the provider every time (`APIRepository.kt:123-148`) | — | — |
| mainPage results | none in APIRepository (HomeViewModel holds LiveData per session; doc 06) | — | reload events |
| Poster/UI flags | `SearchResultBuilder.showCache` in-memory map of poster-option prefs (`SearchResultBuilder.kt:33-43`) | session | `updateCache()` on MainActivity create / ResultFragment resume |
| Download metadata | `download_header_cache` / `download_episode_cache` keys (§4.7) — persistent, `cacheTime` stored per record (`DM:1900-1930`) | unbounded | overwritten per download; included in backups |
| **Link + subtitle cache** | `RepoLinkGenerator` companion, keyed `(apiName, episodeId)` — **NOT re-derived here**; full mechanics + 20-min TTL + saturation in **doc 08 §4** (`RepoLinkGenerator.kt`, cited via doc 08:527-534) | per-episode links/subs / 20 min | TTL, `clearCache=true` on user reload (doc 08) |
| HTTP cache | per-request `cacheTime` param in library `app.get(...)` (providers pass `cacheTime = 0` to bypass); player-side Cronet disk cache + ExoPlayer `SimpleCache` — **doc 09** "Transport choice" section, cited not re-derived | provider/player-controlled | per doc 09 |
| Class pre-load | `loadCache()` warms `android.net.NetworkCapabilities` (`AppContextUtils.kt:693-698`) | — | — |

`[note]` There is no disk result cache and no per-account keying of the APIRepository cache — any account switching shares the same 20 LoadResponses (they're keyed only by api+url).

---

## 8. Accounts & sync

### 8.1 The AccountManager model

`AccountManager` (abstract class w/ static companion — `syncproviders/AccountManager.kt:19-166`) is a **registry of 10 repos**, not a login manager:

- **Sync repos** (5): `SyncRepo(malApi)`, `SyncRepo(kitsuApi)`, `SyncRepo(aniListApi)`, `SyncRepo(simklApi)`, `SyncRepo(localListApi)` (`AM:64-75, 127-133`).
- **Subtitle repos** (4): OpenSubtitles, Addic7ed, SubDl, SubSource (`AM:121-126`).
- **Plain auth** (1): AnimeSkip (`AM:73`).
- Auth tokens per (provider, app-account): `auth_tokens/<idPrefix>/<accountIndex>`; selected per-provider account index: `auth_ids/<idPrefix>/<accountIndex>` (`AM:37-62`) — so **one tracker can hold multiple logins, one active per app profile**.
- `initMainAPI()` pushes tracker id-prefixes into `LoadResponse` statics (`malIdPrefix` etc.) so any provider can emit `syncData["mal"] = id` and it resolves — the metadata join underpinning library openers and duplicate detection (`AM:114-119`; `LoadResponse.syncData`, doc 05/10).

### 8.2 What the SyncAPI contract does

`SyncAPI : AuthAPI` defines the per-tracker operations: `updateStatus`, `status`, `load`, `search`, `library`, `urlToId` (all default `NotImplementedError`) + `requireLibraryRefresh` flag + `supportedWatchTypes` + `syncIdName` (`SyncAPI.kt:22-71`); result types `SyncSearchResult` (a `SearchResponse`), `AbstractSyncStatus{status,score,watchedEpisodes,isFavorite,maxEpisodes}`, `SyncResult`, `LibraryMetadata/LibraryList/Page/LibraryItem` (`SyncAPI.kt:73-120+`).

What syncs: **watch status + score + episode counts** per item (`SyncStatus`), and the **library list** (rendered in the Library tab); favorites are exposed to trackers via `BookmarkedData.toLibraryItem()` / `FavoritesData.toLibraryItem()` conversions (§5.1) — the local and tracker lists are *views over the same UI*, not a bidirectional sync engine `[inferred]` (each `SyncRepo` implementation owns its own push/pull; e.g. MAL/AniList APIs in `syncproviders/providers/`, not re-derived here).

### 8.3 App profiles vs tracker accounts — two-layer isolation

- **App profiles** (`data_store_helper/account`): keyIndex namespaces ALL DataStoreHelper user state (§4) — separate libraries, watch positions, homepages, quality profiles per profile. Switching = `setAccount` → swaps prefix + fires reload events + `AccountManager.updateAccountIds()` (`DSH:198-210`).
- **Tracker logins** (`auth_tokens`/`auth_ids`): also namespaced by profile (§8.1).
- Per-profile lock: `Account.lockPin` + biometric (phone-layout gate, `MainActivity.kt:1308-1325`; PIN dialogs in `SettingsAccount.kt:156+`).

---

## 9. Settings IA map

`SettingsFragment` (nav `navigation_settings`) shows profile header + 7 entries (`SettingsFragment.kt:223-242`) → each a `BasePreferenceFragmentCompat` on an `R.xml.*` resource (doc 11 §4 covers the base class). One-liners `[verified]`:

| Screen (nav id) | File / xml | What it contains |
|---|---|---|
| **General** | `SettingsGeneral.kt` / `settings_general.xml` | language (`locale_key`), DNS-over-HTTPS (`dns_key`), jsDelivr proxy toggle, concurrent/parallel download limits, download path, **custom cloned providers editor** (`user_custom_sites` — SG:190,263,284), battery-optimization exemption, benene easter egg |
| **Player** | `SettingsPlayer.kt` / `settings_player.xml` | default player (`player_default_key`), ExoPlayer buffer length/size/disk-clear, video limit titles, quality-profile pickers (`quality_pref_key` + mobile-data variant) → SourcePriorityDialog flows, subtitle settings (+Chromecast variant), TV category |
| **Accounts** ("Credits" in code, `settings_account`) | `SettingsAccount.kt` / `settings_account.xml` | login/logout rows for mal/kitsu/anilist/simkl/opensubtitles/subdl/animeskip, biometric lock (`biometric_key`), skip-startup-account-select |
| **UI** | `SettingsUI.kt` / `settings_ui.xml` | layout (phone/TV/emulator `app_layout_key`), theme + primary color (applied via `loadThemes` — `CommonActivity.kt:339-358`), poster size + poster UI option flags, overscan, TV clock, exit-confirm, search-quality filter |
| **Providers** | `SettingsProviders.kt` / `settings_providers.xml` | dub/sub display (`display_sub_key`), preferred media types (`prefer_media_type_key`, resets home), provider language filter (`provider_lang_key`), NSFW toggle (`enable_nsfw_on_providers_key`), **test providers** screen (`navigation_test_providers` — `ui/settings/testing/` runs every provider through search+load and reports a table) — key semantics in doc 10 |
| **Updates** | `SettingsUpdates.kt` / `settings_updates.xml` | auto-update plugins (`auto_update_plugins_key`), **auto-download plugins mode** (`auto_download_plugins_key`), manual update plugins (`manual_update_plugins_key` → `___DO_NOT_CALL…manuallyReloadAndUpdatePlugins`), backup now / restore (`backup_key`/`restore_key`, path), automatic periodic backup (`automatic_backup_key` → `BackupWorkManager.enqueuePeriodicWork` — SU:97), APK installer choice, show-logcat |
| **Extensions** | `ExtensionsFragment` (host) → `PluginsFragment` per repo + `PluginDetailsFragment` sheet | repo add/remove (`REPOSITORIES_KEY`), browse repo plugin list, install/uninstall/update, per-plugin gear (openSettings — doc 11 §4) — flow in doc 04 |

Plus **Setup** wizard graph (`ui/setup/`: Language → ProviderLanguage → Media → Layout → Extensions) gated by `HAS_DONE_SETUP` (§4.8), auto-shown when no plugins installed (`MainActivity.kt:2020-2032`).

---

## 10. Crash handling & safe mode

### 10.1 Crash handler (no ACRA)

`ExceptionHandler(errorFile, onError) : Thread.UncaughtExceptionHandler` (`CloudStreamApp.kt:41-68`):
- Writes to `filesDir/last_error`: **the currently-loading extension name** (`PluginManager.currentlyLoading`) + thread name/id + stacktrace (`CSA:55-59`).
- Then restarts the launch activity via `makeRestartActivityTask` and `exitProcess(1)` (`CSA:62-66`).

### 10.2 Crash-loop detection

Next launch: `MainActivity.setLastError` reads `last_error` into the static; the file is deleted after read (`MA:208-218`). In `onCreate`: `else if (lastError == null)` guards the whole plugin-loading block — **a previous crash suppresses all plugin loading for that launch** (`MA:1350`). The user sees a "Safe mode" AlertDialog with a "crash info" button showing the raw error text (`MA:1396-1411`). Since the file is deleted on read, one clean launch exits crash-safe-mode (plugins load again; if they crash again you're back — the user must uninstall the culprit from Extensions while in safe mode).

### 10.3 The `safe` file

`PluginManager.checkSafeModeFile()`: any file named `safe` (case-insensitive) in `/sdcard/Cloudstream3/` → toast `safe_mode_file` and skip plugin loading (`PluginManager.kt:570-588`, folder const `CLOUD_STREAM_FOLDER` at 186-187). `isSafeMode() = checkSafeModeFile() || lastError != null` (571-573) — consulted by the download queue too (§6.4). This is the **user-controlled** escape hatch (create the file with any file manager) vs the automatic `last_error` path.

`[note]` Safe mode skips plugin *loading* but the app still runs with zero providers (plus `noneApi`); `loadSinglePlugin` is also skipped (branch at `MA:1346`).

---

## 11. ANI-KUTA mapping preview

(Deep dives are docs 14/15's job; light-touch pass over `/home/z/ANI-KUTA-WORK/ANI-KUTA/ANI-KUTA/APP/ani-kuta/` — module list in `settings.gradle.kts`; persistence surfaces: `core/database` (SQLDelight), `core/preferences`, `core/watch-progress`, `data/extension` (aniyomi-style).)

- **Two persistence philosophies.** CS3: **JSON blobs of provider DTOs** in one SharedPreferences (`FavoritesData` etc. = flattened `SearchResponse` snapshot, §5). ANI-KUTA: **normalized SQLDelight schema** — `library_category` + `library_item(main_id FK → main_entry)` (`core/database/.../library.sq:9-39`), watch progress in `watch_progress(episode_key PK, position, duration, completed, watch_count, first_watched_at, auto_mark_suppressed, user_marked_watched)` with partial indexes for O(log N) continue-watching (`watch.sq:9-46`). `[note → doc 15]` The CS3 shape means: zero migration cost when providers add fields (unknown fields dropped), but snapshots go stale and there is no cross-provider identity (dedupe is a user-driven dialog, `ResultViewModel2.kt:999-1079`). Our normalized schema needs an explicit "provider DTO cache" table if we want CS3-style display data without re-loading — CS3 gets that for free.
- **Identity: URL-hash vs composite key.** CS3 ids are `String.hashCode()` of provider-relative URLs (`ResultViewModel2.kt:376-379`) — 32-bit collision-prone, provider-specific, no cross-provider join (that's what `syncData` tracker ids are for). ANI-KUTA already uses a content-identity layer (`main_entry`/`main_id`) — strictly better; keep provider DTOs keyed by `(extensionId, providerKey)` pair. `[gap → doc 15]` decide whether CS3-plugin favorites map onto `main_entry` directly or via a `provider_item` join.
- **Registry.** CS3: static `APIHolder.allProviders` + `apis` + apiMap, events for reload, custom-clone re-instantiation (`MainAPI.kt:109-172`; `MainActivity.kt:810-844`). ANI-KUTA: Hilt-scoped `ExtensionManager` (`data/extension/manager/ExtensionManager.kt`) with `AnimeExtension` model incl. `isEnabled` — **we already have the per-provider enable/disable CS3 lacks** (§0.4); doc 14 should map `registerMainAPI` → our provider registration and decide the "disable = deregister vs hide" semantics (CS3 unloads the classloader entirely — aggressive but consistent).
- **Startup orchestration.** CS3's three-step eager pipeline + first-provider-fast (`MA:1350-1391`) + safe-mode gate is a good blueprint; our equivalent = Application-level Hilt graph + lazy per-screen loading; we have no crash-loop plugin story yet — **adopt the `last_error`-with-currently-loading-plugin trick + safe file** (cheap, high value). `[note → doc 17]`
- **APIRepository wrapper.** The timeout-hard-kill + Resource-wrap + tiny ring cache pattern (`APIRepository.kt`) ports cleanly to our Cloud Screen repositories; CS3's 20-entry/10-min cache is a sensible default for detail screens (doc 07/15).
- **Subscriptions.** CS3's model is exactly "load each subscribed show, diff episode counts, notify" — 6 h WorkManager + foreground worker + force-plugin-load (`SWM`). ANI-KUTA has `episodeSchedule.sq`/`animeUpdateState.sq`/`notifications.sq` in the DB already — richer than CS3's prefs-only bookkeeping; doc 17 should spec notifications per channel (CS3's 6 channels are a decent taxonomy).
- **Accounts.** CS3's profile = key-prefix namespace + tracker login registry (`AccountManager`); ANI-KUTA is single-profile today (profile module exists at `app/…/profile`) — if multi-profile ever happens, CS3's prefix trick is the cheap version of what our normalized DB would do with a `profile_id` column. `[gap]`
- **Settings.** CS3 settings IA (§9) vs our Compose settings — the Providers screen's "test providers" runner (`ui/settings/testing/`) is worth stealing for our Cloud Screen health view. `[note → doc 16/17]`
- **`[gap]` inventory for docs 14/15/17**: (a) JSON-blob → Room/SQLDelight DTO-cache mapping; (b) stable ids for CS3 items (don't adopt URL-hash); (c) enable/disable semantics; (d) crash-loop safe mode; (e) auto-download of plugins (AutoDownloadMode) — deferred per doc 10 recommendation; (f) bookmark WatchType 5-state model vs our category system (`library_category` already covers it, richer).

---

## 12. Unverified / open questions

1. `KEY_RESULT_SORT = "result_sort"` (`DSH:59`) — no consumers found in app or library at this commit; possibly vestigial from an old episode-sort system (replaced by `results_sorting_mode`). `[inferred]`
2. Whether any *plugin* in the ecosystem writes the watch/library keys directly (they can — doc 11 §5 showed full storage access); not surveyed here (doc 12 census found no settings-bearing plugins besides SyncPlugin, so [inferred] unlikely).
3. `Provider_API`/`PLUGINS_KEY` interplay with the "cloned site" feature across *local* plugins — `onAllPluginsLoaded` matches clones against `allProviders` by class simple-name; if the parent plugin is local and not yet loaded when the event fires, the clone silently misses. Order-dependence [inferred] — not tested.
4. Exact `DownloadedFileInfo`/`KEY_DOWNLOAD_INFO` record shape (VDM:198) — doc 09 pipeline scope; only key name verified here.
5. `REPOSITORIES_KEY` write sites (Extensions UI) and repo JSON shape — cited from doc 04 rather than re-derived.
6. ANI-KUTA `core/preferences` key inventory — deferred to doc 15 (light touch only here, per assignment).
