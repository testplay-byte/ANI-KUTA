# 08 — Extension System

> How Animiru loads, installs, trusts, and updates extensions. The
> extension metadata keys, ClassLoader strategy, trust management,
> installer backends, repo system, and a brief comparison with
> ANI-KUTA's approach.

## 1. What an "extension" is in Animiru

An Animiru extension is a **standalone APK** that:
- Declares the `<uses-feature android:name="tachiyomi.animeextension" />`
  feature in its manifest.
- Has a metadata field `tachiyomi.animeextension.class` pointing to one
  or more `AnimeSource` or `AnimeSourceFactory` subclasses.
- Is signed with a certificate that's either:
  - The Aniyomi/Mihon extension signing key (trusted by default), or
  - A user-approved custom key (trusted via `TrustExtension`).
- Has a `versionName` of the form `X.Y.Z` where `X.Y` is the
  `extensions-lib` version (must be in `[LIB_VERSION_MIN, LIB_VERSION_MAX]`).

The extension is loaded as a separate APK via a `PathClassLoader`, not
compiled into the host app. This lets extensions update independently
of the app.

## 2. Extension metadata keys

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:50-57
// AY -->
private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
private const val METADATA_SOURCE_FACTORY = "tachiyomi.animeextension.factory"
private const val METADATA_NSFW = "tachiyomi.animeextension.nsfw"
const val LIB_VERSION_MIN = 12
const val LIB_VERSION_MAX = 16
// <-- AY
```

| Key | Purpose |
|-----|---------|
| `tachiyomi.animeextension` | `<uses-feature>` name. Used to detect that an APK is an extension. |
| `tachiyomi.animeextension.class` | Metadata: semicolon-separated list of `AnimeSource` or `AnimeSourceFactory` class names. |
| `tachiyomi.animeextension.factory` | Metadata: optional factory class name (legacy). |
| `tachiyomi.animeextension.nsfw` | Metadata: `1` if NSFW, `0` otherwise. |
| `LIB_VERSION_MIN` / `LIB_VERSION_MAX` | The supported range of `extensions-lib` versions (12-16). |

The anime-specific keys use the `tachiyomi.animeextension` prefix
(distinct from manga's `tachiyomi.extension`). This lets Aniyomi/Animiru
co-exist with manga-only Tachiyomi forks without extension conflicts.

## 3. Two extension install locations

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:28-41
/**
 * Class that handles the loading of the extensions. Supports two kinds of extensions:
 *
 * 1. Shared extension: This extension is installed to the system with package
 * installer, so other variants of Tachiyomi and its forks can also use this extension.
 *
 * 2. Private extension: This extension is put inside private data directory of the
 * running app, so this extension can only be used by the running app and not shared
 * with other apps.
 *
 * When both kinds of extensions are installed with a same package name, shared
 * extension will be used unless the version codes are different. In that case the
 * one with higher version code will be used.
 */
```

- **Shared extension** — installed via the system package installer
  (PackageInstaller or Shizuku). Visible to other Tachiyomi forks.
  Lives in the system's app directory.
- **Private extension** — installed as a `.ext` file in
  `context.filesDir/exts/<packageName>.ext`. Only visible to this app.
  Loaded via `getPackageArchiveInfo` + `fixBasePaths`.

The `selectExtensionPackage` function picks the higher-version one when
both exist:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:360-374
private fun selectExtensionPackage(shared: ExtensionInfo?, private: ExtensionInfo?): ExtensionInfo? {
    when {
        private == null && shared != null -> return shared
        shared == null && private != null -> return private
        shared == null && private == null -> return null
    }

    return if (PackageInfoCompat.getLongVersionCode(shared!!.packageInfo) >=
        PackageInfoCompat.getLongVersionCode(private!!.packageInfo)
    ) {
        shared
    } else {
        private
    }
}
```

> ANI-KUTA: ANI-KUTA is Aniyomi-compatible, so it should support both
> shared and private extensions. The private extension mechanism is
> especially useful for sideloading extensions that aren't in any repo.

## 4. ClassLoader strategy — `ChildFirstPathClassLoader`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:277-282
val classLoader = try {
    ChildFirstPathClassLoader(appInfo.sourceDir, null, context.classLoader)
} catch (e: Exception) {
    logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($pkgName)" }
    return LoadResult.Error
}
```

