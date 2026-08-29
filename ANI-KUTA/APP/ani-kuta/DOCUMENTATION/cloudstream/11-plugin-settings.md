# 11. Plugin Settings & Plugin-UI System — How CS3 Plugins Declare, Store and Render Settings

> Research doc 11 (batch B3-a). Scope: the **plugin settings system** as it actually exists in the
> CS3 codebase at snapshot commit `efc1915` (2026-08-28): the `Plugin`/`BasePlugin` API surface,
> the `openSettings` mechanism (the *only* settings/UI hook), the storage backend plugins write to
> (`DataStore` → `rebuild_preference` SharedPreferences), the three app-side entry points that
> render the settings affordance, the `BlankFragment`/custom-view pattern for plugin-contributed
> UI screens, what settings real plugins in our repos expose, security/stability, and the ANI-KUTA
> mapping.
>
> **⚠ Correction to prior assumptions** (task brief + research tracker line 60 mention a
> "Plugin settings DSL / ProviderSettings"): at this commit **there is NO `preferences` property,
> no settings DSL and no `ProviderSettings` class anywhere in the app or library**. Doc 03 §5.1
> already flagged this ("There is NO preferences API in the current library — repo-wide grep =
> zero hits"; the 4.0-era preference DSL is gone) — re-verified here with fresh greps
> (`val preferences|var preferences|fun preferences|getPreferences|preferences:` and
> `PreferenceScreen|PreferenceFragmentCompat|preregister` across `research/cloudstream/` → only
> hits are the app's *own* AndroidX preference screens and `DataStore.getPreferences`, which is
> storage, not a plugin API). `[verified]` The real system is: **`openSettings` lambda + a
> plugin-authored Fragment/Dialog + app-wide SharedPreferences for storage.**
>
> Doc 02 §6.3 has a one-paragraph summary of `openSettings`/`BlankFragment` — this doc goes deep
> and does not repeat doc 02's format forensics. Doc 10 §2 owns the *app's own* provider settings
> UI (`SettingsProviders`, `prefer_media_type_key_2` etc.) — cited where contrasted.
>
> Sources (read-only): `research/cloudstream/` (app + library), `research/TestPlugins/`
> (official template), `research/CakesTwix-ext/`, `research/storm-ext/`, `research/extensions/`,
> `research/csdocs/`.
>
> Marker conventions: `[verified]` = read in source with line numbers; `[inferred]` = reasoned
> from code but not directly observed; `[docs]` = cited from a previous doc in this series;
> `[open-question]` = unresolved design question for ANI-KUTA; `[gap]` = missing capability.

**File abbreviations** (all under `research/cloudstream/` unless noted):
`BP` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/plugins/BasePlugin.kt` ·
`Plugin.kt` = `app/src/main/java/com/lagradost/cloudstream3/plugins/Plugin.kt` ·
`PM` = `app/src/main/java/com/lagradost/cloudstream3/plugins/PluginManager.kt` ·
`DS` = `app/src/main/java/com/lagradost/cloudstream3/utils/DataStore.kt` ·
`CSA` = `app/src/main/java/com/lagradost/cloudstream3/CloudStreamApp.kt` ·
`PDF` = `app/.../ui/settings/extensions/PluginDetailsFragment.kt` ·
`PA` = `app/.../ui/settings/extensions/PluginAdapter.kt` ·
`EF` = `app/.../ui/settings/extensions/ExtensionsFragment.kt` ·
`HF` = `app/.../ui/home/HomeFragment.kt` ·
`SF` = `app/.../ui/settings/SettingsFragment.kt` ·
`BF` = `app/.../ui/BaseFragment.kt` ·
`VCA` = `app/.../actions/VideoClickAction.kt` ·
`MA` = `app/src/main/java/com/lagradost/cloudstream3/MainActivity.kt` ·
`ExP` = `research/TestPlugins/ExampleProvider/src/main/kotlin/com/example/` ·
`Sync` = `research/CakesTwix-ext/SyncPlugin/src/main/kotlin/com/lagradost/sync/`.

---

## 0. The system in one paragraph

A CS3 plugin has exactly **one** settings/UI extension point: `Plugin.openSettings`, a nullable
`((context: Context) -> Unit)` lambda the plugin assigns inside `load(context)`
(`Plugin.kt:36-39`). The app does **not** render plugin settings itself — wherever it finds a
*loaded* plugin instance whose `openSettings != null`, it shows a gear button and simply
**invokes the plugin's lambda with a `Context`** (three call sites: extension details bottom
sheet, extension list row, home provider picker). The lambda is expected to show a
`Fragment`/`BottomSheetDialog`/`AlertDialog` built by the plugin — layouts inflated from the
plugin's own bundled resources (`requiresResources = true` + `Resources.getIdentifier`), or plain
programmatic views. Storage is **not** plugin-scoped: plugins call the app's global
`DataStore`/`CloudStreamApp.getKey/setKey` helpers, which write JSON-serialized values into the
app-wide `rebuild_preference` SharedPreferences file — the same file the app itself uses — with
only naming conventions (a `CLOUDSYNC_`-style prefix or `folder/key` path) for separation.

---

## 1. The Plugin class API surface

### 1.1 `BasePlugin` (library, `commonMain` — platform-neutral)

`BP:14-78` — abstract class, no constructor parameters, no state except `filename`:

```kotlin
abstract class BasePlugin {
    fun registerMainAPI(element: MainAPI)          // BP:20-25 — adds to APIHolder.allProviders
                                                   //   + stamps element.sourcePlugin = filename
    fun registerExtractorAPI(element: ExtractorApi) // BP:31-35 — adds to utils.extractorApis
    @Throws(Throwable::class)
    open fun beforeUnload() {}                      // BP:40-42 — called on unload
    @Throws(Throwable::class)
    open fun load() {}                              // BP:47-49 — cross-platform load hook
    @Deprecated("Renamed to `filename` ...", level = DeprecationLevel.ERROR)
    var __filename: String? ...                     // BP:51-61
    var filename: String? = null                    // BP:62 — full file path to the .cs3
    @Serializable class Manifest { name, pluginClassName, requiresResources, version } // BP:64-77
}
```

Deliberately **no `Context`, no preferences, no resources** at this level — `commonMain` is the
KMP-shared source set (doc 02 §5.5) `[docs]`. `[verified]`

### 1.2 `Plugin` (app, Android-only — adds the UI/settings surface)

`Plugin.kt:10-40` — the *complete* file (this is the whole plugin-facing API beyond BasePlugin):

```kotlin
abstract class Plugin : BasePlugin() {
    /** Called when your Plugin is loaded @param context Context */
    @Throws(Throwable::class)
    open fun load(context: Context) {   // Plugin.kt:15-19 — falls back to cross-platform load()
        load()
    }
    /** Used to register VideoClickAction instances */
    fun registerVideoClickAction(element: VideoClickAction) {   // Plugin.kt:25-29
        element.sourcePlugin = this.filename
        VideoClickActionHolder.allVideoClickActions.add(element)
    }
    /** This will contain your resources if you specified requiresResources in gradle */
    var resources: Resources? = null      // Plugin.kt:34
    /** This will add a button in the settings allowing you to add custom settings */
    var openSettings: ((context: Context) -> Unit)? = null   // Plugin.kt:39
}
```

`[verified]` What the app injects/gives the plugin instance:

| Member | Meaning | Set by |
|---|---|---|
| `filename` (BasePlugin) | absolute path of the loaded `.cs3` | `PM:644` before `load()` |
| `resources` | plugin's own `Resources` (bundled `res/`), **null unless `requiresResources = true`** | `PM:645-659` via reflection `AssetManager.addAssetPath` |
| `load(context)` argument | **`MainActivity`** (`AppCompatActivity`, `MA:197`) — passed down from `___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(this@MainActivity)` (`MA:1364-1368`, also 1387 for local plugins) | `PM:669-673` |
| `registerMainAPI` / `registerExtractorAPI` / `registerVideoClickAction` | registration funnels into app-global holders | `BP:20-35`, `Plugin.kt:25-29` |
| `beforeUnload()` | cleanup hook (see §7.4) | `PM:697-701` |

Anything else a plugin wants (HTTP clients, DataStore, `MainActivity` events, toasts) it reaches
**directly through the parent classloader** — `PathClassLoader(filePath, context.classLoader)` is
parent-first (`PM:611`), so every app class (`CloudStreamApp`, `utils.DataStore`,
`CommonActivity.showToast`, even `MainActivity.bookmarksUpdatedEvent`) is importable from plugin
code. Real example: `Sync/CloudSyncPlugin.kt:8-15` imports `CloudStreamApp`,
`MainActivity`, `CommonActivity.showToast`, `utils.DataStore.getSharedPrefs` — all *app-module*
classes. `[verified]` (Consequence: a plugin binary compiled against the app module only runs on
that app — see §8.)

`@CloudstreamPlugin` itself is a fieldless marker annotation (doc 03 §2.1) — it contributes
nothing to the settings system. `[docs]`

### 1.3 Lifecycle entry points relevant to settings

- `load(context)` is where a plugin **must** assign `openSettings` (it's read lazily later by UI
  code, but the plugin instance is created fresh per load). Dispatch: `PM:669-673` —
  `if (pluginInstance is Plugin) pluginInstance.load(context) else pluginInstance.load()`.
  Cross-platform plugins (`BasePlugin`, e.g. `research/extensions/TwitchProvider/.../TwitchPlugin.kt:7-13`)
  **cannot** have settings at all — `openSettings` exists only on the app-side `Plugin`.
  `[verified]`
- `assertNonRecursiveCallstack()` (`PM:447-452`) throws if anything on the current stack is
  already inside `loadPlugin` — guards against a plugin re-triggering plugin loading from its own
  `load()` (infinite loop). The scary `___DO_NOT_CALL_FROM_A_PLUGIN_*` names encode the same
  rule. `[verified]`
- Hot reload: `___DO_NOT_CALL_FROM_A_PLUGIN_hotReloadAllLocalPlugins` (`PM:485-494`) unloads all
  local plugins then reloads them — the moment `beforeUnload()` matters (§7.4). `[verified]`

---

## 2. How plugins declare settings — the real mechanism

### 2.1 There is no declaration API — only a "show your own UI" lambda

The plugin does **not** describe settings (no key/type/default schema, no PreferenceScreen
builder, no annotations). The entire contract is:

```kotlin
// ExP/ExamplePlugin.kt:8-24 — the official template, verbatim
@CloudstreamPlugin
class ExamplePlugin: Plugin() {
    private var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity      // stash MainActivity for later

        // All providers should be added in this manner
        registerMainAPI(ExampleProvider())

        openSettings = {                              // ← THE settings declaration
            val frag = BlankFragment(this)
            activity?.let {
                frag.show(it.supportFragmentManager, "Frag")
            }
        }
    }
}
```

`[verified]` Pattern breakdown:

1. **Stash the Activity** — the lambda later receives only a generic `Context` (from
   `requireContext()`/`itemView.context` at the call sites, §4), so the template saves the
   `AppCompatActivity` captured at `load()` time to have a `SupportFragmentManager` to show a
   `DialogFragment` with (`ExamplePlugin.kt:10,13,20-22`).
2. **Assign `openSettings`** — any lambda `(Context) -> Unit`. Showing a Fragment is the norm but
   a plain `AlertDialog` or `BottomSheetDialog` works too (see §2.3).
3. **No framework involvement** — the app never learns *what* the settings are; it only knows
   "settings exist" (`openSettings != null`).

### 2.2 The UI is *anything the plugin can draw* — two real styles

**Style A — plugin-resource `BottomSheetDialogFragment` (official template).**
`ExP/BlankFragment.kt:24-93` — a `BottomSheetDialogFragment(private val plugin: ExamplePlugin)`
that inflates a layout shipped *inside the .cs3*:

```kotlin
override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    // Inflate the layout for this fragment
    val layoutId = plugin.resources?.getIdentifier("fragment_blank", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
    return layoutId?.let {
        inflater.inflate(plugin.resources?.getLayout(it), container, false)   // BlankFragment.kt:56-59
    }
}
```

Because the plugin's `res/` lives in the plugin zip (not the app), everything is resolved **by
name** through `plugin.resources` with sibling helpers `getDrawable(name)` / `getString(name)` /
`View.findViewByName(name)` (`BlankFragment.kt:27-47`), each annotated
`@SuppressLint("DiscouragedApi")` (`getIdentifier` is reflection-slow and lint-discouraged).
The layout is a plain `LinearLayout` with two `TextView`s and two `ImageView`s
(`ExP/src/main/res/layout/fragment_blank.xml:1-43`); localization ships as normal resource
qualifiers (`values-pl/strings.xml` — "Witaj zabawny fragmencie!!"). The fragment can freely mix
**app** resources alongside plugin ones (`R.style.ResultInfoText`, `R.string.legal_notice_text`,
`colorFromAttribute` — `BlankFragment.kt:76-92`) because app classes/resources resolve through
the parent classloader. Requires `requiresResources = true` in the gradle block +
`viewBinding`/`buildConfig` buildFeatures (`ExP/build.gradle.kts:26,33-37`). `[verified]`

**Style B — hand-rolled view + `BottomSheetDialog` (the only real-world settings UI in our
repos).** CakesTwix's SyncPlugin (`research/CakesTwix-ext/SyncPlugin/`) implements a full
settings screen *without* any Fragment subclass:

```kotlin
// Sync/SyncSettings.kt:36-59
class SyncSettings(private val plugin: CloudSyncPlugin) {
    private val res: Resources = plugin.resources ?: throw Exception("Unable to read resources")
    private var dialog: BottomSheetDialog? = null
    ...
    fun show(context: Context) {
        val inflater = LayoutInflater.from(context)
        val view = buildView(inflater, context)       // inflates plugin layout "settings"
        val d = BottomSheetDialog(context)
        d.setContentView(view)
        d.setOnDismissListener { scope.cancel() }     // cancels the dialog's coroutine scope
        d.show()
        ... // force STATE_EXPANDED
    }
}

