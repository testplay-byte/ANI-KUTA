# 06 — Video Resolution Pipeline

> How Animiru resolves a video URL from an episode, including the
> `Video` / `Hoster` data model, the ext-lib 16 `getHosterList` vs
> `getVideoList` API split, header passing, proxy URLs, and the
> resolver service/repository pattern.

## 1. The data model

### `Video` — a single resolvable video stream

```kotlin
// source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/Video.kt:29-104
@Stable // mutability only concerns the downloader
data class Video(
    var videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
) {
    @Transient
    @Volatile
    var status: State = State.QUEUE
        set(value) {
            field = value
        }

    enum class State {
        QUEUE,
        LOAD_VIDEO,
        READY,
        ERROR,
    }

    companion object {
        const val MPV_ARGS_TAG = "ANIYOMI_MPV_ARGS"
    }
}
```

Fields worth understanding:
- **`videoUrl`** — the actual streamable URL passed to mpv `loadfile`. May
  be empty initially for ext-lib 16 lazy hosters (resolved later).
- **`videoTitle`** — what's shown in the QualitySheet (e.g. `"1080p — Server A"`).
- **`resolution`** / **`bitrate`** — optional metadata, currently unused
  by the player UI (the title string carries quality info).
- **`headers`** — per-video HTTP headers (overrides source headers).
- **`preferred`** — hint to the loader that this is the source's preferred
  video (gets auto-selected if no user preference).
- **`subtitleTracks`** / **`audioTracks`** — external subtitle/audio
  URLs bundled with the video by the extension.
- **`timestamps`** — AniSkip / chapter timestamps from the extension.
- **`mpvArgs`** — per-video MPV options passed as the 5th arg of `loadfile`.
- **`ffmpegStreamArgs`** / **`ffmpegVideoArgs`** — for the download manager
  (FFmpeg-based stream download).
- **`internalData`** — opaque string the extension can use to pass data
  through the resolver.
- **`initialized`** — false until `source.resolveVideo(video)` has been
  called. Prevents re-resolution.
- **`status`** — UI state (`QUEUE` / `LOAD_VIDEO` / `READY` / `ERROR`).
  Marked `@Volatile @Transient` — not serialized, used by QualitySheet.

There are also legacy constructors marked `@Deprecated` that map the old
`url`/`quality`/`videoUrl` API to the new `videoTitle`/`videoUrl` API.
These exist for binary compatibility with old extensions (ext-lib <1.6).

### `Hoster` — a streaming-mirror server

```kotlin
// source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/animesource/model/Hoster.kt:8-49
open class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
) {
    @Transient
    @Volatile
    var status: State = State.IDLE

    enum class State {
        IDLE,
        LOADING,
        READY,
        ERROR,
    }

    fun copy(
        hosterUrl: String = this.hosterUrl,
        hosterName: String = this.hosterName,
        videoList: List<Video>? = this.videoList,
        internalData: String = this.internalData,
        lazy: Boolean = this.lazy,
    ): Hoster {
        return Hoster(hosterUrl, hosterName, videoList, internalData, lazy)
    }

    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        fun List<Video>.toHosterList(): List<Hoster> {
            return listOf(
                Hoster(
                    hosterUrl = "",
                    hosterName = NO_HOSTER_LIST,
                    videoList = this,
                ),
            )
        }
    }
}
```

Fields:
- **`hosterUrl`** — the URL to fetch the video list from (for lazy hosters).
- **`hosterName`** — display name (e.g. `"Vidstream"`).
- **`videoList`** — null for lazy hosters (not yet fetched); non-null for
  eager hosters (extension already resolved the list).
- **`internalData`** — opaque extension data.
- **`lazy`** — if true, the player won't fetch this hoster's videos until
  the user explicitly expands it in the QualitySheet.

The `NO_HOSTER_LIST` constant is a sentinel: when an old ext-lib <1.6
source returns a flat `List<Video>`, it's wrapped in a single Hoster with
this name. The QualitySheet detects this and renders a flat video list
instead of the accordion.

