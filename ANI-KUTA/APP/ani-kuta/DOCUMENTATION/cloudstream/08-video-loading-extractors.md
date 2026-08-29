# 08 — Video Loading & the Extractor Subsystem

> **Scope**: how CloudStream (CS3) turns a details page into playable stream links —
> the `loadLinks` contract, the `ExtractorApi` subsystem (registry, URL→extractor
> resolution, built-in inventory, anti-bot tooling), the app-side link-generation
> pipeline (`RepoLinkGenerator` → player/download), subtitle streaming during load,
> and plugin-declared custom extractors. Ends with an ANI-KUTA mapping preview.
>
> **Companion docs** — this file *defers* where earlier docs already covered the ground:
> - `03-mainapi-reference.md` §2.9 (loadLinks constants), §6 (extractor-side API summary)
> - `05-data-models.md` §6.6 (`ExtractorLinkType`), §7.1–7.6 (`SubtitleFile`, `AudioFile`,
>   `ExtractorLink` ALL-fields table, `DrmExtractorLink`, `ExtractorLinkPlayList`,
>   `ExtractorApi` short form)
> This doc is the *pipeline deep-dive*: what happens between `loadLinks(data…)` being
> called and ExoPlayer holding a `MediaItem`.
>
> **Citation keys**
> - `MA` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/MainAPI.kt`
> - `EA` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/utils/ExtractorApi.kt`
> - `X/<File>` = `library/src/commonMain/kotlin/com/lagradost/cloudstream3/extractors/<File>`
> - `APP/<path>` = `app/src/main/java/com/lagradost/cloudstream3/<path>`
> - `STORM/<path>` = `/research/storm-ext/<path>` (real plugin repo)
> - `AK/<path>` = ANI-KUTA app tree
> - `MainActivity.kt` = `library/.../cloudstream3/MainActivity.kt` (top-level `app` client)
> - Markers: `[verified]` read in source · `[docs]` from a prior doc, spot-checked ·
>   `[inferred]` reasoned, not directly observable.

---

## 1. The `loadLinks` contract

### 1.1 Signature + KDoc (verbatim)

```kotlin
/**Callback is fired once a link is found, will return true if method is executed successfully
 * @param data dataUrl string returned from [load] function.
 * @see newMovieLoadResponse
 * @see newTvSeriesLoadResponse
 * @see newLiveStreamLoadResponse
 * @see newAnimeLoadResponse
 * @see newTorrentLoadResponse
 * */
// @WorkerThread
open suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    throw NotImplementedError()
}
```
`MA:683-699` [verified]. Default throws `NotImplementedError` — providers must override.

### 1.2 Parameter semantics