// Sync/CloudSyncPlugin.kt:495-500 — the lambda just delegates
openSettings = { ctx ->
    val act = ctx as? AppCompatActivity
    if (act != null && !act.isFinishing && !act.isDestroyed) {
        SyncSettings(this).show(act)
    }
}
```

`buildView` (`SyncSettings.kt:90-201`) wires plugin-layout `Switch`es, `Button`s and an
`ImageView` "save" toolbar, adds per-category rows dynamically (`getLayout("sync_cat_row", …)`
inflated per item, `SyncSettings.kt:163-189`), opens a credentials `AlertDialog` with three
`EditText`s (`SyncSettings.kt:295-359`), and repaints a live status card + device list from a
coroutine (`SyncSettings.kt:195-198,383-445`). TV support is manual — every focusable gets
`makeTvCompatible()` which swaps in an "outline" drawable background (`SyncSettings.kt:84-87`).
Layouts ship in the plugin (`res/layout/settings.xml`, `sync_cat_row.xml`, `sync_creds.xml`,
`sync_device.xml`). `[verified]`

### 2.3 Complete annotated example (canonical minimal pattern)

```kotlin
@CloudstreamPlugin
class MyPlugin : Plugin() {
    private var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity            // ① MainActivity arrives here (PM:669-673)
        registerMainAPI(MyProvider())                        // ② provider registered first
        openSettings = { ctx ->                              // ③ lambda invoked by the app's gear button
            val act = (ctx as? AppCompatActivity) ?: activity ?: return@openSettings
            val prefs = act.getSharedPrefs()                 // ④ storage = app-wide SharedPreferences (§3)
            val current = act.getKey<Boolean>("mypref/enabled") ?: false
            AlertDialog.Builder(act)                          // ⑤ any UI: dialog, fragment, bottom sheet
                .setTitle("My settings")
                .setMultiChoiceItems(arrayOf("Enable X"), booleanArrayOf(current)) { _, _, checked ->
                    act.setKey("mypref/enabled", checked)     // ⑥ persist immediately, no commit button
                }
                .show()
        }
    }
}
```

Steps ④⑥ are the `utils.DataStore` extensions (§3); the provider later reads the same keys via
`getKey` (there is **no** `provider.preferences` — the provider class and settings storage are
connected only by convention, both running in-process). Note the template's own provider
(`ExP/ExampleProvider.kt:7-20`) reads no settings — a settings-less provider is the ecosystem
default (§6). `[verified]` (pattern assembled from `ExamplePlugin.kt`, `CloudSyncPlugin.kt`,
`SyncStorage.kt`)

---

## 3. Storage mechanism

### 3.1 What backs plugin persistence: the app's `DataStore` object → SharedPreferences

Plugins store settings through the **app's global storage helpers**, not anything plugin-scoped:

- `Context.getSharedPrefs()` → `context.getSharedPreferences("rebuild_preference", MODE_PRIVATE)`
  (`DS:26` `const val PREFERENCES_NAME = "rebuild_preference"`, `DS:103-109`). Every
  `setKey`/`getKey` reads/writes **this single app-wide file**.
- `CloudStreamApp.Companion.getKey/setKey/getKeys/removeKey` (`CSA:119-165`) — static façade over
  the same `DataStore` extensions using a `WeakReference` app context (`CSA:111-117`); this is
  what `SyncStorage` calls from non-Context code (`Sync/SyncStorage.kt:3-4`).
- Serialization: `setKey` stores values **JSON-serialized** (`putString(path, value?.toJsonLiteral())`,
  `DS:173-181`); `getKey` re-parses with `parseJson<T>` (`DS:221-232`). Types survive round-trips
  (String/Int/Boolean/Long/Float/arbitrary `@Serializable`/Gson-friendly classes — SyncStorage
  stores a whole `SyncCreds` object under one key, `SyncStorage.kt:10-14`).
- A second file exists: `Context.getDefaultSharedPrefs()` → `PreferenceManager.getDefaultSharedPreferences`
  (`DS:122-124`) — the AndroidX-Preference file where the *app's own* settings live
  (`settings_*.xml` screens). Plugins *can* touch it (SyncPlugin registers change listeners on
  both files, `CloudSyncPlugin.kt:542-561`) — see §7.2.
- The only namespacing tool: a **string-prefix convention** — `setKey(folder, path)` concatenates
  `"folder/path"` (`getFolderName`, `DS:111-113`); `getKeys(folder)` lists keys by prefix
  (`DS:126-130`); `removeKeys(folder)` bulk-deletes a folder (`DS:158-171`). The app itself uses
  account-prefixed folders (`setKey("$currentAccount/$RESULT_WATCH_STATE_DATA", id, data)` —
  `DataStoreHelper.kt:618` and ~10 sibling call sites), and SyncPlugin uses a flat
  `CLOUDSYNC_*` prefix (`SyncStorage.kt:11,17,23,40,48`). **Nothing enforces a plugin prefix** —
  plugins pick their own and collisions with app keys are possible. `[verified]`

Physical location (standard Android): `/data/data/com.lagradost.cloudstream3/shared_prefs/rebuild_preference.xml`
`[inferred]` (from `getSharedPreferences` semantics; not observed on a device).

Evidence that plugins use this directly: the `DataStore.mapper` field is deprecated with an
error level and a comment addressing *extensions* — "Extensions shouldn't have really been using
this version of it… you can use the stable-API version of the mapper at
com.lagradost.cloudstream3.mapper" (`DS:90-101`) — i.e. the maintainers expect plugins inside
`utils.DataStore`. `[verified]`

### 3.2 Where a settings value lands, end to end

```
plugin UI Switch toggled                    SyncSettings.kt:111-121
  → sm.creds = creds                        (SyncStorage.kt:12-13 setter)
    → CloudStreamApp.setKey("CLOUDSYNC_CREDS", value)   CSA:131-133
      → context.setKey(path, value)          DS:173-181
        → getSharedPrefs().edit { putString("CLOUDSYNC_CREDS", JSON) }   DS:103-109
          → shared_prefs/rebuild_preference.xml