### `SerializableHoster` / `SerializableVideo`

For passing hosters across Activities (via `Intent.putExtra`), Animiru
serializes them to JSON:

```kotlin
// source-api/.../model/Hoster.kt:51-85
@Serializable
data class SerializableHoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: String? = null,
    val internalData: String = "",
    val lazy: Boolean = false,
) {
    companion object {
        fun List<Hoster>.serialize(): String =
            Json.encodeToString(
                this.map { host ->
                    SerializableHoster(
                        host.hosterUrl,
                        host.hosterName,
                        host.videoList?.serialize(),
                        host.internalData,
                        host.lazy,
                    )
                },
            )

        fun String.toHosterList(): List<Hoster> =
            Json.decodeFromString<List<SerializableHoster>>(this)
                .map { sHost ->
                    Hoster(
                        sHost.hosterUrl,
                        sHost.hosterName,
                        sHost.videoList?.toVideoList(),
                        sHost.internalData,
                        sHost.lazy,
                    )
                }
    }
}
```

Same pattern for `SerializableVideo` (`Video.kt:106-170`).

This serialization is used in `PlayerActivity.newIntent`:
```kotlin
// PlayerActivity.kt:128
hostList?.let { putExtra("hostList", it.serialize()) }
```

When the user picks a video in the QualitySheet and then navigates away
+ back, the hoster list is preserved across the Activity recreation.

## 2. The ext-lib 16 API split

The `AnimeHttpSource` abstract class defines both old and new APIs.
Animiru detects at runtime which one the extension implements:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt:63-99
private fun checkHasHosters(source: AnimeHttpSource): Boolean {
    var current: Class<in AnimeHttpSource> = source.javaClass
    while (true) {
        if (current == ParsedAnimeHttpSource::class.java ||
            current == AnimeHttpSource::class.java ||
            current == AnimeSource::class.java
        ) {
            return false
        }
        if (current.declaredMethods.any {
                it.name in
                    listOf("getHosterList", "hosterListRequest", "hosterListParse")
            }
        ) {
            return true
        }
        current = current.superclass ?: return false
    }
}

private suspend fun getHostersOnHttp(episode: Episode, source: AnimeHttpSource): List<Hoster> {
    // TODO(16): Remove else block when dropping support for ext lib <1.6
    return if (checkHasHosters(source)) {
        source.getHosterList(episode.toSEpisode())
            .let { source.run { it.sortHosters() } }
    } else {
        source.getVideoList(episode.toSEpisode())
            .let { source.run { it.sortVideos() } }
            .toHosterList()
    }
}
```

The `checkHasHosters` reflection check walks up the source's class
hierarchy looking for `getHosterList`, `hosterListRequest`, or
`hosterListParse` methods. If found, the source is ext-lib 16+ and uses
the new two-step API. If not, the source is ext-lib <1.6 and uses the
old single-step API.

### Old API (ext-lib <1.6) — `getVideoList(episode)`

```kotlin
// source-api/.../online/AnimeHttpSource.kt:425-454
override suspend fun getVideoList(episode: SEpisode): List<Video> {
    return fetchVideoList(episode).awaitSingle()
}

@Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoList"))
override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
    return client.newCall(videoListRequest(episode))
        .asObservableSuccess()
        .map { response ->
            videoListParse(response)
        }
}

protected open fun videoListRequest(episode: SEpisode): Request {
    return GET(baseUrl + episode.url, headers)
}

protected abstract fun videoListParse(response: Response): List<Video>
```

One HTTP request, one parse, returns a flat list of `Video`s. The
`EpisodeLoader.getHostersOnHttp` wraps these in a single `Hoster` with
`hosterName = NO_HOSTER_LIST`. The QualitySheet renders this as a flat
list.

### New API (ext-lib 16+) — two-step

```kotlin
// source-api/.../online/AnimeHttpSource.kt:340-404
override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
    return client.newCall(hosterListRequest(episode))
        .awaitSuccess()
        .let { response ->
            hosterListParse(response)
        }
}