`ChildFirstPathClassLoader` is a custom `PathClassLoader` subclass that
**checks the extension's own classes first** before delegating to the
parent (host app) ClassLoader. This is the standard pattern for plugin
systems — it prevents the host app's older versions of dependencies
(OkHttp, Jsoup, etc.) from shadowing the extension's versions.

There's a fallback to plain `PathClassLoader` if `LinkageError` occurs
during class loading:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:294-322
.flatMap {
    try {
        when (val obj = Class.forName(it, false, classLoader).getDeclaredConstructor().newInstance()) {
            is AnimeSource -> listOf(obj)
            is AnimeSourceFactory -> obj.createSources()
            else -> throw Exception("Unknown source class type: ${obj.javaClass}")
        }
    } catch (e: LinkageError) {
        // AY -->
        try {
            val fallBackClassLoader = PathClassLoader(appInfo.sourceDir, null, context.classLoader)
            when (
                val obj = Class.forName(
                    it,
                    false,
                    fallBackClassLoader,
                ).getDeclaredConstructor().newInstance()
            ) {
                is AnimeSource -> {
                    listOf(obj)
                }
                is AnimeSourceFactory -> obj.createSources()
                else -> throw Exception("Unknown source class type: ${obj.javaClass}")
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
            return LoadResult.Error
        }
        // <-- AY
    } catch (e: Throwable) {
        logcat(LogPriority.ERROR, e) { "Extension load error: $extName ($it)" }
        return LoadResult.Error
    }
}
```

The fallback to parent-first `PathClassLoader` handles cases where
`ChildFirstPathClassLoader` causes issues (e.g. the extension needs to
share classes with the host for compatibility). This is an Aniyomi-
specific addition over Mihon (`// AY -->` markers).

## 5. Trust management

Extensions must be signed with a trusted certificate. The trust check:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:254-269
val signatures = getSignatures(pkgInfo)
if (signatures.isNullOrEmpty()) {
    logcat(LogPriority.WARN) { "Package $pkgName isn't signed" }
    return LoadResult.Error
} else if (!trustExtension.isTrusted(pkgInfo, signatures)) {
    val extension = Extension.Untrusted(
        extName,
        pkgName,
        versionName,
        versionCode,
        libVersion,
        signatures.last(),
    )
    logcat(LogPriority.WARN) { "Extension $pkgName isn't trusted" }
    return LoadResult.Untrusted(extension)
}
```

`TrustExtension` (in `:domain`) maintains a list of trusted signature
hashes. The flow:
1. Get the package's signatures (SHA-256 hashes of signing certs).
2. Check if any are in the trusted list (`trustExtension.isTrusted`).
3. If trusted → load the extension.
4. If untrusted → return `LoadResult.Untrusted` — the UI shows a
   "trust this extension?" dialog.

The user can trust an extension via:
```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt:286-296
suspend fun trust(extension: Extension.Untrusted) {
    untrustedExtensionMapFlow.value[extension.pkgName] ?: return

    trustExtension.trust(extension.pkgName, extension.versionCode, extension.signatureHash)

    untrustedExtensionMapFlow.value -= extension.pkgName

    ExtensionLoader.loadExtensionFromPkgName(context, extension.pkgName)
        .let { it as? LoadResult.Success }
        ?.let { registerNewExtension(it.extension) }
}
```

Once trusted, the extension is loaded immediately and registered.

### Signature hashing

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt:391-405
private fun getSignatures(pkgInfo: PackageInfo): List<String>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val signingInfo = pkgInfo.signingInfo!!
        if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        }
    } else {
        @Suppress("DEPRECATION")
        pkgInfo.signatures
    }
        ?.map { Hash.sha256(it.toByteArray()) }
        ?.toList()
}
```

Each signature is SHA-256 hashed. The hash is what's stored in the
trusted list and compared against.

## 6. The `ExtensionManager` — central registry

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt:42-77
class ExtensionManager(
    private val context: Context,
    private val preferences: SourcePreferences = Injekt.get(),
    private val trustExtension: TrustExtension = Injekt.get(),
) {

    val scope = CoroutineScope(SupervisorJob())

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * API where all the available extensions can be found.
     */
    private val api = ExtensionApi()

    /**
     * The installer which installs, updates and uninstalls the extensions.
     */
    private val installer by lazy { ExtensionInstaller(context) }

    private val iconMap = mutableMapOf<String, Drawable>()

    private val installedExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.Installed>())
    val installedExtensionsFlow = installedExtensionMapFlow.mapExtensions(scope)

    private val availableExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.Available>())
    val availableExtensionsFlow = availableExtensionMapFlow.mapExtensions(scope)

    private val untrustedExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.Untrusted>())
    val untrustedExtensionsFlow = untrustedExtensionMapFlow.mapExtensions(scope)

    init {
        initExtensions()
        ExtensionInstallReceiver(InstallationListener()).register(context)
    }