| Param | Type | Meaning |
|---|---|---|
| `data` | `String` | The opaque **dataUrl** produced during `load()` — whatever the provider stuffed into `newAnimeLoadResponse(url, data)`/`Episode(data=…)`. Often an embed URL, but can be JSON, a list of URLs, an id — *anything* (see 03 §2.9 and 05 §4: "untyped JSON in a String"). It is NOT necessarily a resolvable URL. |
| `isCasting` | `Boolean` | True when the user is sending to a Chromecast. Providers are expected to **skip links that cannot be cast** (e.g. ones requiring a Referer header, which the cast sender can't attach). See §1.6 for the related `hasChromecastSupport` flag. |
| `subtitleCallback` | `(SubtitleFile) -> Unit` | Fire once per discovered subtitle track. `SubtitleFile(lang, url, headers?)` (`MA:1205-1220` [verified]), built via `newSubtitleFile(lang, url) { … }` (`MA:1224-1236`). May be called zero or many times. |
| `callback` | `(ExtractorLink) -> Unit` | Fire once per discovered **playable stream** (one host usually yields several — one per quality/variant). Full field table in 05 §7.3. |

**Return value**: `Boolean` — "true if method is executed successfully". This is a
*coarse* signal, NOT "at least one link was found": a provider that resolves three
hosts but considers the run successful returns `true` even if every `callback`
emitted nothing; the app treats `false` as a soft failure but still keeps whatever
links already streamed in via `callback` (`RepoLinkGenerator.generateLinks` returns
the repository result *after* caching every link it received — `RLG:135-159` [verified]).

### 1.3 The callback-streaming model

`loadLinks` is `suspend` **and** callback-based — the two combined give it a
streaming character:

1. **Links may arrive before the function returns.** Each `callback(link)` fires the
   moment that link is resolved; the app's callback (RepoLinkGenerator) immediately
   de-dups, caches and forwards it to the player UI (`RLG:135-151` [verified]).
   This is why the player's source list visibly *fills up* while loading is still in
   progress — the dialog observes `VideoState.links` incrementally
   (`PGV:410-418` `modifyState { add(link) }` [verified]).
2. **The function may return before callbacks finish?** No — `suspend` + direct
   invocation means callbacks run inside the coroutine; `loadLinks` returning marks
   the end of the stream *for that call*. However the **app-side wrapper** can be
   cancelled mid-stream (user taps "skip loading" → `currentLoadLinkJob.cancelChildren()`,
   `RVM2:1260-1263` [verified]) — already-emitted links survive in the cache.
3. **Multiple links are the norm** — one embed page typically fans out to N mirror
   hosts (each its own `ExtractorLink`) and each m3u8 master can fan out to N
   qualities (via `M3u8Helper.generateM3u8(...).forEach(callback)`).

### 1.4 Timeout handling

- Provider-declared hint: `open val loadLinksTimeoutMs: Long? = null` — KDoc: "should
  be around a few minutes to prevent any unexpected recursive call/extraction to
  drain resources… only a hint, and may not get respected if you request something
  too long" (`MA:566-573` [verified]).
- App-side enforcement in `APIRepository.loadLinks`:

```kotlin
suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    if (isInvalidData(data)) return false // this makes providers cleaner
    return try {
        withTimeout(getTimeout(api.loadLinksTimeoutMs)) {
            api.loadLinks(data, isCasting, subtitleCallback, callback)
        }
    } catch (throwable: Throwable) {
        logError(throwable)
        return false
    }
}
```
`APP/ui/APIRepository.kt:204-219` [verified]. `getTimeout` clamps the desired value
into **5 s – 480 s, default 120 s** (`DEFAULT_TIMEOUT=120_000`, `MAX_TIMEOUT=4×`,
`MIN_TIMEOUT=5_000`; `AR:29-33, 62-64` [verified]). Any throwable — including
`TimeoutCancellationException` — is swallowed into `false`; streamed links are NOT
rolled back (they're already in the caller's cache). `isInvalidData` rejects
`""`, `"[]"`, `"about:blank"` up-front (`AR:55-58` [verified]).

### 1.5 `data` vs `Episode.data` vs `dataUrl` — one string, three names

- `LoadResponse`-time: the provider sets `dataUrl` on movie responses / `Episode.data`
  on series responses (`Episode` data class: `var data: String` first positional param,
  `MA:2552-2566` [verified]).
- Result-page time: the app copies it verbatim into `ResultEpisode.data`
  (`APP/ui/result/ResultFragment.kt:47` [verified]) — `ResultEpisode` is the
  `@Serializable` view-model unit that survives process death.
- Player time: `RepoLinkGenerator.generateLinks` passes `current.data` (the
  `ResultEpisode`, not the raw `Episode`) into `APIRepository.loadLinks`
  (`RLG:107-110` [verified]).
So: **`Episode.data` (provider vocabulary) == `ResultEpisode.data` (app vocabulary)
== the `data` parameter of `loadLinks`.** The provider round-trips its own payload.

### 1.6 Related provider flags

| Flag | Default | Effect |
|---|---|---|
| `instantLinkLoading` | `false` | KDoc: "If link is stored in the data string, so links can be instantly loaded" (`MA:548-549` [verified]). **Grep of the whole tree finds ZERO readers** — app, library, and the bundled plugin repos all ignore it. Vestigial; do not implement against it. `[verified — absence]` |
| `hasChromecastSupport` | `true` | Only dims/greys the cast button on the details page + shows a toast (`APP/ui/result/ResultFragmentPhone.kt:664-671` [verified]). The real cast filtering is `isCasting` at loadLinks time + `LOADTYPE_CHROMECAST` source-type filter. |
| `hasDownloadSupport` | `true` | Hides the download button per episode (`APP/ui/result/EpisodeAdapter.kt` [verified usage]). |
| `loadLinksTimeoutMs` | `null` | See §1.4. |

---

## 2. `ExtractorApi` — the extractor base class

### 2.1 Declaration (verbatim, complete)

```kotlin
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    /** Determines which plugin a given provider is from. This is the full path to the plugin. */
    var sourcePlugin: String? = null

    //suspend fun getSafeUrl(url: String, referer: String? = null): List<ExtractorLink>? {
    //    return safeAsync { getUrl(url, referer) }
    //}

    // this is the new extractorapi, override to add subtitles and stuff
    @Throws
    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        getUrl(url, referer)?.forEach(callback)
    }

    suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            getUrl(url, referer, subtitleCallback, callback)
        } catch (e: Exception) {
            logError(e)
        }
    }

    /**
     * Will throw errors, use getSafeUrl if you don't want to handle the exception yourself
     */
    @Throws
    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink>? {
        return emptyList()
    }

    open fun getExtractorUrl(id: String): String {
        return id
    }
}
```
`EA:1419-1466` [verified].

### 2.2 Member-by-member

| Member | Kind | Notes |
|---|---|---|
| `name` | `abstract val` | Display name AND the identity used by `getExtractorApiFromName` (§2.4). Also becomes `ExtractorLink.source` by convention (`newExtractorLink(this.name, …)`), which the app later maps back to a provider for `getVideoInterceptor`/`extractorVerifierJob`. |
| `mainUrl` | `abstract val` | Root URL of the host this extractor handles — the ONLY routing key (§2.3). |
| `requiresReferer` | `abstract val` | Declares the host needs a Referer to play. The standalone helper `requireReferer(name)` (`EA:1353-1355`) wraps it, **but has no callers anywhere** in app or library `[verified — absence]` — the flag is effectively documentation for plugin authors; the player always sends `ExtractorLink.referer`/`headers` when present regardless. |
| `sourcePlugin` | `var` | Set by `registerExtractorAPI` (`BP:31-35`); used to remove the extractor when its plugin unloads (`APP/plugins/PluginManager.kt:713-715` [verified]). |
| `getUrl(url, referer, subtitleCallback, callback)` | `open suspend` | **The modern contract** — same streaming shape as `loadLinks`. Default bridges to the 2-arg `getUrl` and `forEach(callback)`, so legacy extractors keep working. |
| `getSafeUrl(url, referer, subtitleCallback, callback)` | `final suspend` | `getUrl` wrapped in try/catch + `logError`. NOTE: the old commented-out `safeAsync`-based `getSafeUrl` (line 1427-1429) returned `List<ExtractorLink>?` — the current one is **callback-shaped and Unit-returning**. Don't trust stale snippets online. |
| `getUrl(url, referer): List<ExtractorLink>?` | `open suspend` | The LEGACY contract (no subtitles). Default returns `emptyList()` (not null). New extractors should override the 4-arg form. |
| `getExtractorUrl(id)` | `open fun` | Identity map (id → URL) for extractors that can build an embed URL from a bare video id. Default `return id`. Rarely overridden. |

There is **no** `domain` property (the assignment brief's "domain?" — answer: it
doesn't exist; `mainUrl` is the domain carrier) `[verified]`.

### 2.3 URL → extractor resolution (`loadExtractor`) — the exact rule

```kotlin
@Throws(CancellationException::class)
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    // Ensure this coroutine has not timed out
    coroutineScope { ensureActive() }

    val currentUrl = unshortenLinkSafe(url)
    val compareUrl = currentUrl.lowercase().replace(schemaStripRegex, "")

    // Iterate in reverse order so the new registered ExtractorApi takes priority
    for (index in extractorApis.lastIndex downTo 0) {
        val extractor = extractorApis[index]
        if (compareUrl.startsWith(extractor.mainUrl.replace(schemaStripRegex, ""))) {
            try {
                extractor.getUrl(currentUrl, referer, subtitleCallback, callback)
            } catch (e: Exception) {
                logError(e)
                if (e is CancellationException) throw e
            }
            return true
        }
    }

    // this is to match mirror domains - like example.com, example.net
    for (index in extractorApis.lastIndex downTo 0) {
        val extractor = extractorApis[index]
        if (Levenshtein.partialRatio(extractor.mainUrl, currentUrl) > 80) {
            try { extractor.getUrl(currentUrl, referer, subtitleCallback, callback) }
            catch (e: Exception) { logError(e); if (e is CancellationException) throw e }
            return true
        }
    }
    return false
}
```
`EA:931-983` (condensed — comments preserved, catch-bodies collapsed) [verified].
`schemaStripRegex = Regex("""^(https:|)//(www\.|)""")` (`EA:847` [verified]).

**The exact algorithm, step by step:**

1. **Unshorten**: `unshortenLinkSafe(url)` — if the URL matches a known shortener
   regex family (adf.ly, linkup.pro, linksafe.cc, uprot.net, … — `ShortLink` table in
   `library/.../utils/UnshortenUrl.kt:15-45` [verified]), follow it to the real URL;
   on any error keep the original (`EA:903-912` [verified]).
2. **Normalize the candidate**: lowercase + strip leading `https://`/`//`/`www.` →
   `compareUrl`.
3. **Pass 1 — exact prefix match**: walk `extractorApis` from **last index to 0**
   (newest registered first, so plugin extractors shadow built-ins). Match iff
   `compareUrl.startsWith(extractor.mainUrl` with schema stripped`)`. **First hit
   wins; its `getUrl` runs; return true.** Note the extractor's `mainUrl` is NOT
   lowercased before comparison — a `mainUrl` containing uppercase letters would
   never match a lowercased `compareUrl` (all real ones are lowercase) `[inferred, quirk]`.
4. **Pass 2 — fuzzy mirror match** (only if pass 1 found nothing): same reverse walk,
   match iff `Levenshtein.partialRatio(extractor.mainUrl, currentUrl) > 80` (≥81%
   similar strings; `utils/Levenshtein.kt` — FuzzyKot port [verified]). Catches
   unregistered mirror domains like `example.com` vs `example.net`.
5. **No match → `false`** ("no extractor could handle this URL").

Key consequences:
- **Exactly one extractor ever runs per `loadExtractor` call** — even if several
  could match. Multi-host pages call `loadExtractor` once per discovered embed URL
  (e.g. `amap { loadExtractor(datavid, …) }`).
- Only the first matching extractor's *exceptions* are logged; a broken extractor
  still returns `true` from `loadExtractor` (it "was loaded").
- `getExtractorApiFromName(name)` resolves **by display name**, first match wins,
  falling back to `extractorApis[0]` (`EA:1346-1351` [verified]) — used by app code
  to get from an `ExtractorLink.source` back to the owning provider (§4.6).
- The registry `extractorApis` is an `AtomicMutableList` (`EA:985`) — plugins append
  (`BP:34`), unload removes by `sourcePlugin` (`PM:713-715`).

### 2.4 Who calls `loadExtractor`?

Provider `loadLinks` implementations (the dominant pattern — §6 examples) and
recursively some extractors/helpers (e.g. `AsianEmbedHelper.getUrls` walks a
server-list page and calls `loadExtractor` per `data-video` attr,
`X/helper/AsianEmbedHelper.kt:17-33` [verified]). The app itself does NOT call
`loadExtractor` — routing to extractors is entirely provider-side
(§4.4: provider returns links either directly or via `loadExtractor`).

---

## 3. Built-in extractor inventory

### 3.1 Scale & shape

- **97 base classes** directly extend `ExtractorApi()` in
  `library/.../extractors/*.kt` (`grep -c ': ExtractorApi()'` = 97 [verified]) —
  this is the "~97 extractors" number quoted in docs 03/05.
- **321 extractor instances are registered** in the `extractorApis` list
  (`EA:985-1343` [verified]) — because of the **mirror-domain subclass pattern**:
  one `open class` base per host family, plus a pile of one-liner subclasses that
  only override `name`/`mainUrl`:

```kotlin
class Sblona : StreamSB() {
    override var name = "Sblona"
    override var mainUrl = "https://sblona.com"
}
```
`X/StreamSB.kt:14-18` [verified]. (Same shape for Dood, StreamWish, VidHidePro,
Vidara, gdriveplayer, … — and identically in plugins, §6.)

### 3.2 Category table (family → registered instances → notable members)

Counts derived from the registry list mapped to parent classes via class
declarations `[verified, script-derived]` (single-instance families are grouped).

| Category | ~Instances | Notable |
|---|---|---|
| StreamWish family (ad-funded HLS rebrand swarm) | 29 | `StreamWishExtractor`, `Mwish`, `Dwish`, `Awish`, `Nekowish`, `Kswplayer`… |
| StreamSB / vidcloud family | 29 | `StreamSB`, `Sbflix`, `Sblona`, `Lvturbo`, `Sbplay1-11` mirrors |
| VidHidePro / filelions family | 23 | `VidHidePro1-6`, `Smoothpre`, `VidHideHub` |
| Dood family | ~21 | `DoodLaExtractor` (base), `DoodCxExtractor`, `DoodToExtractor`, `Dooood`, `D0000d` |
| Vidara family | 17 | `Vidara`, `Vidaraa/aw/ax/So/…` (propietary "vidara" player clones) |
| XStreamCdn / FEmbed family | 13 | `XStreamCdn`, `FEmbed`, `Fplayer`, `FeHD`, `DBfilm`, `LayarKaca` |
| gdriveplayer family | 12 | `Gdriveplayer` + 10 domain variants + `DatabaseGdrive` |
| Voe | 9 | `Voe`, `Voe1`, `Voe2` |
| MixDrop | 9 | `MixDrop`, `MixDropBz/Ch/To/Ag/Ps/Si`, `Mdy`, `MxDropTo` |
| Filesim / FileMoon family | 9 | `Filesim`, `FileMoon`, `FileMoonIn`, `FileMoonSx`, `FilemoonV2`, `Guccihide`, `Ahvsh` |
| JWPlayer generic | 8 | `JWPlayer`, `Zplayer`, `ZplayerV2`, `PlayLtXyz`, `StreamEmbed` |
| ContentX (Indonesian CDN) | 6 | `ContentX`, `FourCX`, `Hotlinger` |
| Hxfile | 5 | `Hxfile` + mirrors |
| ByseSX (AES-GCM JSON API) | 5 | `ByseSX`, `Bysezejataos`, `ByseBuho`… |
| StreamTape | 6 | `StreamTape`, `ShaveTape`, `Watchadsontape` |
| Uqload | 6 | `Uqload`, `Uqload1/2/cx/bz` |
| Vidmoly / LuluStream / Secvideo | 3 each | `Vidmoly`, `LuluStream`, `SecvideoOnline` |
| Ok.ru / Mail.ru | 4+3 | `OkRuSSL/HTTP(+Mobile)`, `MailRu`, `CloudMailRu` |
| Big platforms | ~10 | `Dailymotion`, `YoutubeExtractor` (expect/actual ×3 wrappers), `Cda`, `VkExtractor`, `Odnoklassniki`, `InternetArchive` |
| Direct file hosts | ~10 | `PixelDrain(+Dev)`, `Gofile`, `Mediafire`, `Krakenfiles`, `Userscloud`, `HubuCloud`, `Linkbox`, `Acefile`, `Flyfile` |
| Anti-bot "premium" CDNs | ~6 | `Rabbitstream`, `Dokicloud`, `Megacloud`, `GDMirrorbot` (smashy-style signed players) |
| Single-site players | rest | `Videa`, `Sendvid`, `Upstream`, `Vidoza`, `Tantifilm`, `Blogger`, `YourUpload`, `Odnoklassniki`, `SibNet`, `TauVideo`, `Jeniusplay`, `Streamlare`, `Vtbe`, … |

No torrent extractors exist — TORRENT/MAGNET links come from *providers* as
`ExtractorLink(type = TORRENT|MAGNET)` (enum at `EA:414-439` [verified]; the
torrent engine lives app-side in `APP/ui/player/Torrent.kt`).

### 3.3 Notable extractors, one-liners

| Extractor | What it does |
|---|---|
| `DoodLaExtractor` (`X/DoodExtractor.kt:95+`) | Classic dood dance: `/pass_md5/<hash>` + token → hidden mp4; regexes quality `\d{3,4}[pP]` from URL. |
| `StreamSB` (`X/StreamSB.kt:138+`) | Encodes video id as hex of `hashtable‖id‖hashtable‖streamsb`, hits the obfuscated `/37566435…/` API with `watchsb: sbstream` header, then `M3u8Helper.generateM3u8(...).forEach(callback)` + subs from `stream_data.subs`. Best "complete extractor" reference. |
| `JWPlayer` (`X/JWPlayer.kt:52-62`) | Grabs the `<script>` containing `sources:`, delegates to `JwPlayerHelper.extractStreamLinks` — the generic player-scraping pattern. |
| `PixelDrain` (`X/PixelDrainExtractor.kt:15-37`) | `/u/<id>` → `$mainUrl/api/file/<id>?download` direct-file link; passes raw URL through if no id — simplest possible extractor. |
| `Gofile` / `InternetArchive` / `Mediafire` (`X/Gofile.kt:15+` etc.) | Public-API file hosts: token/content-tree → direct file links. |
| `Dailymotion` (`X/Dailymotion.kt:22+`) | Fetches Dailymotion player metadata JSON; extracts qualities + subtitles. |
| `Rabbitstream` (`X/Rabbitstream.kt:77+`) | The "rabbit" signed-CDN family (smashy stream et al.) — extracts encrypted source list from the embed's JSON blob. |
| `Voe` / `Filesim` / `FilemoonV2` / `Mp4Upload` | The obfuscated-JS drillers: regex/unpack the embed script for the m3u8 or `sources:` JSON; domain mirrors galore. |
| `MixDrop` | Two-step form POST (`getPostForm` helper, `EA:1361-1395` — op/id/mode/hash fields + magic `delay(5000)`) then video URL regex. |
| `VidHidePro` (storm-ext copy, §6) | Packed JS → `var links` → m3u8 with `hls2:`/`hls4:` prefixes stripped. |
| `GenericM3U8` (`X/GenericM3U8.kt`) | Declared but **NOT registered** (commented out at `EA:1105`) — pass-through m3u8 helper class only. `[verified]` |

### 3.4 Helper classes (`extractors/helper/`)

| Helper | Purpose |
|---|---|
| `JwPlayerHelper` (`JWPlayerHelper.kt:15-50`) | Regex-parses `sources:` / `tracks:` / m3u8 URLs out of a jwplayer `setup({...})` script; maps each source to `ExtractorLink` (with `M3u8Helper`) and each track to `SubtitleFile`. The single most reused helper. |
| `AsianEmbedHelper` (`AsianEmbedHelper.kt:10-33`) | Fetches an "asian embed" server-list page, selects `div#list-server-more > ul > li.linkserver`, and calls `loadExtractor` on every `data-video` attr — a provider-grade aggregator usable inside any extractor/provider. |
| `CryptoJSHelper` (`CryptoJSHelper.kt:19-40`) | Pure-Kotlin reimplementation of CryptoJS's AES-CBC (`Salted__` KDF, MD5 key/iv) using `dev.whyoleg.cryptography` — for hosts that ship CryptoJS-encrypted payloads. |
| `NineAnimeHelper` (`NineAnimeHelper.kt:14-27`) | 9anime's VRF obfuscation (custom cipher + base64) ported from Saikou (GPL header preserved). |
| `WcoHelper` (`WcoHelper.kt:10-30`) | WCO site keys — fetches rotating cipher keys from a GitHub "keys backup" endpoint, AES-decrypts the stream config. |
| `AesHelper`, `GogoHelper`, `VstreamhubHelper` | AES util, gogoanime CDN (goload) decode, vstreamhub iframe decode (same family as AsianEmbed). |

### 3.5 Anti-bot / deobfuscation tooling

**`JsUnpacker`** (`utils/JsUnpacker.kt:6-60` [verified]) — P.A.C.K.E.R unpacker.
`detect()` matches `eval(function(p,a,c,k,e,[rd]`; `unpack()` regex-extracts the
payload/radix/count/symbol-table and substitutes tokens back. Used via the
convenience pair `getPacked(html)` / `getAndUnpack(html)` (`EA:893-901`) — the
standard first move in every packed-JS extractor (StreamWish, VidHidePro, Filesim…):
`if (!getPacked(res.text).isNullOrEmpty()) script = getAndUnpack(res.text)`.

**`JsInterpreter`** (`utils/JsInterpreter.kt:1-60` [verified]) — "Lightweight
pure-Kotlin JavaScript interpreter designed to replace Rhino for our own
deobfuscation use-cases". Supports the obfuscated-video-host JS subset (vars,
arithmetic/string/array ops, control flow, functions, template literals); every
evaluation is bounded by a wall-clock + instruction-count budget so hostile
`while(true){}` scripts can't hang the app. Exposed to plugins as `evalJs`/
`newJsContext` (03 §5 [docs]). Use when the payload is real obfuscated JS rather
than packed — i.e. `JsUnpacker` fails or the result still needs evaluating.

**`WebViewResolver`** (`library/.../network/WebViewResolver.kt:1-80` [verified]) —
expect/actual class (Android impl `WebViewResolver.android.kt`) that is BOTH an
OkHttp `Interceptor` and a suspend service:
```kotlin
expect class WebViewResolver(
    interceptUrl: Regex,
    additionalUrls: List<Regex> = emptyList(),
    userAgent: String? = USER_AGENT,
    useOkhttp: Boolean = true,
    script: String? = null,
    scriptCallback: ((String) -> Unit)? = null,
    timeout: Long = DEFAULT_TIMEOUT
) : Interceptor {
    suspend fun resolveUsingWebView(url: String, referer: String? = null, …): Pair<Request?, List<Request>>
}
```
It loads the page in a real WebView and waits until a request matches
`interceptUrl` (Cloudflare challenges execute; the surviving request carries the
cookies/UA you need). Typical extractor usage — as an *interceptor on app.get*:
```kotlin
val webViewM3u8Resolver = WebViewResolver(
    interceptUrl = Regex("""txt|m3u8"""),
    useOkhttp = false,           // disable for cloudflare
    timeout = 15_000L
)
val interceptedStreamUrl = app.get(url, referer = referer, interceptor = webViewM3u8Resolver).url
```
`STORM/AnimeJlProvider/.../extractors/StreamWishExtractor.kt:199-210` [verified].
(`app` = the global NiceHTTP `Requests` client, `library/.../cloudstream3/MainActivity.kt:30` [verified].)

**`M3u8Helper` / `M3u8Helper2`** (`utils/M3u8Helper.kt:16-130` [verified]) — the
m3u8 Swiss-army knife every extractor ends with: fetches the playlist (with
referer/headers), walks `#EXT-X-STREAM-INF` variants, regexes resolution →
`Qualities` int, follows/collapses variant playlists into one `ExtractorLink` per
quality (type `M3U8`), propagates headers, and can AES-128-decrypt encrypted
segments (`#EXT-X-KEY` handling, `ENCRYPTION_URL_IV_REGEX`, AES-CBC via
`dev.whyoleg.cryptography` — `M3u8Helper.kt:33-46, 78-111` [verified]). Call shape:
`M3u8Helper.generateM3u8(source, streamUrl, referer, headers=…).forEach(callback)`.
The deprecated-looking `class M3u8Helper` is a "backwards api surface" wrapper over
the real `object M3u8Helper2` (`M3u8Helper.kt:18-30`).

---

## 4. The app-side pipeline — ResultEpisode → RepoLinkGenerator → links

### 4.1 Cast of files

| File | Role |
|---|---|
| `APP/ui/result/ResultViewModel2.kt` (RVM2) | Details-page VM; builds the `RepoLinkGenerator`, handles episode-click actions |
| `APP/ui/player/RepoLinkGenerator.kt` (RLG) | `VideoGenerator<ResultEpisode>` — cache + dedup + label around `APIRepository.loadLinks` |
| `APP/ui/player/PlayerGeneratorViewModel.kt` (PGV) | Player VM: `VideoState`, runs `generateLinks`, preloads next episode |
| `APP/ui/player/IGenerator.kt` | `VideoGenerator` abstract + `LOADTYPE_*` source-type sets |
| `APP/ui/player/GeneratorPlayer.kt` (GP) | Full-screen player UI; source/subtitle dialogs; `extractorVerifierJob` launcher |
| `APP/ui/player/CS3IPlayer.kt` (IP) | Media3 glue; `getVideoInterceptor` consumer |
| `APP/ui/APIRepository.kt` (AR) | safeApiCall + timeout wrapper (§1.4) |

### 4.2 Generator construction (details page)

When the details page settles (dub/season/range selection), RVM2 builds ONE
generator for the whole episode list:

```kotlin
generator = if (isMovie) {
    getMovie()?.let { RepoLinkGenerator(listOf(it), page = currentResponse) }
} else {
    val episodes = currentEpisodes.filter { it.key.dubStatus == indexer.dubStatus }
        .toList()
        .sortedBy { it.first.season }
        .flatMap { it.second }
    RepoLinkGenerator(episodes, page = currentResponse)
}
```
`RVM2:2076-2085` [verified]; field `private var generator: RepoLinkGenerator? = null`
(`RVM2:483`). `page` (the whole `LoadResponse`) rides along so the player can read
metadata/sync data later (`PGV:398` `response = (gen as? RepoLinkGenerator)?.page`).

### 4.3 When does `loadLinks` actually run?

Five triggers, all funnelling into the same generator:

1. **In-app play** — user clicks episode → `ACTION_PLAY_EPISODE_IN_PLAYER`:
   `GeneratorPlayer.newInstance(generator, index, syncData)` → player fragment →
   `PlayerGeneratorViewModel.loadLinks()` → `generator.generateLinks(...)`
   (`RVM2:1546-1569`, `PGV:384-446` [verified]). Lazy: nothing was resolved before this.
2. **Any "pick one link" dialog** — download mirror, chromecast, external-player
   actions (`oneSource`) — `acquireSingleLink(...)` runs `loadLinks(result)` against
   a temp single-episode `RepoLinkGenerator` and shows a popup of ALL resolved links
   (`RVM2:1221-1243, 1265-1312` [verified]).
3. **"Reload links" episode action** — `ACTION_RELOAD_EPISODE` calls
   `loadLinks(click.data, isVisible = false, LOADTYPE_INAPP, clearCache = true)` —
   a headless warm-up that repopulates the shared cache so the next play is instant
   (`RVM2:1516-1529` [verified]).
4. **Chromecast start** — `startChromecast` → `loadLinks(..., isCasting = true,
   sourceTypes = LOADTYPE_CHROMECAST)` (`RVM2:813-827` [verified]).
5. **Next-episode preload** — while playing, `preLoadNextLinks()` runs
   `generateLinks(offset = episodeIndex + 1, callback = {}, subtitleCallback = {})`
   purely to warm the cache for the next episode (`PGV:281-312` [verified]).
   Guarded by `currentLoadingEpisodeId` to avoid duplicate jobs.

Note there is **no preload on details-page open** — resolution starts at click.

### 4.4 `RepoLinkGenerator.generateLinks` — the core (file:line walkthrough)

```kotlin
override suspend fun generateLinks(
    clearCache: Boolean,
    sourceTypes: Set<ExtractorLinkType>,
    callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
    subtitleCallback: (SubtitleData) -> Unit,
    offset: Int,
    isCasting: Boolean,
): Boolean {
    val current = videos.getOrNull(offset) ?: return false
```
`RLG:43-51`. `offset` = index into the generator's episode list (the player passes
its `episodeIndex`).