protected open fun hosterListRequest(episode: SEpisode): Request {
    return GET(baseUrl + episode.url, headers)
}

protected abstract fun hosterListParse(response: Response): List<Hoster>

override suspend fun getVideoList(hoster: Hoster): List<Video> {
    return client.newCall(videoListRequest(hoster))
        .awaitSuccess()
        .let { response ->
            videoListParse(response, hoster)
        }
}

protected open fun videoListRequest(hoster: Hoster): Request {
    return GET(hoster.hosterUrl, headers)
}

protected abstract fun videoListParse(response: Response, hoster: Hoster): List<Video>
```

Step 1: `getHosterList(episode)` — fetches the list of hosters for an
episode. Returns `List<Hoster>` with `videoList = null` (lazy) or
`videoList = [...]` (eager).

Step 2: `getVideoList(hoster)` — for a lazy hoster, fetches its video
list. Eager hosters already have their `videoList` populated, so this
step is skipped.

### `resolveVideo` — the third step

Some extensions return `Video` objects with empty `videoUrl`. The
extension must then resolve the URL on demand:

```kotlin
// source-api/.../online/AnimeHttpSource.kt:413-416
open suspend fun resolveVideo(video: Video): Video? {
    return video
}
```

Default implementation returns the video unchanged. Sources that need
resolution override this (e.g. to fetch a tokenized URL).

The `HosterLoader.getResolvedVideo` calls this:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt:152-168
suspend fun getResolvedVideo(source: AnimeSource?, video: Video): Video? {
    val resolvedVideo = if (source is AnimeHttpSource && !video.initialized) {
        try {
            source.resolveVideo(video)
        } catch (e: Exception) {
            if (e is CancellationException) {
                throw e
            }
            null
        }
    } else {
        video
    }

    return resolvedVideo?.copy(initialized = true)
}
```

Note: the returned video is marked `initialized = true` to prevent
re-resolution.

## 3. The full resolver pipeline

```
EpisodeLoader.getHosters(episode, anime, source)
    │
    ├─ if downloaded: getHostersOnDownloaded (builds Video from local file)
    ├─ if source is AnimeHttpSource:
    │     ├─ if ext-lib 16 (checkHasHosters): source.getHosterList(episode) → sortHosters()
    │     └─ else: source.getVideoList(episode) → sortVideos() → toHosterList()
    └─ if source is LocalSource: getHostersOnLocal (reads SAF file)
    │
    ▼
PlayerViewModel.loadHosters(hosterList, hostIndex, videoIndex)
    │
    │  (per hoster, async)
    ▼
EpisodeLoader.loadHosterVideos(source, hoster, force = false)
    │
    ├─ if hoster.lazy && !force: return HosterState.Idle(name)
    ├─ else: getVideos(source, hoster)
    │        ├─ if hoster.videoList != null && source is AnimeHttpSource:
    │        │     hoster.videoList.parseVideoUrls(source)  ← legacy <1.6 path
    │        ├─ if hoster.videoList != null: hoster.videoList
    │        └─ else (source is AnimeHttpSource): source.getVideoList(hoster).parseVideoUrls(source)
    │
    └─ sortVideos() → HosterState.Ready(name, videos, [QUEUE] * videos.size)
    │
    ▼
PlayerViewModel.loadVideo(video, hosterIdx, videoIdx)
    │
    ├─ if videoState != READY:
    │     HosterLoader.getResolvedVideo(source, video)
    │       → source.resolveVideo(video) if !initialized
    │       → returns Video with initialized=true
    │
    ├─ if resolvedVideo.videoUrl.isEmpty():
    │     try next-best video via HosterLoader.selectBestVideo
    │
    └─ else:
          setVideo(resolvedVideo)
            ├─ setHttpOptions(video) — http-header-fields
            ├─ set "start" position
            └─ mpv.command("loadfile", url, "replace", "0", videoOptions)
```