```

Three StateFlows:
- `installedExtensionsFlow` — extensions successfully loaded.
- `availableExtensionsFlow` — extensions found in repos (not necessarily
  installed).
- `untrustedExtensionsFlow` — extensions whose signatures aren't trusted.

`initExtensions()` runs synchronously in the constructor:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt:128-140
private fun initExtensions() {
    val extensions = ExtensionLoader.loadExtensions(context)

    installedExtensionMapFlow.value = extensions
        .filterIsInstance<LoadResult.Success>()
        .associate { it.extension.pkgName to it.extension }

    untrustedExtensionMapFlow.value = extensions
        .filterIsInstance<LoadResult.Untrusted>()
        .associate { it.extension.pkgName to it.extension }

    _isInitialized.value = true
}
```

`ExtensionLoader.loadExtensions` runs on a background thread (it uses
`runBlocking` internally with `async`/`awaitAll`). All extensions are
loaded concurrently.

## 7. The `ExtensionApi` — repo fetching

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt:28-66
internal class ExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateExtensionRepo by injectLazy()
    private val extensionManager: ExtensionManager by injectLazy()
    private val json: Json by injectLazy()

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong(Preference.appStateKey("last_ext_check"), 0)
    }

    suspend fun findExtensions(): List<Extension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<Extension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.min.json"))
                .awaitSuccess()

            with(json) {
                response
                    .parseAs<List<ExtensionJsonObject>>()
                    .toExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }
```

Each repo has a `baseUrl`. Animiru fetches `$baseUrl/index.min.json` —
a JSON array of extension metadata. The repos are stored in a DB table
(`ExtensionRepo`) managed by `GetExtensionRepo` / `UpdateExtensionRepo`
interactors.

### The JSON schema

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt:143-170
@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)
```