provider/API code later: context.getKey<SyncCreds>("CLOUDSYNC_CREDS")    DS:230-232
```

`[verified]` (each hop read in source; the XML file itself not opened on device)

### 3.3 Cross-platform behavior

- The storage layer is **app-module, Android-only** — `utils/DataStore.kt` lives in
  `app/src/main/...`, imports `androidx.preference.PreferenceManager`, and is not part of the KMP
  library at all. The library's `commonMain`/`jvmMain`/`webMain` source sets have no preferences
  API (grep = zero hits; doc 02 §5.5). `[verified]`
- Therefore: **cross-platform plugins (BasePlugin, `.jar` builds, `isCrossPlatform = true` — e.g.
  `extensions/TwitchProvider/build.gradle.kts:22`) cannot persist or expose settings**; only
  Android `Plugin` subclasses can, via the app-module helpers. The desktop/web story for plugin
  settings simply does not exist at this commit. `[inferred]` from the above.

---

## 4. How the app renders plugin settings

The app renders **only the affordance** (a gear), never the settings content. Three call sites,
all guarded by `openSettings != null` **and** the plugin being currently loaded:

### 4.1 Entry point 1 — Extensions → plugin details bottom sheet

Navigation path: Settings screen (`SF:223-242`, `settingsExtensions` row → nav action at
`SF:231`) → **ExtensionsFragment** (`EF:46`) which lists repositories + downloaded-plugin stats
and hosts a `PluginAdapter` (`EF:192-204`); tapping a *remote* plugin row opens
**`PluginDetailsFragment`**, a `BaseBottomSheetDialogFragment` showing icon/name/version/author/
status/tvTypes/language/votes (`PDF:31,56-94`):

```kotlin
if (data.isDownloaded) {
    val plugin = (PluginManager.urlPlugins[metadata.url] ?: PluginManager.plugins[metadata.url])
        as? com.lagradost.cloudstream3.plugins.Plugin        // PDF:98-99
    if (plugin?.openSettings != null && context != null) {
        actionSettings.isVisible = true                      // PDF:100-101 — gear ImageView
        actionSettings.setOnClickListener {
            try {
                plugin.openSettings!!.invoke(requireContext())   // PDF:104 — plain invoke
            } catch (e: Throwable) {
                Log.e("PluginAdapter", "Failed to open ${metadata.name} settings: …")  // PDF:105-112
            }
        }
    } else actionSettings.isVisible = false                  // PDF:114-116
}
```

Notes: (a) the plugin instance is fetched from the live `PluginManager.plugins`/`urlPlugins`
maps keyed by file path/URL — a downloaded-but-not-loaded plugin shows no gear; (b) failures are
caught and only logged (no user-visible crash) — see §7.1; (c) the gear lives in
`fragment_plugin_details.xml:47` (`@+id/action_settings`). `[verified]`

### 4.2 Entry point 2 — extension list row gear (local/downloaded plugins page)

`PluginAdapter.onBindContent` shows the gear directly on the row for downloaded plugins
(`PA:119-144`): same plugin-instance lookup (`PA:121-123`), same try/catch-invoke
(`PA:125-138`, log tag "PluginAdapter", "Failed to open … settings"). This adapter is used both
by ExtensionsFragment (repo rows, `showRepositoryNames = true`, `EF:200`) and by
**PluginsFragment**, reached from the "plugin storage" appbar (`EF:183-190`,
`PluginsFragment.newLocalInstance`) or per-repository (`EF:116-120`, `PluginsFragment.newInstance`).
`[verified]`

### 4.3 Entry point 3 — home provider picker gear

In the home "switch provider" dialog, each provider row gets a gear when its plugin has settings
— `HF:450-463`:

```kotlin
val pluginInstance = providerApi.sourcePlugin?.let { PluginManager.plugins[it] } as? Plugin
val isDownloadedPluginWithSettings = pluginInstance?.openSettings != null && !isLayout(TV)
settingsIcon.visibility = if (isDownloadedPluginWithSettings) View.VISIBLE else View.GONE
if (isDownloadedPluginWithSettings) {
    settingsIcon.setOnClickListener {
        try {
            val activityContext = it.context.getActivity() ?: it.context
            pluginInstance.openSettings?.invoke(activityContext)
        } catch (e: Throwable) { logError(e) }
    }
}
```

Notable: the chain runs provider → `MainAPI.sourcePlugin` (the plugin *file path* string,
stamped by `registerMainAPI`, `BP:22`) → `PluginManager.plugins[path]` → cast to app-side
`Plugin` → check `openSettings`. **Hidden on TV layouts** (`!isLayout(TV)`, `HF:451`) — on TV
the gear is dropped rather than restyled (SyncPlugin compensates by hand in its own UI, §2.2).
`[verified]`

### 4.4 Container, back-navigation, theming

- **Container**: whatever the plugin's lambda draws — no shared host activity/fragment exists
  for plugin settings. Both real styles are *dialogs* (`BottomSheetDialogFragment.show(supportFragmentManager,…)`
  in the template; `BottomSheetDialog` in SyncPlugin), so back-navigation is dialog dismissal
  (swipe-down/back) — there is **no settings nav-stack entry**, no up-affordance, no
  `onBackPressed` wiring by the app. `[verified]`
- **Theming**: none is provided by the app. Plugin UIs must style themselves; the template leans
  on *app* styles/attrs (`R.style.ResultInfoText`, `colorFromAttribute(R.attr.white)` —
  `BlankFragment.kt:78-91`), which couples plugin visuals to host theme internals. SyncPlugin
  hardcodes colors (`0xFF4CAF50`, `SyncSettings.kt:247`) and does TV focus outlines manually.
  `[verified]`
- **Contrast — the app's own settings** are classic AndroidX Preferences: every built-in screen
  extends `BasePreferenceFragmentCompat` (`BF:268-278`, a thin `PreferenceFragmentCompat` with
  system-bar padding) inflating `res/xml/settings_*.xml` `<PreferenceScreen>` trees
  (`SettingsProviders.kt:23`, `settings_providers.xml:2-31`, …). Plugin settings share **zero**
  of this machinery — no `PreferenceFragmentCompat`, no `PreferenceScreen` builder is exposed to
  plugins (grep = zero plugin-reachable hits). `[verified]`
- The per-provider *app-side* settings that do exist (enable/disable, NSFW, dub/sub, language —
  `prefer_media_type_key_2` & friends) are documented in doc 10 §2 and are **not** plugin-declared;
  they apply to every provider uniformly. `[docs]`

---

## 5. Fragment UI plugins — the "custom UI" capability

### 5.1 What it is

`openSettings` is the *only* sanctioned channel for a plugin to contribute UI, and it is
generic enough that a plugin can show **arbitrary full-screen-capable Fragments**, not just
settings: the lambda receives a `Context` that in every call site is (or wraps) the
`MainActivity`, and the plugin can `supportFragmentManager.beginTransaction()…` any Fragment
it ships, inflating its own layouts. The template demonstrates the complete resource dance
(`BlankFragment.kt`): layout + drawables + localized strings all live in the .cs3
(`res/layout/fragment_blank.xml`, `res/drawable/ic_android_24dp.xml`, `values*/strings.xml`)
and are looked up by name via `plugin.resources` (`BlankFragment.kt:29-47,56-59`). The mechanism
prerequisites: `requiresResources = true` (so `PM:645-659` builds the plugin `Resources` via the
reflection `AssetManager.addAssetPath` trick) and — because the Fragment class is instantiated
by the *app's* FragmentManager — the fragment must be a plain `androidx.fragment.app.Fragment`
compatible with the host's FragmentManager. `[verified]`

### 5.2 Hosting model & limits

- The plugin Fragment lives in the **app's `MainActivity.supportFragmentManager`** — the app
  does not create any separate container/activity for plugin UI. A dialog fragment shows over
  whatever screen is current. `[verified]`
- Nothing routes the plugin UI into navigation (no nav-graph destination, no deep link); it is
  fire-and-forget from the gear click. `[verified]`
- A plugin *cannot* inject UI outside `openSettings` through this mechanism — there is no
  "registerFragment" API. The other UI-capable extension point is `VideoClickAction`
  (`Plugin.kt:25-29`): actions surfacing in the player's source dialog, which may run UI code via
  `uiThread`/`launch` helpers (`VCA:104-163`) — but those launch *external activities* /
  show chooser dialogs rather than contribute screens. `[verified]`
- **Use cases seen in the wild** (our snapshot): only the two in §2 — the template demo fragment
  and SyncPlugin's sync dashboard (status card, per-category backup/restore matrix, credentials
  dialog, device list with removal — effectively a whole sub-app UI in a bottom sheet,
  `SyncSettings.kt:90-455`). This confirms the capability is real but rarely exercised. `[verified]`

---

## 6. What settings real plugins expose — survey

Census across all plugin classes in our research repos (`rg -l ': Plugin\(\)'` = **57 Android
plugin classes**: storm-ext ×35, CakesTwix ×21, MegaRepo ×1; plus **7 cross-platform
`BasePlugin()` classes** in `extensions/` ×5 and Luna712 ×2, which structurally cannot have
settings; `openSettings` present in **exactly 1** of the 57):

| Plugin (repo) | Class extends | Settings UI? | Settings exposed | Storage |
|---|---|---|---|---|
| **CakesTwix SyncPlugin** (`Sync/CloudSyncPlugin.kt:33`) | `Plugin()` | ✅ `openSettings` → `SyncSettings` bottom sheet | sync server URL + bearer token + device name (creds dialog, `SyncSettings.kt:295-359`); device-local backup & restore master switches (`:108-138`); per-category backup/restore toggles ×5 (Extensions/Bookmarks/Resume/Search-history/Settings, `:152-189`); "sync now" button (`:141-150`); device list w/ deregister (`:383-445`); disconnect device | `CLOUDSYNC_CREDS`, `CLOUDSYNC_V2_MIGRATED`, `CLOUDSYNC_TS_*`, `CLOUDSYNC_HASH_*`, `CLOUDSYNC_SYNCED_KEYS_*` — flat keys in `rebuild_preference` via `CloudStreamApp.setKey` (`SyncStorage.kt:10-62`) |
| **Official template** (`ExP/ExamplePlugin.kt:9`) | `Plugin()` | ✅ demo only | none — `BlankFragment` shows hello-world text + icons | none |
| storm-ext ×35 (e.g. `AnimeflvnetProviderPlugin.kt:7-12`, DoramasFlix, Monoschinos…) | `Plugin()` | ❌ | — | — |
| CakesTwix provider plugins ×20 (BambooUA `BambooUAProviderPlugin.kt:7-13`, Uakino, KlonTV, HentaiUkr…) | `Plugin()` | ❌ | — | — |
| MegaRepo ×1 (`MegaPlugin.kt`) | `Plugin()` | ❌ | — | — |
| Official cross-platform `extensions/` ×5 (Twitch `TwitchPlugin.kt:6-13`, Dailymotion `DailymotionPlugin.kt:7`, YouTube, Invidious, InternetArchive) + Luna712 ×2 | `BasePlugin()` | ❌ **impossible** (no `openSettings` on BasePlugin) | — | — |

`[verified]` (each file read or grep-counted)

Key takeaways: (a) quality/server/language preferences — the settings the task brief predicted —
are **absent from this snapshot's ecosystem**; providers hardcode everything. (b) The single
real settings implementation is for *login credentials + sync policy*, i.e. data too rich for
the app's own provider settings. (c) Community plugin classes are otherwise 5-13-line
registration stubs. (d) csdocs developer docs contain **zero** mention of `openSettings`,
preferences or plugin settings (`rg -ni "openSettings|preference" csdocs/devs/` = no matches);
the user-facing `csdocs/Settings/` pages (Downloading/SrcPriority/Subtitle/stream/othersettings)
also never mention plugin settings — the feature is entirely undocumented officially.
`[verified]`

---

## 7. Security & stability considerations

### 7.1 Plugin UI code runs with full app privileges, contained only by try/catch

All three `openSettings` invoke sites wrap the call in `try { … } catch (e: Throwable) { log }`
(`PDF:103-113`, `PA:127-138`, `HF:455-461`) — crash *containment* only: the lambda already runs
in-process with the app's identity (network, storage, activities — e.g. SyncPlugin registers
app-wide `ActivityLifecycleCallbacks`, `CloudSyncPlugin.kt:576-631`). There is no sandbox, no
permission model for UI, no theme isolation. `[verified]`

### 7.2 Preferences storage has NO plugin isolation

`rebuild_preference` (and the default-preferences file) are shared by app + all plugins; keys
are flat strings. Any plugin can read/write **every** key — including the app's provider
settings and *other plugins'* keys. SyncPlugin demonstrates the full-trust model explicitly: it
enumerates `getSharedPrefs().all.keys + getDefaultSharedPrefs().all.keys` and classifies them
(`CloudSyncPlugin.kt:505-515`, `SyncBackup.kt:69-92`), and observes both files for changes
(`:542-561`). Its own code flags the consequence — the bearer token is stored in the general
host DataStore with a TODO to move it to Android Keystore/encrypted storage
(`SyncStorage.kt:8-9`). Cross-plugin key collision is possible and nothing detects it.
`[verified]`

### 7.3 Plugin fragments outlive their classloader

`unloadPlugin` (`PM:689-731`) calls `beforeUnload()`, deregisters providers/extractors/actions
and drops the plugin from `plugins`/`classLoaders`/`urlPlugins` — but it does **not** dismiss or
remove any Fragment/Dialog the plugin previously added to the app's FragmentManager. A plugin
Fragment still on screen (or in the FragmentManager's state, e.g. across a configuration change)
after unload references classes from a now-unreferenced `PathClassLoader`; state restoration of
such a fragment (`Fragment.instantiate` by class name) would fail with a classloader miss.
`[inferred]` — not observed at runtime, but follows directly from `PM:720-730` removing the
classloader map entry while the FragmentManager retains the fragment. In practice the exposure
window is small (dialog fragments die on dismissal; hot-reload is a dev flow), but ANI-KUTA's
equivalent must explicitly flush plugin UI on unload.

### 7.4 Leaks & cleanup discipline are the plugin's job

- The template's `ExamplePlugin` holds `activity: AppCompatActivity?` (`ExamplePlugin.kt:10`)
  forever and never overrides `beforeUnload()` — a leak-by-default pattern on hot reload.
- SyncPlugin is the counter-example: `beforeUnload()` → `cleanup()` cancels its scopes, unregisters
  *both* preference listeners from the exact context they were registered on (comment:
  "during hot reload they must be removed from the same SharedPreferences", `CloudSyncPlugin.kt:38-40`),
  detaches the bookmarks event observer, unregisters `ActivityLifecycleCallbacks` and nulls the
  activity reference (`:456-480`). Note the app merely *calls* `beforeUnload` and swallows its
  exceptions (`PM:697-701`) — no enforcement. `[verified]`
- Resource access via `Resources.getIdentifier` is reflection-based and lint-discouraged
  (`@SuppressLint("DiscouragedApi")`, `BlankFragment.kt:26-49`) — slow per lookup, and a
  `resources == null` (plugin forgot `requiresResources`) silently yields a blank fragment
  (`layoutId?.let{…}` returns null, `BlankFragment.kt:56-59`). `[verified]`

### 7.5 Reload-loop protection

`assertNonRecursiveCallstack()` throws an `Error` if any frame is `loadPlugin`
(`PM:447-452`) — a plugin trying to trigger plugin loading (from `load()` or from a settings
callback) gets a hard stop instead of an infinite loop/OOM. `[verified]`

---

## 8. ANI-KUTA mapping preview

Our current extension system (aniyomi-based) has **no per-extension settings UI**: extension
rows expose trust/enable controls only, and the D-298 language filter is app-side, single-select,
in-memory (doc 10 §8 inventory of `ExtensionsSettingsScreen.kt`). `[docs]` If we adopt CS3-style
plugins, the settings story needs the following (each mapped to what CS3 does above):

- **Per-plugin settings storage (namespaced)** — CS3's flat `rebuild_preference` + prefix
  convention (§3.1) is the *weak* half of its design; we should do better from day one: a
  DataStore scope per plugin (e.g. file `cs3_plugin_<internalName>` or a
  `cs3/<internalName>/<key>` folder prefix via our `PreferenceStore`, which already wraps
  SharedPreferences with `anikuta_prefs`). If we want drop-in compatibility with existing CS3
  plugins that import `com.lagradost.cloudstream3.utils.DataStore`, we must **provide that class
  with identical behavior** (they compile against the *app* module, §1.2) — decide compat-shim
  vs fork-and-rewrite. `[open-question]`
- **Settings host screen in Compose** — CS3's three gear entry points (extension details sheet,
  extension row, provider picker — §4) map cleanly onto our Compose surfaces (extension details
  `ModalBottomSheet`, extension list row trailing icon, source-picker sheet). But CS3 plugins
  expect to show **Android Fragments/Dialogs with Views** — our app is Compose-only. Options:
  (a) host a `FragmentContainerView`/`AndroidView` Fragment host activity purely for plugin UI
  (full compat, adds a Fragments dependency to a Compose app, needs the FragmentManager +
  supportFragmentManager on the host Activity — our activities are ComponentActivities today);
  (b) define our own **declarative settings DSL** (plugin declares keys/types/defaults; we render
  in Compose) for plugins *we* author, and accept that third-party CS3 plugins with custom
  Fragment UIs (like SyncPlugin) won't render; (c) hybrid — DSL for simple prefs, Fragment host
  for full UIs. This is the central design question of the CS3 settings feature.
  `[open-question]`
- **`openSettings` equivalent API shape** — even if we add a DSL, keeping an
  `openSettings: (Context) -> Unit`-style escape hatch preserves the "whole sub-app UI"
  capability (SyncPlugin shows how valuable it is). If we host Fragments, we must replicate the
  Context plumbing: CS3 passes **MainActivity** into `load(context)` (`PM:669-673`) and plugins
  stash/cast it to `AppCompatActivity` (`ExamplePlugin.kt:13`) — our host would need to expose
  a FragmentActivity. `[open-question]` `[gap]` (no Fragment infrastructure in our Compose app)
- **`requiresResources` support is a prerequisite for any layout-based plugin UI** — without the
  `AssetManager.addAssetPath` resource wiring (doc 02 §5.3, `PM:645-659`), plugin settings
  fragments can't inflate their layouts. Plan this together with resource support, not after.
  `[gap]`
- **Theming compatibility** — plugin fragments reach for host styles/attrs
  (`R.style.ResultInfoText`, `colorFromAttribute`, `R.attr.white` — `BlankFragment.kt:76-92`);
  we'd need to provide CloudStream-equivalent style/attr names or plugin UIs render unstyled.
  `[gap]`
- **Unload hygiene** — flush any plugin-hosted UI (dismiss dialogs, clear Fragment host) when a
  plugin is disabled/removed, unlike CS3 (§7.3); require the `beforeUnload` discipline SyncPlugin
  models (§7.4). `[recommendation]`
- **What NOT to port**: the flat shared prefs file, the silent try/catch-only error handling,
  the TV "just hide the gear" approach (`HF:451`) — we can render Compose settings fine on TV.
  `[recommendation]`

---

## 9. Unverified / could not confirm

- **The historical preference DSL** — doc 03 §5.1 states a 4.0-era plugin preference DSL
  existed and was removed; the snapshot is a shallow clone (`git rev-list --count HEAD` = 1,
  commit `efc1915`, 2026-08-28) so history could not be inspected to date the removal or quote
  the old API. `[docs]`/`[inferred]`
- **On-device behavior** — where `rebuild_preference.xml` physically lands, whether plugin
  fragments actually crash after unload (§7.3), and gear rendering on real TV hardware are
  reasoned from source, not executed. `[inferred]`
- **Whether any CS3 plugin anywhere exposes quality/server-choice settings** — the task brief
  predicted these from community repos; in our 6 repos × 57 plugins the answer is none (§6).
  Upstream repos we don't mirror may differ. `[gap]`
- **`PluginViewData`/`PluginWrapper` internals** of the extensions list (status flags, isDownloaded
  derivation) are doc 02/04 territory and were only consulted via `PA:73-144`. `[docs]`

---
## ✔ B5-b Verification Note (2026-08-29)
Checked: 20 claims sampled → 20 verified, 0 corrected, 0 flagged-stale. Consistency: ok.
Corrections: none.
Samples re-verified against source: `Plugin.kt:39` openSettings + full file 10-40; `BasePlugin.kt:14-78` incl. registerMainAPI :20-25 / registerExtractorAPI :31-35 / Manifest :64-77; `DataStore.kt:26,90-101,103-109,111-113,122-130,158-171,173-181,221-232` (PREFERENCES_NAME, mapper deprecation, JSON round-trip, folder keys); CloudStreamApp companion :111-164; `PluginManager.kt` :448/:485/:611/:644/:645-659/:669-673/:689/:698 (line cites exact ±1); MainActivity :197/:1368/:1387; the three gear call sites PDF:98-116 / PA:119-144 / HF:450-463; the 57-Android-Plugin census (storm ×35, CakesTwix ×21, Mega ×1) + 7 BasePlugin (extensions ×5, Luna712 ×2) + `openSettings` in exactly 1 of 57 — all grep-reproduced; SyncPlugin imports :8-15, openSettings :495-500, SyncSettings/SyncStorage CLOUDSYNC keys; TestPlugins ExamplePlugin/BlankFragment; `isCrossPlatform = true` at TwitchProvider/build.gradle.kts:22; csdocs/devs zero `openSettings|preference` hits; no `preferences` DSL in library (grep empty). BasePreferenceFragmentCompat at BF:268-279 confirmed.