## 4. The `EpisodeLoader.getHosters` dispatch

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt:33-41
suspend fun getHosters(episode: Episode, anime: Anime, source: AnimeSource): List<Hoster> {
    val isDownloaded = isDownload(episode, anime)
    return when {
        isDownloaded -> getHostersOnDownloaded(episode, anime, source)
        source is AnimeHttpSource -> getHostersOnHttp(episode, source)
        source is LocalSource -> getHostersOnLocal(episode)
        else -> error("source not supported")
    }
}
```

Three source types:
1. **Downloaded** — uses `downloadManager.buildVideo(source, anime, episode)`
   to construct a single `Video` pointing at the local file.
2. **HTTP source** — calls `getHosterList` (ext-lib 16) or `getVideoList`
   (ext-lib <1.6).
3. **Local source** — reads from SAF: `fileSystem.getBaseDirectory().findFile(animeDirName).findFile(episodeName)`.

### `isDownload` check

```kotlin
// EpisodeLoader.kt:49-61
fun isDownload(episode: Episode, anime: Anime): Boolean {
    val downloadManager: DownloadManager = Injekt.get()
    return downloadManager.isEpisodeDownloaded(
        episode.name,
        episode.scanlator,
        episode.url,
        anime.ogTitle,
        anime.source,
        skipCache = true,
    )
}
```

`skipCache = true` forces a fresh filesystem check rather than relying
on the in-memory download cache.

## 5. The `EpisodeLoader.loadHosterVideos` — single-hoster fetch

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/EpisodeLoader.kt:192-207
suspend fun loadHosterVideos(source: AnimeSource, hoster: Hoster, force: Boolean = false): HosterState {
    if (!force && hoster.lazy) {
        return HosterState.Idle(hoster.hosterName)
    }

    return try {
        val videos = getVideos(source, hoster)
        HosterState.Ready(hoster.hosterName, videos, List(videos.size) { Video.State.QUEUE })
    } catch (e: Exception) {
        if (e is CancellationException) {
            throw e
        }

        HosterState.Error(hoster.hosterName)
    }
}
```

- Lazy hosters return `Idle` (QualitySheet shows "Tap to load").
- Force flag overrides laziness — used when the user taps an idle hoster.
- Errors become `HosterState.Error` (caught + swallowed except for
  `CancellationException`).

### `getVideos` (private)

```kotlin
// EpisodeLoader.kt:156-169
private suspend fun getVideos(source: AnimeSource, hoster: Hoster): List<Video> {
    val videos = when {
        hoster.videoList != null && source is AnimeHttpSource -> hoster.videoList!!.parseVideoUrls(source)
        hoster.videoList != null -> hoster.videoList!!
        source is AnimeHttpSource -> getVideosOnHttp(source, hoster)
        else -> error("source not supported")
    }

    return if (source is AnimeHttpSource) {
        source.run { videos.sortVideos() }
    } else {
        videos
    }
}
```

Three branches:
1. **Eager hoster + HTTP source** — videos are pre-populated, but URLs
   may need parsing via `source.getVideoUrl(video)` (legacy <1.6 path).
2. **Eager hoster + non-HTTP source** — videos are pre-populated, use as-is.
3. **Lazy hoster + HTTP source** — call `source.getVideoList(hoster)`.

### `parseVideoUrls` (legacy compat)

```kotlin
// EpisodeLoader.kt:182-190
private suspend fun List<Video>.parseVideoUrls(source: AnimeHttpSource): List<Video> {
    return this.map { video ->
        if (video.videoUrl != "null") return@map video

        val newVideoUrl = source.getVideoUrl(video)
        video.copy(videoUrl = newVideoUrl)
    }
}
```

Old extensions return `Video` with `videoUrl = "null"` (string) when the
URL needs to be fetched separately. The `parseVideoUrls` extension
function calls `source.getVideoUrl(video)` for each such video, which
makes another HTTP request to resolve the URL.