Each repo's `index.min.json` is an array of these objects. The
`toExtensions` function filters by lib version and converts to
`Extension.Available`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt:111-132
private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<Extension.Available> {
    return this
        .filter {
            val libVersion = it.extractLibVersion()
            libVersion >= ExtensionLoader.LIB_VERSION_MIN && libVersion <= ExtensionLoader.LIB_VERSION_MAX
        }
        .map {
            Extension.Available(
                name = it.name.substringAfter("Aniyomi: "),
                pkgName = it.pkg,
                versionName = it.version,
                versionCode = it.code,
                libVersion = it.extractLibVersion(),
                lang = it.lang,
                isNsfw = it.nsfw == 1,
                sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                apkName = it.apk,
                iconUrl = "$repoUrl/icon/${it.pkg}.png",
                repoUrl = repoUrl,
            )
        }
}
```

Extensions whose lib version is outside `[12, 16]` are filtered out.

### Update checking

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/api/ExtensionApi.kt:68-109
suspend fun checkForUpdates(
    context: Context,
    fromAvailableExtensionList: Boolean = false,
): List<Extension.Installed>? {
    // Limit checks to once a day at most
    if (!fromAvailableExtensionList &&
        Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
    ) {
        return null
    }

    // Update extension repo details
    updateExtensionRepo.awaitAll()

    val extensions = if (fromAvailableExtensionList) {
        extensionManager.availableExtensionsFlow.value
    } else {
        findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
    }

    val installedExtensions = ExtensionLoader.loadExtensions(context)
        .filterIsInstance<LoadResult.Success>()
        .map { it.extension }

    val extensionsWithUpdate = mutableListOf<Extension.Installed>()
    for (installedExt in installedExtensions) {
        val pkgName = installedExt.pkgName
        val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
        val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
        val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
        val hasUpdate = hasUpdatedVer || hasUpdatedLib
        if (hasUpdate) {
            extensionsWithUpdate.add(installedExt)
        }
    }

    if (extensionsWithUpdate.isNotEmpty()) {
        ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
    }

    return extensionsWithUpdate
}
```

- **Throttled to once per day** — `lastExtCheck` preference.
- Compares `versionCode` AND `libVersion` — either triggers an update.
- Notifies the user via `ExtensionUpdateNotifier`.

## 8. The installer backends