**Cache layer** (`RLG:15-22, 53-105`):
- **Companion-object, process-wide static cache**: `HashMap<Pair<String, Int>, Cache>`
  keyed by `(apiName, episodeId)`; `Cache` = `linkCache: MutableSet<ExtractorLink>` +
  `subtitleCache: MutableSet<SubtitleData>` + `lastCachedTimestamp` + `saturated`
  (`RLG:28-32`). Survives across player/VM instances until process death.
- **TTL 20 minutes**: `unixTime - lastCachedTimestamp > 60*20` → clear + unsaturate
  (`RLG:72-78`). `clearCache=true` (user reload / CustomMedia) force-clears.
- **Replay**: cached links/subs are re-emitted through the callbacks *before* any
  network call; if `saturated` (a previous run completed with ≥1 link,
  `RLG:154-157`), **return true without touching the provider at all** (`RLG:100-104`).
  This is what makes "skip loading" / instant second play work
  (`VideoGenerator.canSkipLoading = true`, `RLG:35`).

**Dedup + labeling** (`RLG:64-69, 112-151`):
- `currentLinksUrls` / `currentSubsUrls` — `ConcurrentHashMap.newKeySet<String>()`;
  a link whose URL was already seen this run (or in cache) is dropped
  (`if (link.url.isBlank() || !currentLinksUrls.add(link.url)) return@loadLinks`,
  `RLG:137-139`). Dedup key is the **URL only** — same URL from two hosts collapses.