This is the legacy path — ext-lib 16+ sources return fully-resolved
`videoUrl`s directly.

## 6. The `HosterLoader.selectBestVideo` — fallback selection

When the user hasn't specified a hoster/video, the loader picks one:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt:27-67
fun selectBestVideo(hosterState: List<HosterState>): Pair<Int, Int> {
    val availableHosters = hosterState.withIndex()
        .filter { (_, state) -> state is HosterState.Ready }

    // Check for first preferred
    val isPreferred: (Pair<Video, Video.State>) -> Boolean = { (v, s) ->
        v.preferred && (s == Video.State.READY || s == Video.State.QUEUE)
    }
    val prefHosterIdx = availableHosters.indexOfFirst {
        (it.value as HosterState.Ready).let { hoster ->
            hoster.videoList zip hoster.videoState
        }.any(isPreferred)
    }
    if (prefHosterIdx != -1) {
        val videoList = (availableHosters[prefHosterIdx].value as HosterState.Ready).let { hoster ->
            hoster.videoList zip hoster.videoState
        }
        val prefVideoIdx = videoList.indexOfFirst(isPreferred)
        return availableHosters[prefHosterIdx].index to prefVideoIdx
    }

    // Check for first video with non-empty url
    val firstValid: (Pair<Video, Video.State>) -> Boolean = { (v, s) ->
        (v.videoUrl.isNotEmpty() && s == Video.State.READY) || s == Video.State.QUEUE
    }
    val firstAvailableHosterIdx = availableHosters.indexOfFirst {
        (it.value as HosterState.Ready).let { hoster ->
            hoster.videoList zip hoster.videoState
        }.any(firstValid)
    }
    if (firstAvailableHosterIdx != -1) {
        val videoList = (availableHosters[firstAvailableHosterIdx].value as HosterState.Ready).let { hoster ->
            hoster.videoList zip hoster.videoState
        }
        val firstVideoIdx = videoList.indexOfFirst(firstValid)
        return availableHosters[firstAvailableHosterIdx].index to firstVideoIdx
    }

    // No success
    return Pair(-1, -1)
}
```

Two-pass selection:
1. **Preferred** — first video with `preferred = true` and state `READY`
   or `QUEUE`.
2. **First valid** — first video with non-empty `videoUrl` and state
   `READY`, or any `QUEUE` video.

Returns `(-1, -1)` if no valid video found.

## 7. `HosterLoader.getBestVideo` — the streaming variant

There's a sibling method `getBestVideo` that's used when you want to
resolve a single video without going through the full UI flow (e.g.
external intent). It runs the same `selectBestVideo` logic but in a
streaming fashion, returning as soon as a preferred video resolves:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/loader/HosterLoader.kt:78-150
suspend fun getBestVideo(source: AnimeSource, hosterList: List<Hoster>): Video? {
    val hosterStates = MutableList<HosterState>(hosterList.size) { HosterState.Idle("") }

    return try {
        withContext(Dispatchers.IO) {
            hosterList.mapIndexed { hosterIdx, hoster ->
                async {
                    val hosterState = EpisodeLoader.loadHosterVideos(source, hoster)
                    hosterStates[hosterIdx] = hosterState

                    if (hosterState is HosterState.Ready) {
                        val prefIndex = hosterState.videoList.indexOfFirst { it.preferred && !it.initialized }
                        if (prefIndex != -1) {
                            val video = hosterState.videoList[prefIndex]
                            hosterStates[hosterIdx] =
                                (hosterStates[hosterIdx] as HosterState.Ready).getChangedAt(
                                    prefIndex,
                                    video,
                                    Video.State.LOAD_VIDEO,
                                )

                            val resolvedVideo = getResolvedVideo(source, video)
                            if (resolvedVideo?.videoUrl?.isNotEmpty() == true) {
                                coroutineContext.cancelChildren()
                                throw EarlyReturnException(resolvedVideo)
                            }

                            hosterStates[hosterIdx] =
                                (hosterStates[hosterIdx] as HosterState.Ready).getChangedAt(
                                    prefIndex,
                                    video,
                                    Video.State.ERROR,
                                )
                        }
                    }
                }
            }.awaitAll()

            var (hosterIdx, videoIdx) = selectBestVideo(hosterStates)
            while (hosterIdx != -1) {
                val hosterState = hosterStates[hosterIdx] as HosterState.Ready
                val video = hosterState.videoList[videoIdx]
                hosterStates[hosterIdx] =
                    (hosterStates[hosterIdx] as HosterState.Ready).getChangedAt(
                        videoIdx,
                        video,
                        Video.State.LOAD_VIDEO,
                    )

                val resolvedVideo = getResolvedVideo(source, video)
                if (resolvedVideo?.videoUrl?.isNotEmpty() == true) {
                    coroutineContext.cancelChildren()
                    return@withContext resolvedVideo
                }

                hosterStates[hosterIdx] =
                    (hosterStates[hosterIdx] as HosterState.Ready).getChangedAt(
                        videoIdx,
                        video,
                        Video.State.ERROR,
                    )
                val newResult = selectBestVideo(hosterStates)
                hosterIdx = newResult.first
                videoIdx = newResult.second
            }

            coroutineContext.cancelChildren()
            return@withContext null
        }
    } catch (e: EarlyReturnException) {
        e.video
    }
}
```