`ExtensionInstaller` is the orchestrator. It picks a backend based on
`BasePreferences.extensionInstaller`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionInstaller.kt:103-137
private fun installApk(downloadId: Long, tempFile: File) {
    when (val installer = extensionInstaller.get()) {
        BasePreferences.ExtensionInstaller.LEGACY -> {
            val intent = Intent(context, ExtensionInstallActivity::class.java)
                .setDataAndType(tempFile.getUriCompat(context), APK_MIME)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(intent)
        }
        BasePreferences.ExtensionInstaller.PRIVATE -> {
            try {
                if (ExtensionLoader.installPrivateExtensionFile(context, tempFile)) {
                    updateInstallStep(downloadId, InstallStep.Installed)
                } else {
                    updateInstallStep(downloadId, InstallStep.Error)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to read downloaded extension file." }
                updateInstallStep(downloadId, InstallStep.Error)
            }

            tempFile.delete()
        }
        else -> {
            val intent = ExtensionInstallService.getIntent(
                context,
                downloadId,
                tempFile.getUriCompat(context),
                installer,
            )
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
```

Three backends:
1. **LEGACY** — launches `ExtensionInstallActivity`, which uses Android's
   `PackageInstaller` system API. Shows the standard "Install" dialog.
   Requires user confirmation per-install.
2. **PRIVATE** — calls `ExtensionLoader.installPrivateExtensionFile`,
   which copies the APK to `filesDir/exts/<pkg>.ext`. No user prompt.
   Only visible to this app.
3. **SHIZUKU** (or others) — starts `ExtensionInstallService`, a
   foreground service that uses Shizuku to install without prompts.
   Shizuku runs as shell user, bypassing the unknown-sources restriction.

### The `Installer` abstract base

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/installer/Installer.kt:22-172
abstract class Installer(private val service: Service) {

    private val extensionManager: ExtensionManager by injectLazy()

    private var waitingInstall = AtomicReference<Entry?>(null)
    private val queue = Collections.synchronizedList(mutableListOf<Entry>())

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1).takeIf { it >= 0 } ?: return
            cancelQueue(downloadId)
        }
    }

    abstract var ready: Boolean

    fun addToQueue(downloadId: Long, uri: Uri) {
        queue.add(Entry(downloadId, uri))
        checkQueue()
    }

    @CallSuper
    open fun processEntry(entry: Entry) {
        extensionManager.setInstalling(entry.downloadId)
    }

    open fun cancelEntry(entry: Entry): Boolean {
        return true
    }

    fun continueQueue(resultStep: InstallStep) {
        val completedEntry = waitingInstall.exchange(null)
        if (completedEntry != null) {
            extensionManager.updateInstallStep(completedEntry.downloadId, resultStep)
            checkQueue()
        }
    }

    fun checkQueue() {
        if (!ready) {
            return
        }
        if (queue.isEmpty()) {
            service.stopSelf()
            return
        }
        val nextEntry = queue.first()
        if (waitingInstall.compareAndSet(null, nextEntry)) {
            queue.removeAt(0)
            processEntry(nextEntry)
        }
    }
```

This is a queue-based installer that runs inside a foreground `Service`.
Subclasses (`PackageInstallerInstaller`, `ShizukuInstaller`) implement
`processEntry` to do the actual install.

### `PackageInstallerInstaller`

Uses Android's `PackageInstaller` system API. Requires user confirmation
per install (the system shows a dialog).

### `ShizukuInstaller`

Uses Shizuku (a shell-user service) to install without prompts. Requires
the user to have Shizuku running on their device. Animiru's
`ShellInterface` (`app/src/main/java/mihon/app/shizuku/ShellInterface.kt`)
wraps the Shizuku API.

## 9. The `ExtensionInstallReceiver` — install events

A `BroadcastReceiver` that listens for package install/update/remove
events:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt:331-354
private inner class InstallationListener : ExtensionInstallReceiver.Listener {

    override fun onExtensionInstalled(extension: Extension.Installed) {
        registerNewExtension(extension.withUpdateCheck())
        updatePendingUpdatesCount()
    }

    override fun onExtensionUpdated(extension: Extension.Installed) {
        registerUpdatedExtension(extension.withUpdateCheck())
        updatePendingUpdatesCount()
    }

    override fun onExtensionUntrusted(extension: Extension.Untrusted) {
        installedExtensionMapFlow.value -= extension.pkgName
        untrustedExtensionMapFlow.value += extension
        updatePendingUpdatesCount()
    }

    override fun onPackageUninstalled(pkgName: String) {
        ExtensionLoader.uninstallPrivateExtension(context, pkgName)
        unregisterExtension(pkgName)
        updatePendingUpdatesCount()
    }
}
```

When a package is installed/updated/removed (by any means — the
installer, ADB, or another Tachiyomi fork), this receiver fires and
the `ExtensionManager` updates its state flows.

## 10. The `Extension` model

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/extension/model/Extension.kt
sealed class Extension {
    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val libVersion: Double

    data class Installed(...)
    data class Available(...)
    data class Untrusted(...)
}
```

Three subtypes:
- **`Installed`** — loaded and ready to use. Has `sources: List<AnimeSource>`.
- **`Available`** — found in a repo but not installed. Has `apkName`,
  `iconUrl`, `repoUrl`.
- **`Untrusted`** — installed but signature not trusted. Has
  `signatureHash`.

## 11. The repo system

Animiru supports multiple extension repos. Each repo has:
- `baseUrl` — the HTTPS URL of the repo.
- A list of extensions at `$baseUrl/index.min.json`.
- APKs at `$baseUrl/apk/<apkName>`.
- Icons at `$baseUrl/icon/<pkg>.png`.

The repos are stored in a DB table managed by:
- `GetExtensionRepo` — read all repos.
- `UpdateExtensionRepo` — fetch and update repo metadata.

The default repo is hardcoded somewhere in the app initialization (not
shown in the files I read). Users can add custom repos via the Settings
screen.

### Aniyomi-compatible repos

Animiru uses the **Aniyomi** extension repo format (same JSON schema).
This means:
- Aniyomi extension repos work in Animiru.
- Animiru can use the official Aniyomi extension index.

The `substringAfter("Aniyomi: ")` calls in `ExtensionApi.toExtensions`
and `ExtensionLoader.loadExtension` strip the `"Aniyomi: "` prefix
from extension names — this is the convention for Aniyomi-extension
APK labels.

## 12. Comparison with ANI-KUTA

> Per `CORE_RULES.md` and prior worklog entries, ANI-KUTA is
> Aniyomi-compatible. This means ANI-KUTA should support the same
> extension format.

| Aspect | Animiru | ANI-KUTA (current/target) |
|--------|---------|---------------------------|
| Extension format | Aniyomi APK (`.animeextension` feature) | Same (Aniyomi-compatible) |
| Lib version range | 12–16 | TBD — match Animiru |
| ClassLoader | `ChildFirstPathClassLoader` with `PathClassLoader` fallback | TBD — port `ChildFirstPathClassLoader` |
| Trust | SHA-256 signature hash, user-approved | TBD — port `TrustExtension` |
| Installer backends | LEGACY (PackageInstaller), PRIVATE (`.ext` file), SHIZUKU | TBD — at minimum PRIVATE + SHIZUKU |
| Repo system | Multi-repo, DB-stored, `index.min.json` format | TBD — port |
| Update check | Daily throttle, notification | TBD — port |
| Shared vs private | Both supported | TBD — both |
| Module structure | `:source-api` (interfaces), `:app` (loader/manager/installer) | TBD — similar split |

> ANI-KUTA: The extension system is the most directly-portable part of
> Animiru. The `ExtensionLoader`, `ExtensionManager`, `ExtensionApi`,
> `ExtensionInstaller`, and `Installer` classes can be lifted almost
> verbatim, with DI adjustments (Injekt → Hilt).

## 13. Quirks + warnings

1. **`runBlocking` in `loadExtensions`** — `ExtensionLoader.loadExtensions`
   uses `runBlocking` with `async`/`awaitAll` for concurrent loading.
   This blocks the calling thread. It's called from
   `ExtensionManager.initExtensions()` which runs in the constructor —
   effectively on the main thread during app startup. For a small
   number of extensions this is fine; for many it could cause ANR.

2. **`getSignatures` on Android 9+** — uses `signingInfo` which requires
   `GET_SIGNING_CERTIFICATES` flag. On older Android, falls back to the
   deprecated `pkgInfo.signatures` field. Both paths are handled.

3. **Private extension file permissions** — `installPrivateExtensionFile`
   calls `copyAndSetReadOnlyTo` to make the file read-only. On Android
   14+, this is required (writable files in the app's private dir are
   treated as untrusted by the system).

4. **`fixBasePaths` for `getPackageArchiveInfo`** — on Android 13+,
   `ApplicationInfo` from `getPackageArchiveInfo` doesn't have
   `sourceDir` set, which breaks asset loading. The `fixBasePaths`
   helper manually sets it.

5. **Icon caching** — `iconMap` in `ExtensionManager` is a
   `mutableMapOf` (not thread-safe). Icons are loaded lazily via
   `loadIcon` on the main thread. For a large number of extensions,
   this could cause jank in the extension list UI.

6. **NSFW filtering** — `loadNsfwSource` is read **once** at init
   (`ExtensionLoader.kt:46-48`). Changing the `showNsfwSource` pref
   doesn't immediately hide/show NSFW sources — the user must restart
   the app. This is a known UX limitation.

7. **`libVersion` parsing** — `extractLibVersion` does
   `version.substringBeforeLast('.').toDoubleOrNull()`. If the version
   string is malformed (e.g. `"1.6.0-beta"`), `toDoubleOrNull` returns
   null and the extension is filtered out by the lib version check.

8. **No rollback** — if an extension update breaks something, the user
   can't easily downgrade. They'd need to manually install an older APK.
   The `PRIVATE` installer prevents downgrades (`installPrivateExtensionFile`
   checks `PackageInfoCompat.getLongVersionCode(extension) <
   PackageInfoCompat.getLongVersionCode(currentExtension)` and refuses).

9. **Aniyomi vs Mihon metadata** — the `tachiyomi.animeextension.*`
   keys are Aniyomi-specific. Mihon (manga-only) uses
   `tachiyomi.extension.*`. Animiru only loads anime extensions, not
   manga ones. ANI-KUTA (being Aniyomi-compatible) should do the same.

10. **`ExtensionInstallService` is a foreground service** — requires a
    persistent notification while installing. This is Android's
    requirement for foreground services; without it, the service would
    be killed if the user backgrounds the app mid-install.