- Subtitle names are uniquified for the UI: HTML-decoded (`%3Ch1%3E…` → `…`),
  then a per-name `AtomicInteger` suffix appended (`"1"`, `"2"`…) so two "English"
  tracks both stay selectable (`RLG:119-126`).
- **sourceTypes filter**: links are only forwarded to the UI callback if their
  `type` is in the requested set — `LOADTYPE_INAPP` = {VIDEO, DASH, M3U8, TORRENT,
  MAGNET}, `LOADTYPE_CHROMECAST` drops torrents, `LOADTYPE_INAPP_DOWNLOAD` =
  {VIDEO, M3U8} (`IGenerator.kt:6-25` [verified]; filter at `RLG:89, 143`).

**The actual call** (`RLG:107-152`):

```kotlin
val result = APIRepository(
    getApiFromNameNull(current.apiName) ?: throw Exception("This provider does not exist")
).loadLinks(
    current.data,
    isCasting = isCasting,
    subtitleCallback = { file -> … dedup → cache.add → subtitleCallback(updatedFile) },
    callback = { link -> … dedup → cache.add → if (sourceTypes.contains(link.type)) callback(Pair(link, null)) },
)
```
- Provider lookup by `ResultEpisode.apiName` via `APIHolder.getApiFromNameNull`
  (`MA:155-165` [verified]) — throws if the extension was uninstalled mid-session.