Uses an `EarlyReturnException` to short-circuit out of `awaitAll` when a
preferred video resolves successfully. This is a clever coroutine
pattern — `coroutineContext.cancelChildren()` cancels sibling fetches,
and the exception is caught at the bottom to return the result.

> ANI-KUTA: This `EarlyReturnException` pattern is unusual but
> effective. A cleaner approach might be a `select` on the deferreds,
> but this works.

## 8. How headers are passed

Each `Video` can have its own `Headers` (OkHttp `Headers` object). If
absent, the source's default `headers` are used.

The VM's `setHttpOptions` flattens these into a single CSV string for
mpv's `http-header-fields` option:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1076-1090
private fun setHttpOptions(video: Video) {
    if (!stateData.value.isEpisodeOnline) return
    val source = stateData.value.currentSource as? AnimeHttpSource
        ?: return

    val headers = (video.headers ?: source.headers)
        .toMultimap()
        .mapValues { it.value.firstOrNull() ?: "" }

    val httpHeaderString = headers.map {
        it.key + ": " + it.value.replace(",", "\\,")
    }.joinToString(",")

    mpv.setOptionString("http-header-fields", httpHeaderString)
}
```

Format: `"Key1: Value1,Key2: Value2,Key3: Value\, with\, comma"`.
- Commas in values are escaped as `\,`.
- Multiple values for the same key are flattened to the first one.
- Set via `setOptionString` (not `setPropertyString`) because it's a
  startup option — must be set before `loadfile`.

The source's `headersBuilder()` provides the default `User-Agent`:

```kotlin
// source-api/.../online/AnimeHttpSource.kt:96-100
protected open fun headersBuilder() = Headers.Builder().apply {
    add("User-Agent", network.defaultUserAgentProvider())
}
```

Extensions can override `headersBuilder()` to add their own headers
(refer, origin, cookies, etc.).

## 9. URL schemes + proxy URLs

The `parseVideoUrl` helper in the VM handles URL scheme dispatch:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1071-1074
private fun parseVideoUrl(videoUrl: String?): String? {
    return videoUrl?.toUri()?.resolveUri(context)
        ?: videoUrl
}
```

And `resolveUri` in `PlayerUtils.kt`:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerUtils.kt:43-54
internal fun Uri.resolveUri(context: Context): String? {
    val filepath = when (scheme) {
        "file" -> path
        "content" -> openContentFd(context)
        "data" -> "data://$schemeSpecificPart"
        in Utils.PROTOCOLS -> toString()
        else -> null
    }

    if (filepath == null) logcat(LogPriority.ERROR) { "unknown scheme: $scheme" }
    return filepath
}
```

Four cases:
1. **`file://`** → use the path directly. mpv handles `file://` natively.
2. **`content://`** (SAF) → convert to a file descriptor via
   `openContentFd`. mpv can't read `content://` URIs directly.