- The provider decides how links materialize: it may build `ExtractorLink`s itself
  (direct hosts, §6 BambooUA) **or** call `loadExtractor(embedUrl, referer, …)` per
  embed and let the extractor registry do the work (§2.3) — the app never calls
  `loadExtractor` directly.

### 4.5 Player-side consumption

`PlayerGeneratorViewModel.loadLinks` resets `VideoState` (loading + `GeneratorState`
with current/next meta) and streams generator callbacks into immutable state:
```kotlin
currentJob = viewModelScope.launchSafe {
    val loadingState = safeApiCall {
        generator?.generateLinks(
            sourceTypes = sourceTypes,
            clearCache = forceClearCache,
            callback = { link -> if (isActive) modifyState { add(link) } },
            isCasting = false,
            offset = index,
            subtitleCallback = { link -> if (isActive && isValidSubtitle(link)) modifyState { add(link) } })
        Unit
    } …
}
```
`PGV:384-446` [verified]. `VideoState` holds `PersistentSet<VideoLink>` /
`PersistentSet<SubtitleData>` / `erroredLinks` / `stamps` (`typealias VideoLink =
Pair<ExtractorLink?, ExtractorUri?>`, `PGV:34`; state class `PGV:55-165`).
`state.sortLinks(qualityProfile)` sorts by the user's quality-profile priority
(cached per profile in a `ConcurrentHashMap`), producing `DisplayLink(link,
shouldUseLink, priority)` used by the source dialog and auto-next-mirror logic
(`PGV:45-50, 81-116`; dialog `GP:1014-1133` [verified]). Default sort outside the
player is simply `sortUrls = urls.sortedBy { -it.quality }` (`MA:769-771` [verified]),
used by `acquireSingleLink` results (`RVM2:1308`).

### 4.6 `extractorData` flow — what it is, where it lives