3. **`data://`** → wrap as `data://` (mpv's data URI handler).
4. **Anything in `Utils.PROTOCOLS`** (http, https, ftp, etc.) → use the
   URI's `toString()`.

### `openContentFd` — the SAF bridge

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerUtils.kt:35-41
internal fun Uri.openContentFd(context: Context): String? {
    return context.contentResolver.openFileDescriptor(this, "r")?.detachFd()?.let {
        Utils.findRealPath(it)?.also { _ ->
            ParcelFileDescriptor.adoptFd(it).close()
        } ?: "fd://$it"
    }
}
```

For SAF `content://` URIs, Animiru:
1. Opens a `ParcelFileDescriptor` in read mode.
2. Detaches the FD (takes ownership of the file descriptor integer).
3. Tries `Utils.findRealPath(fd)` — mpv-android-lib's helper that
   reads `/proc/self/fd/<fd>` to find the real path. If the FD points
   at a real file, this returns the path.
4. If that fails (e.g. for a pipe), returns `"fd://<fd>"` — mpv's
   file-descriptor URI scheme.
5. Adopts + closes the FD to avoid leaking it.

This is how local-source videos (which use SAF `content://` URIs) are
played through mpv.

### External subtitle / audio URLs

External tracks (added via the sheet's "Add external" button) go
through the same `openContentFd` conversion:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1478-1489
fun addSubtitle(uri: Uri) {
    val url = uri.toString()
    val isContentUri = url.startsWith("content://")
    val path = (if (isContentUri) uri.openContentFd(context) else url)
        ?: return
    val name = if (isContentURI) uri.getFileName(context) else null
    if (name == null) {
        mpvCommand("sub-add", path, "cached")
    } else {
        mpvCommand("sub-add", path, "cached", name)
    }
}
```

The `"cached"` flag tells mpv to cache the subtitle file contents in
memory (so the FD can be closed immediately). Without it, mpv would
keep the FD open for the duration of playback.

## 10. The `mpvArgs` per-video options

Each `Video` can carry `mpvArgs: List<Pair<String, String>>`. These are
passed as the 5th argument to mpv's `loadfile` command:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1053-1068
// We handle selecting these in the viewmodel
val mpvOpts = listOf(
    Pair("sid", "no"),
    Pair("aid", "no"),
)
val videoOptions = (video.mpvArgs + mpvOpts).joinToString(",") { (option, value) ->
    "$option=\"$value\""
}

mpvCommand(
    "loadfile",
    parseVideoUrl(video.videoUrl)!!,
    "replace",
    "0",
    videoOptions,
)
```

Example output:
```
loadfile "https://example.com/video.mp4" replace 0 "http-header-fields=\"Referer: https://example.com\"",sid="no",aid="no"
```

Extensions use `mpvArgs` to pass per-video options that aren't covered
by the `Video` model's typed fields (e.g. `--http-header-fields`,
`--user-agent`, `--demuxer-lavf-o=...` for HLS-specific options).

### `MPV_ARGS_TAG` — the embedded-args workaround

For downloaded videos, the `mpvArgs` are stored in the file's metadata
under the `ANIYOMI_MPV_ARGS` tag. When mpv loads the file, the VM
reads the metadata and applies the args:

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt:1230-1254
private fun setMpvOptions() {
    val video = stateData.value.currentVideo ?: return

    // Only check for `MPV_ARGS_TAG` on downloaded videos
    if (listOf("file", "content", "data").none { video.videoUrl.startsWith(it) }) {
        return
    }

    try {
        val metadata = mpv.getPropertyNode("metadata")?.asMap()
            ?: return

        val opts = metadata[Video.MPV_ARGS_TAG]
            ?.asString()
            ?.split(";")
            ?.map { it.split("=", limit = 2) }
            ?: return

        opts.forEach { (option, value) ->
            setPropertyString(option, value)
        }
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e) { "Failed to read video metadata" }
    }
}
```

Format in metadata: `"key1=value1;key2=value2;..."` — semicolon-separated.

This is only for local files (`file://`, `content://`, `data://`).
Streaming URLs can't carry metadata, so they use `video.mpvArgs`
directly.