- **What**: a free-form `String?` on every `ExtractorLink`
  (`@SerialName("extractorData") open var extractorData: String? = null`,
  `EA:710` [verified]) — KDoc on the field: "Used for getExtractorVerifierJob()".
  A provider sets it when the resolved link needs **active keep-alive** (originally
  sflix link-expiry polling — KDoc `MA:669-677`: "This job runs in the background
  when a link is playing in exoplayer… First implemented to do polling for sflix
  to keep the link from getting expired").
- **Where re-read**: exactly two call sites, both re-resolving the provider by the
  link's `source` name and running the suspend verifier until playback/download
  ends or the job is cancelled:

```kotlin
// Player:
private fun loadExtractorJob(extractorLink: ExtractorLink?) {
    currentVerifyLink?.cancel()
    extractorLink?.let { link ->
        currentVerifyLink = ioSafe {
            if (link.extractorData != null) {
                getApiFromNameNull(link.source)?.extractorVerifierJob(link.extractorData)
            }
        }
    }
}
```
`GP:256-268`, invoked from `loadLink(...)` when the user picks/plays a link
(`GP:524` [verified]). Download equivalent: `DM:1487-1495` — a parallel `ioSafe`
job cancelled when the download finishes (`utils/downloader/DownloadManager.kt:1490-1495` [verified]).
- **Persistence**: the `ExtractorLink` object (with `extractorData`) is cached in
  `RepoLinkGenerator`'s 20-min cache and, when a mirror download is queued, in the
  `DownloadObjects.DownloadQueueItem.links` list (`RVM2:1488-1513` [verified]) —
  that's how the verifier keeps running during an active download.
  It is NOT persisted to DataStore across app restarts (no serializer writes it —
  the persistent download queue stores a re-resolvable episode, and
  `ResultEpisode` has no link fields) `[inferred]`.
- **What breaks without it**: nothing crashes — the default
  `extractorVerifierJob` **throws `NotImplementedError`** (`MA:679-681`), and both
  call sites run it inside `ioSafe`/`safeApiCall` (player) or a plain `ioSafe`
  (download), so the exception is logged and swallowed. What *breaks* is the
  **link expiring mid-playback**: hosts that mint short-TTL URLs will 403 after
  N minutes and the player errors out with no automatic recovery
  `[inferred from KDoc + call sites]`. (Doc 03 §2.10 makes the same point:
  "Default throws, so don't set `extractorData` unless you implement it.")

### 4.7 `getVideoInterceptor` usage

Sole consumer — Media3 data-source construction in the player:

```kotlin
val provider = getApiFromNameNull(link.source)
val interceptor: Interceptor? = provider?.getVideoInterceptor(link)

val onlineSourceFactory = createVideoSource(
    link = link,
    engine = tryCreateEngine(context, simpleCacheSize),
    interceptor = interceptor
)
```
`APP/ui/player/CS3IPlayer.kt:1940-1947` [verified]; also threaded into subtitle and
audio source factories ("Backwards compatibility, needs a new api to work properly",
`IP:1952-1965`). Signature on the provider side:
`open fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? = null`
(`MA:701-704` [verified]). Purpose: per-link OkHttp interceptor for
`OkHttpDataSource` (e.g. injecting headers/cookies ExoPlayer wouldn't send).
Note it's a **provider** (MainAPI) hook, not an ExtractorApi hook — the link's
`source` string is used to find the provider that (usually) produced it.

---

## 5. Subtitles during loading

End-to-end path of one subtitle track:

```
Extractor/provider code
  → subtitleCallback(SubtitleFile(lang, url, headers?))          // built by newSubtitleFile (MA:1224-1236)
  → [provider] RepoLinkGenerator subtitleCallback lambda          // RLG:112-134
      • PlayerSubtitleHelper.getSubtitleData(file) → SubtitleData // PSH:111-121
          (originalName=lang, url, origin=URL, mimeType from extension: vtt/srt/ttml,
           headers, languageCode = langTag ?: lang)
      • dedup by URL; name HTML-decoded + uniqueness suffix "1"/"2"
      • Cache.subtitleCache.add + timestamp refresh
      • forwards SubtitleData (NOT SubtitleFile) upward
  → [player] PlayerGeneratorViewModel subtitleCallback             // PGV:421-426
      • isValidSubtitle(langFilterList) — optional language filter (PGV:369-382)
      • modifyState { add(sub) } → VideoState.subtitles (PersistentSet)
  → UI: source+subs dialog (GP:1021 sortSubs, GP:1125+), auto-select (GP:538-540)
  → Media3: CS3IPlayer.getSubSources builds SingleSampleMediaSources with
    SubtitleData.headers + mimeType (IP:1952-1965)                // [verified, cited in 4.7]
```

Key facts:
- `SubtitleData` (app model, `PSH:40-80`) is richer than `SubtitleFile`:
  `originalName`, `nameSuffix` (the dedup counter), `origin`
  (`URL | DOWNLOADED_FILE | EMBEDDED_IN_VIDEO`), `mimeType`, `headers`,
  `languageCode` + derived `getIETF_tag()` for auto-select, and `getFixedUrl()`
  that repairs `//proto-less` URLs (`PSH:26-79` [verified]).
- Subtitles are cached alongside links in the same 20-minute `Cache`
  (`RLG:16-17, 94-98, 128-133`) and replayed on re-entry.
- Downloads carry the resolved subs through `DownloadQueueManager.addToQueue(…
  listOf(result.links[index]), result.subs)` (`RVM2:1495-1507` [verified]) —
  external subtitle files are fetched into the download folder.
- On-subtitle-select "REQUIRES_RELOAD" semantics (activating a subtitle that
  arrived after playback started forces a source reload) come from
  `PlayerSubtitleHelper.subtitleStatus` (`PSH:124-132`) `[docs/verified]`.

---

## 6. Custom extractors in plugins

### 6.1 Registration

`BasePlugin.registerExtractorAPI` — the ONLY sanctioned way:

```kotlin
fun registerExtractorAPI(element: ExtractorApi) {
    Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) ExtractorApi")
    element.sourcePlugin = this.filename
    extractorApis.add(element)
}
```
`library/.../plugins/BasePlugin.kt:31-35` [verified]. Appended at the END of the
registry → the reverse iteration in `loadExtractor` (§2.3) means **plugin
extractors always shadow built-ins with the same `mainUrl`** (doc 03 §5 says the
same: "plugin extractors shadow built-ins"). Unloading removes them by
`sourcePlugin` (`PM:713-715`).

Real-world example — storm-ext's AnimeJlProvider registers 60+ extractors in its
plugin class `load()` (elided):

```kotlin
@CloudstreamPlugin
class AnimeJlProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeJlProvider())
        registerExtractorAPI(VidHidePro())          // + VidHidePro1-6, DhtpreCom, Ryderjet…
        registerExtractorAPI(StreamWishExtractor()) // + SaveFiles, Mwish, Dwish… 27 mirrors
        registerExtractorAPI(EmturbovidCom())       // … ByseSX / VidStack / Filesim families
    }
}
```
`STORM/AnimeJlProvider/src/main/kotlin/com/stormunblessed/AnimeJlProviderPlugin.kt:8-76` (elided) [verified].

### 6.2 The storm-ext pattern — and *why* custom extractors

The storm-ext extractors are **local forks of the built-in families** with
site-specific patches, plus a swarm of domain-only subclasses:

| Custom extractor | Why it exists (vs the built-in) |
|---|---|
| `StreamWishExtractor` (`STORM/.../extractors/StreamWishExtractor.kt:154-236`) | Adds: `/f/`-`/e/` embed-URL normalization (`resolveEmbedUrl`), three-stage script discovery (packed JS → `jwplayer("vplayer")` script → `sources:` script), and a **WebViewResolver fallback** when no m3u8 is found in static HTML. Registered with 27 domain mirrors (SaveFiles, Mwish, Dwish, …). |
| `VidHidePro` (`STORM/.../extractors/VidHidePro.kt`) | Fork of the built-in: same packed-JS flow but handles the `var links` prefix and `file:`/`hls2:`/`hls4:` m3u8 prefixes; ~15 mirrors including the "EarnVids" renames (`Smoothpre`, `Dhtpre`, `Peytonepre` all override `name = "EarnVids"` — branding-specific). |
| `ByseSX` (`STORM/.../extractors/ByseSX.kt:42-135`) | Genuinely new host: `/api/videos/<code>/embed/details` → `/embed/playback` JSON, then **AES-GCM decryption** of `payload` with the split `keyParts` + IV to recover the m3u8. No built-in equivalent exists for this API. |
| `Filesim`/`VidStack` families | Same story: updated regexes/packed-JS handling for domains the library version doesn't cover (or covers worse). |

**The takeaway**: plugins need custom extractors when (a) a host family changed
its obfuscation and the plugin wants a patched resolver NOW (no need to wait for a
library release), (b) the host is new/private (ByseSX), or (c) they want to
re-register a known family under extra mirror domains. The mirror-subclass pattern
(`class X : Family() { override var mainUrl = … }`) keeps the cost at one line per
domain.

### 6.3 Real `loadLinks` examples (provider side)

**(a) Cinecalidad — the canonical loadExtractor fan-out** (movies, Latin Spanish):
```kotlin
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    app.get(data).document.select(".linklist ul li").amap {
        val url = it.select("li").attr("data-option")
        loadExtractor(fixHostsLinks(url), mainUrl, subtitleCallback, callback)
    }
    return true
}
```
`STORM/CinecalidadProvider/.../CinecalidadProvider.kt:116-127` [verified].
`data` = the movie page URL itself; every `<li data-option>` is an embed URL,
rewritten by a local `fixHostsLinks` (mirror→canonical domain map, lines 130-144)
before hitting the registry — the mirror-patching trick that makes fuzzy matching
rarely needed.

**(b) BambooUA — direct resolution, no extractors at all** (Ukrainian):
```kotlin
override suspend fun loadLinks(…): Boolean {
    if (data.startsWith("https://bambooua.com")) {
        val playlistJson = playlistRegex.find(document.html())?.groupValues?.get(1)
        … playlist.forEach { group ->
            bambooPlaylistFiles(group).forEach { file ->
                M3u8Helper.generateM3u8(source = group.title, streamUrl = file,
                    referer = "$mainUrl/").forEach(callback)
            } }
        return true
    }
    // Serial — data IS the m3u8 link already
    M3u8Helper.generateM3u8(source = "BambooUA", streamUrl = data,
        referer = "$mainUrl/").forEach(callback)
    return true
}
```
`CakesTwix-ext/BambooUAProvider.kt:199-235` (condensed) [verified]. Shows the
other extreme: `data` is either a page URL with an embedded JSON playlist or a
direct m3u8; `M3u8Helper` expands qualities and everything flows through
`callback` — the extractor registry is bypassed entirely.

**(c) AnimeJlProvider — multi-hop redirect chain** (anime):
page → `var video = [];` script → URLs → for `holuagency.top/load.php` links:
follow JS redirect → POST form (`token/back/sh`) → copy-link page →
`div#player iframe` src → `loadExtractor(fixHostsLinks(it), data, …)`;
other URLs go straight to `loadExtractor`
(`STORM/AnimeJlProvider/.../AnimeJlProvider.kt:146-199` [verified]).

**(d) Subtitle streaming** — UAFlixProvider fires subs per dub-season group:
```kotlin
M3u8Helper.generateM3u8(source = dubs.title, streamUrl = episode.file, …).dropLast(1).forEach(callback)
parseUAFlixSubtitle(episode.subtitle)?.let { subtitle ->
    subtitleCallback.invoke(newSubtitleFile(subtitle.language, subtitle.url))
}
```
`CakesTwix-ext/UAFlixProvider.kt:294-321` [verified] — same callback-parallel
structure: one `callback` fan-out for streams, one `subtitleCallback` per track.

---

## 7. Sequence — "user clicks episode" → "player has links"

```
 User                ResultViewModel2            RepoLinkGenerator           APIRepository/Provider        ExtractorApi (registry)        PlayerGeneratorViewModel / Player UI
  │                        │                            │                            │                            │                            │
  │ click episode ─────────▶ handleEpisodeClickEvent     │                            │                            │                            │
  │                        │ (RVM2:1357)                 │                            │                            │                            │
  │                        │ ACTION_PLAY_EPISODE_IN_PLAYER│                           │                            │                            │
  │                        │ GeneratorPlayer.newInstance(generator, index) ────────────────────────────────────────────────────────────────────────────▶ player opens
  │                        │                            │                            │                            │                            │
  │                        │                            │                            │                            │   PGV.loadLinks() (PGV:384)  │
  │                        │                            │                            │                            │   generator.generateLinks(clearCache=forceClearCache,
  │                        │                            │◀───────────────────────────────────────────────────────────── offset=index, sourceTypes=LOADTYPE_INAPP)
  │                        │                            │                            │                            │                            │
  │                        │                 [cache lookup (apiName,id)]             │                            │                            │
  │                        │                 replay cached links+subs ─────────────────────────────────────────────────────────────────▶ modifyState{add} (links appear instantly)
  │                        │                 saturated? ── yes ──▶ return true ─ done │                            │                            │
  │                        │                            │                            │                            │                            │
  │                        │                 APIRepository(provider).loadLinks(ResultEpisode.data, isCasting=false, …)
  │                        │                            │───────────────────────────▶ isInvalidData? no                    │                            │
  │                        │                            │                            │ withTimeout(5s..480s)      │                            │
  │                        │                            │                            │ api.loadLinks(data, …)     │                            │
  │                        │                            │                            │                            │                            │
  │                        │                            │                            │ [provider parses page]      │                            │
  │                        │                            │                            │── per embed url: loadExtractor(url, referer, …) ─▶│                       │
  │                        │                            │                            │                            │ unshorten → reverse walk registry:
  │                        │                            │                            │                            │  prefix match on mainUrl (or Levenshtein>80)
  │                        │                            │                            │                            │  ONE extractor runs getUrl()
  │                        │                            │                            │                            │  (packed-JS unpack / WebView / API+AES …)
  │                        │                            │                            │                            │  M3u8Helper → per quality:
  │                        │                            │◀──────────────── callback(ExtractorLink) ──────────────────────────┘                       │
  │                        │                            │  dedup by URL → Cache.linkCache.add ───────────────────────────────────────▶ callback(link) → modifyState{add(link)}
  │                        │                            │◀────────────── subtitleCallback(SubtitleFile) ─────────────────────                        │
  │                        │                            │  → SubtitleData, dedup, suffix, cache ────────────────────────────────────▶ subtitleCallback → modifyState{add(sub)}
  │                        │                            │                            │                            │                            │
  │                        │                            │◀── loadLinks returns Boolean (timeout-safe) ──│                            │                            │
  │                        │                            │ Cache.saturated = links.isNotEmpty(); timestamp=now (RLG:154-157)                  │                            │
  │                        │                            │──────────────────────────── generateLinks returns ──────────────────────────────────▶ state.loading = Success/Error
  │                        │                            │                            │                            │                            │
  │ user opens "sources" dialog (GP:1014) / auto-pick ──────────────────────────────────────────────────────────────────────────▶ loadLink(VideoLink) (GP:499)
  │                        │                            │                            │                            │                            │ loadExtractorJob(link) (GP:258) — if link.extractorData != null:
  │                        │                            │                            │                            │                            │   provider.extractorVerifierJob(data) runs until cancelled (GP:264)
  │                        │                            │                            │                            │                            │ player.loadPlayer(url, headers, subs…) (GP:529)
  │                        │                            │                            │                            │                            │ CS3IPlayer: getVideoInterceptor(link) (IP:1941) → MediaSources → ExoPlayer
  │                        │                            │                            │                            │                            │
  │ (background) next-episode preload: PGV.preLoadNextLinks → generateLinks(offset+1, callbacks={}) warms the shared cache (PGV:281-312)
```

---

## 8. ANI-KUTA mapping preview

Our current pipeline (`AK/`):

- **UI**: `feature/anime-details/impl/.../ResolverSheet.kt` — ModalBottomSheet with
  a **Server → AudioVersion → Video accordion** (`ResolverTypes.kt:29-70`):
  `ResolverServer(name, audioVersions[ResolverAudioVersion(label, videos[ResolverVideo(quality, url, directUrl, videoTitle, videoHeaders, subtitleTracks, audioTracks)])])`
  [verified]. States: Idle/Loading/Error(+Open-in-WebView for Cloudflare)/Success.
- **Model**: `ResolvedVideo(url, quality, directUrl, headers /* single String "K: V,K2: V2" */, subtitleTracks, audioTracks)` (`ResolverState.kt:40-49` [verified]);
  extension-facing `SourceVideo(url, quality, videoUrl?)` (`core/provider-api/.../SourceVideo.kt:10-14` [verified]).
- **Fetching**: `VideoExtensionProvider.fetchVideoList(episode): Flow<List<SourceVideo>>`
  (`VideoExtensionProvider.kt` [verified]) — one-shot Flow, no streaming callbacks.
- **Downloads**: `ReResolver.reResolve(context: ResolveContext{mainId, serverName,
  audioLabel, quality}, source, episode)` re-runs `VideoResolver.resolve` and
  re-pins by (server, audio, quality) — the proxy-churn fix; caps 1 re-resolve
  (`app/.../download/ReResolver.kt:18-80` [verified]).

### 8.1 What CS3 gives us / confirms

- **Callback-streaming beats one-shot Flow for link generation**: CS3's
  `loadLinks`/`getUrl` push each link the instant it's resolved so the picker UI
  fills live and the user can start watching the first mirror while slow hosts are
  still resolving. Our `fetchVideoList: Flow<List<SourceVideo>>` only emits a
  terminal list; `ResolverSheet` shows a spinner until everything's done.
  → candidate: `Flow<SourceVideo>` (or a callback-in-flight wrapper) so ResolverSheet
  can render incrementally.
- **A 20-minute, process-wide, (source, episode)-keyed link cache with
  `saturated` short-circuit** (RLG) — our ResolverState is rebuilt from scratch on
  every sheet open; CS3 proves the cache makes "skip loading"/instant replay cheap.
- **The extractor registry is a genuinely reusable subsystem**: 97 base extractors
  + 321 mirror registrations + WebView/JS-unpack/M3u8 tooling out of the box. Our
  aniyomi-based extensions do their own resolving inside the extension process;
  hosting CS3 plugins would give us their extractor arsenal "for free" via
  `loadExtractor` — the URL→extractor rule (§2.3) is the only contract needed.
- **`extractorData` + `extractorVerifierJob`**: a clean answer to expiring links —
  a per-link async keep-alive job owned by the provider, launched by the *player*
  on link select and cancelled on switch. We currently have nothing equivalent;
  expiring-URL hosts will just die mid-watch.
- **`getVideoInterceptor`** — per-link OkHttp interceptor hook for the player's
  data source. Our headers flow as a formatted String into MPV; an interceptor
  hook would matter if/when we adopt Media3/OkHttp-based playback or downloads.
- **Type-aware link filtering** (`LOADTYPE_INAPP`/`CHROMECAST`/`DOWNLOAD` sets on
  `ExtractorLinkType`) — cast/download-safe subsets are computed centrally, not
  per-provider.
- **Mirror handling via domain-subclass swarm + `fixHostsLinks` rewriting** —
  cheap operational trick for the constant domain churn of pirate CDNs.

### 8.2 Gaps in our model to flag for the CS3 integration design

- `[gap]` **`SourceVideo` loses headers/referer** — only `ResolverVideo.videoHeaders`
  (MPV-format string) survives to the player; `SourceVideo(url, quality, videoUrl)`
  has **no headers, no referer, no type, no extractorData, no audioTracks** —
  hosts requiring Referer/UA can't be expressed through the provider-api contract.
  CS3's `ExtractorLink` carries all of them (05 §7.3).
- `[gap]` **No container/type tag** — CS3 tags every link `VIDEO|M3U8|DASH|TORRENT|MAGNET`
  and gates casting/downloads on it; we can't distinguish HLS from MP4 in the model
  (quality label string only).
- `[gap]` **No keep-alive channel** — no `extractorData` equivalent; `ReResolver`
  re-resolves only on download IO errors, not proactively during playback.
- `[gap]` **No dedup/label layer** — CS3 dedups by URL and suffixes duplicate
  subtitle names centrally (RLG); we pass through whatever the extension returns.
- `[gap]` **Subtitles second-class** — our `ResolverSubtitleTrack(url, lang)` has no
  headers/mimeType; CS3 `SubtitleFile.headers` + `SubtitleData.mimeType`+IETF tags
  drive correct fetching and auto-select.
- `[gap]` **No plugin-extractor concept** — aniyomi extensions embed their resolvers;
  a CS3-style shared registry (plugin extractors shadowing built-ins, unload-safe
  via `sourcePlugin`) would decouple resolver maintenance from provider releases.

---

## 9. Unverified / open items

- `instantLinkLoading` (`MA:549`) and `requireReferer(name)` (`EA:1353`): verified
  as **declared-but-unread** in this snapshot's app+library; third-party plugin
  repos could still read them (none in `/research` do) `[verified — absence]`.
- Whether `ExtractorLink` instances (incl. `extractorData`) survive app restarts:
  no DataStore serializer was found writing them; the download queue re-resolves
  instead. `[inferred]`
- The exact set of hosts reachable through the fuzzy `Levenshtein > 80` pass in
  production — a runtime property; only the mechanism is verified.
- `WebViewResolver` JVM/desktop actual (`WebViewResolver.jvm.kt`) was not read —
  Android path is the one document; expect-class semantics verified only on the
  common declaration.
- `LOADTYPE_INAPP_DOWNLOAD` excludes DASH "no support at the moment"
  (`EA:414-424` KDoc) — download-side enforcement was not traced beyond
  `DM:1482-1484` (which also rejects TORRENT/MAGNET/DASH).