## 11. The resolver in summary

```
Extension (AnimeHttpSource subclass)
    │
    ├─ ext-lib <1.6: implements videoListParse(response): List<Video>
    │                  └─ returns flat list with videoUrl="null" if lazy
    │
    └─ ext-lib 16+: implements hosterListParse(response): List<Hoster]
                       AND videoListParse(response, hoster): List<Video]
                       AND optionally resolveVideo(video): Video?
                       │
                       ├─ Eager hoster: videoList is pre-populated
                       └─ Lazy hoster: videoList is null, fetched on demand

EpisodeLoader
    │
    ├─ getHosters(episode, anime, source) → List<Hoster>  (one-shot)
    └─ loadHosterVideos(source, hoster, force) → HosterState  (per-hoster)

HosterLoader
    │
    ├─ selectBestVideo(hosterState) → (hosterIdx, videoIdx)  (sync)
    ├─ getBestVideo(source, hosterList) → Video?  (streaming, suspending)
    └─ getResolvedVideo(source, video) → Video?  (calls source.resolveVideo)

PlayerViewModel
    │
    ├─ loadHosters(hosterList, hostIndex, videoIndex)  (orchestrates fetch)
    ├─ loadVideo(video, hosterIdx, videoIdx)  (resolves + setVideo)
    └─ setVideo(video)  → mpv.command("loadfile", ...)
```

## 12. Quirks + warnings

1. **Reflection-based ext-lib detection** — `checkHasHosters` walks the
   class hierarchy looking for method names. This is brittle: if an
   extension renames `getHosterList`, the check fails and falls back to
   the old API. The `// TODO(16): Remove else block when dropping
   support for ext lib <1.6` comment indicates this is a temporary
   bridge.

2. **`videoUrl = "null"` (string)** — old extensions use the literal
   string `"null"` to indicate "URL not yet resolved". This is checked
   in `parseVideoUrls`. It's a stringly-typed null sentinel — easy to
   miss.

3. **`Headers.toMultimap().mapValues { it.value.firstOrNull() ?: "" }`**
   — only the first value of each header is kept. If a video has
   multiple `Set-Cookie` headers, only the first survives. This is a
   lossy conversion.

4. **`http-header-fields` is a startup option** — must be set BEFORE
   `loadfile`. Setting it as a property after `loadfile` doesn't affect
   the current file. Animiru does this correctly.

5. **`fd://` URIs leak FDs if not closed** — the `openContentFd` helper
   carefully adopts + closes the FD when `findRealPath` succeeds. But
   if `findRealPath` fails, it returns `"fd://$it"` and the FD is
   **not** closed — mpv takes ownership. If mpv fails to load the file,
   the FD leaks. This is a minor edge case but worth knowing.

6. **No proxy URL support** — Animiru doesn't have a built-in HTTP
   proxy for video URLs. If a source requires request signing (e.g.
   signed URLs that expire), the extension must handle that in
   `resolveVideo`. There's no Animiru-side proxy server.

7. **`internalData` is opaque** — extensions can stash arbitrary string
   data on `Hoster` / `Video`. Animiru never interprets it; it's
   purely for the extension's own use (e.g. passing tokens between
   `hosterListParse` and `videoListParse`).

8. **`getBestVideo` is unused by the player UI** — it's only used by
   `ExternalIntents.kt` (for "open in external player" feature). The
   player itself uses `loadHosters` + `loadVideo`.
