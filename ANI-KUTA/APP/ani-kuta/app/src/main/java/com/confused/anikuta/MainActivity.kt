package com.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.designsystem.component.AnikutaBottomNavBar
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.appupdate.AppUpdateManager
import com.confused.anikuta.core.designsystem.component.NavIcons
import com.confused.anikuta.core.designsystem.component.NavItem
import com.confused.anikuta.core.designsystem.theme.AnikutaTheme
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animebrowse.AnimeBrowseKey
import com.confused.anikuta.feature.animebrowse.BrowseScreen
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import com.confused.anikuta.feature.animedetails.DetailsScreen
import com.confused.anikuta.feature.animelibrary.AnimeLibraryKeyImpl
import com.confused.anikuta.feature.animelibrary.LibraryEntry
import com.confused.anikuta.feature.animelibrary.LibraryScreen
import com.confused.anikuta.feature.animelibrary.LibrarySelectionMode
import com.confused.anikuta.feature.animelibrary.LocalLibrarySelectionMode
import com.confused.anikuta.feature.animesearch.AnimeSearchKey
import com.confused.anikuta.feature.animesearch.SearchScreen
import com.confused.anikuta.feature.download.DownloadsKey
import com.confused.anikuta.feature.download.DownloadsScreen
import com.confused.anikuta.feature.download.DownloadedFilesKey
import com.confused.anikuta.feature.download.DownloadedFilesScreen
import com.confused.anikuta.feature.download.DownloadSettingsKey
import com.confused.anikuta.feature.download.DownloadSettingsScreen
import com.confused.anikuta.feature.extensionssettings.ExtensionsSettingsKey
import com.confused.anikuta.feature.extensionssettings.ExtensionDetailKey
import com.confused.anikuta.feature.extensionssettings.SourcePreferencesKey
import com.confused.anikuta.feature.extensionssettings.ExtensionsSettingsScreen
import com.confused.anikuta.feature.extensionssettings.ExtensionDetailScreen
import com.confused.anikuta.feature.extensionssettings.AutoLinkSettingsKey
import com.confused.anikuta.feature.extensionssettings.AutoLinkSettingsScreen
import com.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsKey
import com.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsScreen
import com.confused.anikuta.feature.watch.WatchKey
import com.confused.anikuta.feature.watch.WatchScreen
import com.confused.anikuta.download.DownloadOrchestrator
import com.confused.anikuta.download.EnqueueResult
import com.confused.anikuta.settings.AboutScreen
import com.confused.anikuta.settings.AppearanceGeneralScreen
import com.confused.anikuta.settings.DetailsPageSettingsScreen
import com.confused.anikuta.settings.AppearanceScreen
import com.confused.anikuta.settings.SettingsScreen
import com.confused.anikuta.settings.UpdateCategoriesScreen
import com.confused.anikuta.settings.UpdatesSettingsScreen
import com.confused.anikuta.settings.PlayerSettingsScreen
import com.confused.anikuta.settings.NotificationsSettingsScreen
import com.confused.anikuta.settings.NotificationsLibraryScreen
import com.confused.anikuta.settings.ThemeMode
import com.confused.anikuta.settings.ThemePreferences
import com.confused.anikuta.updates.UpdateBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

class MainActivity : androidx.fragment.app.FragmentActivity() {

    // D-222: OAuth redirect flags — observed by AppRoot to auto-navigate to Trackers
    // after a successful AniList login + show a snackbar.
    // D-222-R2: `internal set` (not private) so AppRoot (same module) can clear them.
    @Volatile var anilistLoginSuccess: Boolean = false
        internal set
    @Volatile var anilistLoginError: String? = null
        internal set

    @androidx.compose.material3.ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        // D-220: Handle AniList OAuth redirect (anikuta://anilist-auth#access_token=...).
        // The token is in the URL fragment (not query). Parse + pass to AniListTracker.
        handleAniListOAuthRedirect(intent)
        setContent {
            val prefs = koinInject<ThemePreferences>()
            val themeMode = prefs.themeMode.value
            val amoled = prefs.amoled.value
            // Accent seed: resolves CUSTOM → stored custom color, else preset seed.
            val accentSeed = prefs.resolveAccentSeed()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AnikutaTheme(darkTheme = isDark, amoled = amoled, accentSeed = accentSeed) {
                AppRoot()
            }
        }
    }

    // D-220: Handle AniList OAuth redirect when the activity is already running.
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAniListOAuthRedirect(intent)
    }

    /**
     * D-220: Parse the AniList OAuth2 implicit grant redirect.
     *
     * AniList redirects to `anikuta://anilist-auth#access_token=...&expires_in=...`
     * The token is in the URL **fragment** (after #), NOT the query.
     *
     * This method extracts the token + calls AniListTracker.handleLoginCallback(token)
     * in a background coroutine.
     */
    private fun handleAniListOAuthRedirect(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "anikuta" || data.host != "anilist-auth") return

        // The access_token is in the URL fragment (implicit grant).
        val fragment = data.encodedFragment ?: ""
        val token = com.confused.anikuta.core.trackeranilist.AniListOAuth
            .parseAccessToken(fragment)

        if (token.isNullOrBlank()) {
            com.confused.anikuta.core.common.Logger.w("MainActivity") {
                "AniList OAuth redirect: no access_token in fragment"
            }
            return
        }

        com.confused.anikuta.core.common.Logger.i("MainActivity") {
            "AniList OAuth redirect received — exchanging token..."
        }

        // Launch a coroutine to call the tracker (suspend function).
        kotlinx.coroutines.MainScope().launch {
            try {
                val tracker = org.koin.core.context.GlobalContext.get()
                    .get<com.confused.anikuta.core.trackeranilist.AniListTracker>()
                val success = tracker.handleLoginCallback(token)
                if (success) {
                    com.confused.anikuta.core.common.Logger.i("MainActivity") {
                        "AniList login successful!"
                    }
                    // D-222: auto-navigate to the Trackers page so the user sees
                    // the confirmation. The redirect opens the app fresh (Browse page),
                    // so we need to push the Trackers page onto the backstack.
                    anilistLoginSuccess = true
                } else {
                    com.confused.anikuta.core.common.Logger.e("MainActivity") {
                        "AniList login failed"
                    }
                    anilistLoginError = "Login failed — please try again."
                }
            } catch (e: Exception) {
                com.confused.anikuta.core.common.Logger.e("MainActivity", e) {
                    "AniList OAuth redirect handling failed: ${e.message}"
                }
            }
        }
    }
}

@Serializable
object MoreKey : NavKey

@Serializable
object TrackersKey : NavKey  // D-220: Trackers settings page (AniList link/unlink)

@Serializable
object ProfileKey : NavKey

@Serializable
object SettingsKey : NavKey

@Serializable
object NotificationsKey : NavKey

@Serializable
object NotificationsLibraryKey : NavKey

// D-193 Phase 3: combined Updates & Notifications settings
@Serializable
object UpdatesSettingsKey : NavKey

@Serializable
object UpdateCategoriesKey : NavKey

@Serializable
object AppearanceKey : NavKey

@Serializable
object AppearanceGeneralKey : NavKey

@Serializable
object DetailsPageSettingsKey : NavKey

@Serializable
object EpisodeSettingsKey : NavKey

@Serializable
object PlayerSettingsKey : NavKey

// About & Updates screen — hosts the app-update UI (version, auto-check toggle,
// manual check, downloaded APK list). The UpdateBottomSheet overlay is rendered
// from AppRoot (below) gated on AppUpdateManager.shouldShowUpdateSheet.
@Serializable
object AboutKey : NavKey

/**
 * Root tab keys — these are the 4 tabs that show the bottom nav.
 * Any other key (Details, Settings, Appearance, etc.) is a "sub-screen"
 * that does NOT show the bottom nav.
 */
private val rootTabKeys = setOf(
    AnimeBrowseKey::class,
    AnimeLibraryKeyImpl::class,
    AnimeSearchKey::class,
    MoreKey::class,
)

/**
 * NavKeys on which the `UpdateBottomSheet` overlay is ALLOWED to appear.
 *
 * The sheet is suppressed on screens where it would interrupt critical UX:
 * - `AnimeSearchKey` — search is transient; the sheet would interrupt typing.
 * - `AnimeDetailsKey.AniList` / `AnimeDetailsKey.Extension` — the details page
 *   has its own bottom sheets (resolver, download picker); stacking the update
 *   sheet causes visual + back-gesture conflicts.
 * - `WatchKey` — never pop a sheet over the video player.
 *
 * Everywhere else (root tabs + all settings sub-screens + history + updates +
 * profile) is safe — the sheet's scrim dismiss / X button routes through
 * `AppUpdateManager.dismissUpdateSheet()` which records the 6-hour cooldown.
 */
private val allowedUpdateSheetKeys = setOf(
    AnimeBrowseKey::class,
    AnimeLibraryKeyImpl::class,
    MoreKey::class,
    SettingsKey::class,
    AboutKey::class,
    DownloadsKey::class,
    DownloadedFilesKey::class,
    DownloadSettingsKey::class,
    UpdatesSettingsKey::class,
    UpdateCategoriesKey::class,
    NotificationsKey::class,
    NotificationsLibraryKey::class,
    ExtensionsSettingsKey::class,
    ExtensionRepoSettingsKey::class,
    AutoLinkSettingsKey::class,
    ExtensionDetailKey::class,
    SourcePreferencesKey::class,
    AppearanceKey::class,
    AppearanceGeneralKey::class,
    DetailsPageSettingsKey::class,
    EpisodeSettingsKey::class,
    PlayerSettingsKey::class,
    ProfileKey::class,
    com.confused.anikuta.feature.updates.UpdatesKey::class,
    com.confused.anikuta.feature.animehistory.HistoryKey::class,
)

/**
 * ANI-KUTA navigation root.
 *
 * Fixes (user feedback):
 * - Bottom nav only shows on root tab screens (Browse, Library, Search, More).
 *   Sub-screens (Details, Settings, Appearance) do NOT show the bottom nav.
 * - BackHandler on all screens: device back gesture goes to previous screen,
 *   not exit app.
 * - All screens use MaterialTheme.colorScheme.background for proper theming
 *   in both light and dark mode.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AppRoot() {
    // D-209: captured here so the Cloudflare "Open in WebView" callbacks (non-
    // composable lambdas) can launch CloudflareWebViewActivity.
    val appContext = androidx.compose.ui.platform.LocalContext.current
    val navItems = remember {
        listOf(
            NavItem("browse", "Browse", NavIcons.Browse),
            NavItem("library", "Library", NavIcons.Library),
            NavItem("search", "Search", NavIcons.Search),
            NavItem("more", "More", NavIcons.More),
        )
    }
    var currentTab by remember { mutableStateOf("browse") }

    // D-143: Library selection mode state — shared between LibraryScreen + AppRoot.
    val librarySelectionMode = remember { LibrarySelectionMode() }

    // D.6: download orchestrator + content repository (for the episode download path).
    val orchestrator = koinInject<DownloadOrchestrator>()
    val contentRepository = koinInject<com.confused.anikuta.core.content.ContentRepository>()
    val downloadManager = koinInject<com.confused.anikuta.core.download.DownloadManager>()
    val downloadPreferences = koinInject<com.confused.anikuta.core.download.DownloadPreferences>()
    // D.FIX: DataCacheRepository — needed to load the FULL episode list (not just
    // downloaded episodes) when playing from the downloads page. Without this, the
    // watch screen's episode list only shows downloaded episodes (often just 1).
    val dataCacheRepository = koinInject<com.confused.anikuta.core.datacache.DataCacheRepository>()

    // ── App update manager ──
    // Injected once at the AppRoot level — shared between the startup check
    // (LaunchedEffect below), the page-gated UpdateBottomSheet overlay, and
    // AboutScreen (which koinInject()s its own copy from the same Koin scope).
    val appUpdateManager = koinInject<AppUpdateManager>()

    // D-151-fix: coroutine scope for launching off-main-thread work from synchronous
    // callbacks (e.g. the Downloads→Watch onPlayEpisode lambda — was using runBlocking,
    // an ANR risk). rememberCoroutineScope is tied to the composition — cancelled on dispose.
    val appScope = rememberCoroutineScope()

    // D.CRASH-FIX: First-run setup dialog — prompts for POST_NOTIFICATIONS permission
    // + download folder selection on every launch until both are granted.
    FirstRunSetupDialog(preferences = downloadPreferences)

    val backstack = remember {
        androidx.compose.runtime.mutableStateListOf<NavKey>(AnimeBrowseKey)
    }
    val currentKey = backstack.last()

    // D-222: AniList OAuth redirect — auto-navigate to the Trackers page
    // after a successful login (the redirect opens the app fresh on Browse).
    // Also show a snackbar confirmation.
    val mainActivity = appContext as? MainActivity
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Poll the flags set by handleAniListOAuthRedirect.
        while (true) {
            kotlinx.coroutines.delay(200)
            if (mainActivity?.anilistLoginSuccess == true) {
                mainActivity.anilistLoginSuccess = false
                // Navigate to More → Trackers.
                if (currentKey !is TrackersKey) {
                    if (currentKey !is MoreKey) backstack.add(MoreKey)
                    backstack.add(TrackersKey)
                }
            }
            mainActivity?.anilistLoginError?.let { error ->
                mainActivity.anilistLoginError = null
                // TODO: show a snackbar with the error.
                com.confused.anikuta.core.common.Logger.w("AppRoot") { "AniList login error: $error" }
            }
        }
    }

    // ── App update startup check ──
    // Runs once per composition. Mirrors the old project's AnikutaRoot.kt:140-186
    // pattern (without the AppController layer):
    //   1. Cleanup any downloaded APKs whose version <= installed (frees storage
    //      after a successful install — the just-installed APK is now stale).
    //   2. Clear the in-memory update state (latestUpdate + downloadProgress +
    //      shouldShowUpdateSheet) so we start fresh on every app open.
    //   3. If auto-check is enabled → run checkForUpdate(). On success, the
    //      manager itself flips shouldShowUpdateSheet to true (unless the user
    //      dismissed this exact version < 6h ago). AppRoot observes that flow
    //      (below) and renders UpdateBottomSheet when it's true AND the current
    //      screen is in `allowedUpdateSheetKeys`.
    // The post-install success popup flow (old project's
    // `appController.showPostInstallPopup`) is intentionally NOT ported in this
    // pass — it requires a separate PostInstallSuccessSheet composable. Tracked
    // as a follow-up.
    LaunchedEffect(Unit) {
        try {
            appUpdateManager.cleanupOldDownloads()
            appUpdateManager.clearUpdateState()
            if (appUpdateManager.shouldCheckForUpdate()) {
                appUpdateManager.checkForUpdate()
            }
        } catch (e: Exception) {
            Logger.w("Anikuta:AppRoot", e) { "startup update check failed" }
        }
    }

    // D-193 Phase 7: Handle notification tap deep-link — if the app was opened
    // from a notification, navigate to the details page for the tapped anime.
    val context = androidx.compose.ui.platform.LocalContext.current
    val notifMainId = remember {
        val intent = (context as? android.app.Activity)?.intent
        intent?.getStringExtra("notification_main_id")
    }
    LaunchedEffect(notifMainId) {
        if (!notifMainId.isNullOrBlank() && backstack.size == 1) {
            // Look up the content to determine whether it has an AniList ID or is extension-only.
            val content = contentRepository.getMainEntryByMainId(notifMainId)
            // D-198: getAniListDetail → getContentDetails.
            val details = content?.let { contentRepository.getContentDetails(it.mainId) }
            val anilistId = details?.anilistId
            if (anilistId != null) {
                backstack.add(AnimeDetailsKey.AniList(anilistId))
            } else if (content != null) {
                val sid = content.sourceId
                val url = content.animeUrl
                if (sid != null && url != null) {
                    backstack.add(
                        AnimeDetailsKey.Extension(
                            sourceId = sid,
                            animeUrl = url,
                            title = content.title,
                        ),
                    )
                }
            }
            // Clear the extra so we don't re-navigate on recomposition.
            (context as? android.app.Activity)?.intent?.removeExtra("notification_main_id")
        }
    }

    val pop: () -> Unit = {
        if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
    }

    // BackHandler: handle device back gesture properly
    BackHandler(enabled = backstack.size > 1) {
        pop()
    }

    // D-163 (DB-1): hoisted debug-context state. Screens write via
    // LocalDebugContextUpdater; the debug bubble reads via LocalDebugContext.
    // The provider wraps BOTH the nav content AND the bubble (DebugBubbleHost)
    // so the bubble — a sibling of the nav content in this Box — is inside the
    // provider's subtree and can read the context (D-162 C1 fix).
    var debugContext by remember { androidx.compose.runtime.mutableStateOf<com.confused.anikuta.core.debugapi.DebugContext?>(null) }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLibrarySelectionMode provides librarySelectionMode,
        com.confused.anikuta.core.debugapi.LocalDebugContext provides debugContext,
        com.confused.anikuta.core.debugapi.LocalDebugContextUpdater provides { ctx -> debugContext = ctx },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
        when (currentKey) {
            is AnimeBrowseKey -> BrowseScreen(
                onNavigate = { navKey -> backstack.add(navKey) }
            )
            is AnimeDetailsKey -> {
                // Handle both AniList and Extension variants of the sealed key.
                when (currentKey) {
                    is AnimeDetailsKey.AniList -> DetailsScreen(
                        detailsKey = currentKey,
                        onBack = pop,
                        onNavigateToWatch = { mainId, videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders, resolvedVideosKey, sourceId, subTracks, audioTracks, epMeta ->
                            backstack.add(WatchKey(videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders, resolvedVideosKey, sourceId, mainId, subTracks, audioTracks, epMeta))
                        },
                        onDownloadEpisode = { episode ->
                            handleDownloadEpisode(
                                detailsKey = currentKey,
                                episode = episode,
                                orchestrator = orchestrator,
                                contentRepository = contentRepository,
                            )
                        },
                        onDownloadSpecificVideo = { episode, video, serverName, sourceIdStr, audioLabel ->
                            handleDownloadSpecificVideo(
                                detailsKey = currentKey,
                                episode = episode,
                                video = video,
                                serverName = serverName,
                                sourceIdStr = sourceIdStr,
                                audioLabel = audioLabel,
                                orchestrator = orchestrator,
                                contentRepository = contentRepository,
                            )
                        },
                        // D-209: Cloudflare manual solver.
                        onOpenCloudflareWebView = { url, sourceName ->
                            appContext.startActivity(
                                com.confused.anikuta.webview.CloudflareWebViewActivity.newIntent(
                                    context = appContext, url = url, sourceName = sourceName,
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                    is AnimeDetailsKey.Extension -> DetailsScreen(
                        detailsKey = currentKey,
                        onBack = pop,
                        onNavigateToWatch = { mainId, videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders, resolvedVideosKey, sourceId, subTracks, audioTracks, epMeta ->
                            backstack.add(WatchKey(videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders, resolvedVideosKey, sourceId, mainId, subTracks, audioTracks, epMeta))
                        },
                        onDownloadEpisode = { episode ->
                            handleDownloadEpisode(
                                detailsKey = currentKey,
                                episode = episode,
                                orchestrator = orchestrator,
                                contentRepository = contentRepository,
                            )
                        },
                        onDownloadSpecificVideo = { episode, video, serverName, sourceIdStr, audioLabel ->
                            handleDownloadSpecificVideo(
                                detailsKey = currentKey,
                                episode = episode,
                                video = video,
                                serverName = serverName,
                                sourceIdStr = sourceIdStr,
                                audioLabel = audioLabel,
                                orchestrator = orchestrator,
                                contentRepository = contentRepository,
                            )
                        },
                        // D-209: Cloudflare manual solver.
                        onOpenCloudflareWebView = { url, sourceName ->
                            appContext.startActivity(
                                com.confused.anikuta.webview.CloudflareWebViewActivity.newIntent(
                                    context = appContext, url = url, sourceName = sourceName,
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }
            is AnimeLibraryKeyImpl -> LibraryScreen(
                onNavigateToDetails = { entry ->
                    // D-140: Navigate based on the entry type.
                    // If it has an anilistId → open via AniList.
                    // If it only has an extension source → open via Extension.
                    if (entry.hasAniListId) {
                        backstack.add(AnimeDetailsKey.AniList(entry.anilistId!!))
                    } else if (entry.hasExtensionSource) {
                        backstack.add(
                            AnimeDetailsKey.Extension(
                                entry.sourceId!!,
                                entry.animeUrl!!,
                                entry.title,
                                entry.coverUrl,
                            )
                        )
                    } else {
                        // Fallback: no valid navigation target. Log + ignore.
                        Logger.w("MainActivity") { "Library entry has no valid navigation target: ${entry.mainId}" }
                    }
                }
            )
            is AnimeSearchKey -> SearchScreen(
                onNavigateToDetails = { animeId ->
                    backstack.add(AnimeDetailsKey.AniList(animeId))
                },
                onNavigateToExtensionAnime = { sourceId, animeUrl, title, thumbnailUrl ->
                    backstack.add(AnimeDetailsKey.Extension(sourceId, animeUrl, title, thumbnailUrl))
                },
                // D-209: Cloudflare manual solver — launched from the Search error card.
                onOpenCloudflareWebView = { url, sourceName ->
                    appContext.startActivity(
                        com.confused.anikuta.webview.CloudflareWebViewActivity.newIntent(
                            context = appContext, url = url, sourceName = sourceName,
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            is MoreKey -> MoreScreen(
                onOpenSettings = { backstack.add(SettingsKey) },
                onOpenDownloads = { backstack.add(DownloadsKey) },
                onOpenHistory = { backstack.add(com.confused.anikuta.feature.animehistory.HistoryKey) },
                onOpenUpdates = { backstack.add(com.confused.anikuta.feature.updates.UpdatesKey) },
                onOpenProfile = { backstack.add(ProfileKey) },
                onOpenAbout = { backstack.add(AboutKey) },
                onOpenTrackers = { backstack.add(TrackersKey) },
            )
            is DownloadsKey -> DownloadsScreen(
                onBack = pop,
                onOpenSettings = { backstack.add(DownloadSettingsKey) },
                onOpenDownloaded = { backstack.add(DownloadedFilesKey) },
            )
            is DownloadedFilesKey -> DownloadedFilesScreen(
                onBack = pop,
                onPlayEpisode = { mainId, episodeKey ->
                    // D-151-fix: moved off the main thread. The old code used
                    // `runBlocking { scanSubtitleFilesOnDisk(...) }` (ANR risk) AND
                    // called multiple synchronous DB queries on the main thread. Now
                    // the entire WatchKey-building logic runs on Dispatchers.IO, and
                    // backstack.add fires on the main thread when it completes.
                    appScope.launch {
                        val watchKey = withContext(Dispatchers.IO) {
                            buildWatchKeyForDownloadedEpisode(
                                mainId = mainId,
                                episodeKey = episodeKey,
                                downloadManager = downloadManager,
                                contentRepository = contentRepository,
                                dataCacheRepository = dataCacheRepository,
                            )
                        }
                        if (watchKey != null) {
                            backstack.add(watchKey)
                        } else {
                            Logger.e("Anikuta:MainActivity") {
                                "Downloads→Watch: no local URI found for mainId=$mainId, episodeKey=$episodeKey"
                            }
                        }
                    }
                },
                onNavigateToDetails = { mainId ->
                    // D.FIX: Navigate to the details page for this anime.
                    val content = contentRepository.getMainEntryByMainId(mainId)
                    if (content != null) {
                        // D-198: getAniListDetail + getExtensionDetail → getContentDetails.
                        val details = contentRepository.getContentDetails(mainId)
                        val anilistId = details?.anilistId
                        if (anilistId != null) {
                            backstack.add(AnimeDetailsKey.AniList(anilistId))
                        } else {
                            // Extension-only entry.
                            if (details != null) {
                                backstack.add(AnimeDetailsKey.Extension(
                                    sourceId = details.sourceId ?: 0L,
                                    animeUrl = details.animeUrl ?: content.animeUrl ?: "",
                                    title = content.title,
                                    thumbnailUrl = details.extThumbnailUrl,
                                ))
                            }
                        }
                    }
                },
            )
            is DownloadSettingsKey -> DownloadSettingsScreen(
                onBack = pop,
            )
            is SettingsKey -> SettingsScreen(
                onOpenAppearance = { backstack.add(AppearanceKey) },
                onOpenExtensions = { backstack.add(ExtensionsSettingsKey) },
                onOpenAutoLink = { backstack.add(AutoLinkSettingsKey) },
                onOpenNotifications = { backstack.add(UpdatesSettingsKey) },
                onOpenPlayerSettings = { backstack.add(PlayerSettingsKey) },
                onOpenAbout = { backstack.add(AboutKey) },
                onBack = pop,
            )
            // D-220: Trackers page.
            is TrackersKey -> com.confused.anikuta.settings.TrackersScreen(
                onBack = pop,
            )
            // ── About & Updates ──
            // The update sheet itself renders from AppRoot (below) — gated on
            // AppUpdateManager.shouldShowUpdateSheet (which checkForUpdate flips).
            // The manual "Check for updates" row in AboutScreen calls
            // updateManager.checkForUpdate() directly — no callback needed here.
            // We pass the hoisted appUpdateManager (already koinInject'd at the
            // top of AppRoot) rather than koinInject'ing again here, matching
            // the pattern used for orchestrator/contentRepository/downloadManager.
            is AboutKey -> AboutScreen(
                updateManager = appUpdateManager,
                onBack = pop,
            )
            // D-193 Phase 3: combined Updates & Notifications settings
            is UpdatesSettingsKey -> UpdatesSettingsScreen(
                onOpenNotifications = { backstack.add(NotificationsKey) },
                onOpenCategories = { backstack.add(UpdateCategoriesKey) },
            )
            is UpdateCategoriesKey -> UpdateCategoriesScreen(onBack = pop)
            is NotificationsKey -> NotificationsSettingsScreen(
                onBack = pop,
                onOpenLibrary = { backstack.add(NotificationsLibraryKey) },
            )
            is NotificationsLibraryKey -> NotificationsLibraryScreen(
                onBack = pop,
            )
            is ExtensionsSettingsKey -> ExtensionsSettingsScreen(
                onBack = pop,
                onOpenRepoSettings = { backstack.add(ExtensionRepoSettingsKey) },
                onOpenExtensionDetail = { backstack.add(ExtensionDetailKey(it)) },
            )
            is ExtensionRepoSettingsKey -> ExtensionRepoSettingsScreen(
                onBack = pop,
            )
            is AutoLinkSettingsKey -> AutoLinkSettingsScreen(
                onBack = pop,
            )
            is ExtensionDetailKey -> ExtensionDetailScreen(
                pkgName = currentKey.pkgName,
                onBack = pop,
                onOpenSourcePreferences = { sourceId -> backstack.add(SourcePreferencesKey(sourceId)) },
                // D-209: "Open in WebView" button on the extension detail page —
                // opens the source's baseUrl in a WebView so the user can solve
                // Cloudflare challenges manually. Cookies are saved automatically
                // via the shared CookieManager.
                onOpenInWebView = { url, sourceName ->
                    appContext.startActivity(
                        com.confused.anikuta.webview.CloudflareWebViewActivity.newIntent(
                            context = appContext, url = url, sourceName = sourceName,
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            is SourcePreferencesKey -> com.confused.anikuta.feature.extensionssettings.SourcePreferencesScreen(
                sourceId = currentKey.sourceId,
                onBack = pop,
            )
            is AppearanceKey -> AppearanceScreen(
                onOpenGeneral = { backstack.add(AppearanceGeneralKey) },
                onOpenDetailsPage = { backstack.add(DetailsPageSettingsKey) },
                onOpenEpisodeSettings = { backstack.add(EpisodeSettingsKey) },
                onBack = pop,
            )
            is AppearanceGeneralKey -> AppearanceGeneralScreen(
                onBack = pop,
            )
            is DetailsPageSettingsKey -> DetailsPageSettingsScreen(onBack = pop)
            is EpisodeSettingsKey -> PlaceholderScreen(
                title = "Episode settings",
                description = "Episode display settings will be added in a future phase.",
                onBack = pop,
            )
            is PlayerSettingsKey -> PlayerSettingsScreen(
                onBack = pop,
            )
            is ProfileKey -> com.confused.anikuta.profile.ProfileScreen(
                onBack = pop,
                onNavigateToAnime = { anilistId ->
                    backstack.add(AnimeDetailsKey.AniList(anilistId))
                },
            )
            is WatchKey -> WatchScreen(
                watchKey = currentKey,
                onBack = pop,
            )
            is com.confused.anikuta.feature.updates.UpdatesKey -> {
                com.confused.anikuta.feature.updates.UpdatesScreen(
                    onBack = pop,
                    onNavigateToDetails = { mainId ->
                        val content = contentRepository.getMainEntryByMainId(mainId)
                        if (content != null) {
                            // D-198: getAniListDetail + getExtensionDetail → getContentDetails.
                            val details = contentRepository.getContentDetails(mainId)
                            val anilistId = details?.anilistId
                            if (anilistId != null) {
                                backstack.add(AnimeDetailsKey.AniList(anilistId))
                            } else {
                                if (details != null) {
                                    backstack.add(AnimeDetailsKey.Extension(
                                        details.sourceId ?: 0L,
                                        details.animeUrl ?: content.animeUrl ?: "",
                                        content.title,
                                        details.extThumbnailUrl,
                                    ))
                                }
                            }
                        }
                    },
                )
            }
            is com.confused.anikuta.feature.animehistory.HistoryKey -> {
                com.confused.anikuta.feature.animehistory.HistoryScreen(
                    onBack = pop,
                    onNavigateToDetails = { mainId ->
                        // Navigate to the anime's details page (AniList or Extension based on content).
                        val content = contentRepository.getMainEntryByMainId(mainId)
                        if (content != null) {
                            // D-198: getAniListDetail + getExtensionDetail → getContentDetails.
                            val details = contentRepository.getContentDetails(mainId)
                            val anilistId = details?.anilistId
                            if (anilistId != null) {
                                backstack.add(AnimeDetailsKey.AniList(anilistId))
                            } else {
                                // Extension-only content — use the source ID + URL.
                                if (details != null) {
                                    backstack.add(AnimeDetailsKey.Extension(
                                        details.sourceId ?: 0L,
                                        details.animeUrl ?: content.animeUrl ?: "",
                                        content.title,
                                        details.extThumbnailUrl,
                                    ))
                                }
                            }
                        }
                    },
                )
            }
            // Safety net — should never be reached (all NavKey subtypes are handled above).
            // Non-silent: logs a warning so a missing route is visible in logcat instead of
            // rendering a blank screen with no clue why. Filter: tag:MainActivity.
            else -> {
                com.confused.anikuta.core.common.Logger.w("MainActivity") {
                    "Unrecognized NavKey in dispatch — no screen rendered: ${currentKey::class.simpleName}. Add a branch for this NavKey type."
                }
            }
        }

        // Bottom navigation — ONLY show on root tab screens (not sub-screens)
        val showBottomNav = currentKey::class in rootTabKeys
        if (showBottomNav) {
            // D-143: If library is in selection mode, replace the nav pills
            // with the selection action bar (Cancel / Category / Delete).
            val selectionContent: (@Composable () -> Unit)? = if (
                librarySelectionMode.isSelectionMode && currentKey is AnimeLibraryKeyImpl
            ) {
                {
                    SelectionActionBar(
                        selectedCount = librarySelectionMode.selectedCount,
                        onCancel = { librarySelectionMode.onCancel?.invoke() },
                        onCategory = { librarySelectionMode.onCategory?.invoke() },
                        onDelete = { librarySelectionMode.onDelete?.invoke() },
                    )
                }
            } else null

            AnikutaBottomNavBar(
                items = navItems,
                currentRoute = currentTab,
                onSelect = { route ->
                    currentTab = route
                    backstack.clear()
                    when (route) {
                        "browse" -> backstack.add(AnimeBrowseKey)
                        "library" -> backstack.add(AnimeLibraryKeyImpl)
                        "search" -> backstack.add(AnimeSearchKey)
                        "more" -> backstack.add(MoreKey)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                selectionModeContent = selectionContent,
            )
        }

        // D-163 (DB-1): the debug bubble — renders on top of every screen.
        // DebugBubbleHost is a no-op in release builds (release source set).
        // In debug builds it renders the draggable squircle bubble.
        DebugBubbleHost()

        // ── App update bottom sheet (page-gated) ──
        // Rendered as an overlay from AppRoot (NOT pushed onto the backstack) so
        // it floats above every screen in `allowedUpdateSheetKeys`. We collect
        // `shouldShowUpdateSheet` with lifecycle awareness — the StateFlow is
        // flipped to true by AppUpdateManager.checkForUpdate() (startup OR
        // manual check from AboutScreen) when an update is found AND not in the
        // 6-hour dismiss cooldown.
        //
        // Page-gating: the sheet is suppressed on search / details / watch
        // screens (see `allowedUpdateSheetKeys`) — those screens have their own
        // bottom sheets (resolver / picker) or are critical UX (player) that
        // the update sheet would interrupt.
        //
        // D-199: the UpdateBottomSheet handles its own dismiss internally:
        // - Swipe-down / tap-outside → hideUpdateSheet() (NO cooldown — re-shows)
        // - X button → dismissUpdateSheet() (WITH 6h cooldown — snoozed)
        // The onDismiss callback here is a no-op — the sheet manages the StateFlow.
        val canShowUpdateSheet = currentKey::class in allowedUpdateSheetKeys
        val showUpdateSheet by appUpdateManager.shouldShowUpdateSheet.collectAsStateWithLifecycle()
        if (canShowUpdateSheet && showUpdateSheet) {
            UpdateBottomSheet(
                updateManager = appUpdateManager,
                onDismiss = { },
            )
        }
        } // end CompositionLocalProvider Box
    } // end CompositionLocalProvider
}

/**
 * D.6: Triggers a download for [episode] from the anime represented by [detailsKey].
 *
 * Resolves the content identity (mainId, title, cover) via [contentRepository],
 * then delegates to [orchestrator.enqueueDownload]. The orchestrator runs the
 * auto-download engine (or shows the picker sheet on ASK fallback).
 *
 * Runs in a background coroutine scope — the user sees the spinner on the
 * episode row immediately (the DetailsViewModel exposes the Resolving state).
 */
private fun handleDownloadEpisode(
    detailsKey: AnimeDetailsKey,
    episode: eu.kanade.tachiyomi.animesource.model.SEpisode,
    orchestrator: DownloadOrchestrator,
    contentRepository: com.confused.anikuta.core.content.ContentRepository,
) {
    val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    scope.launch {
        try {
            // 1. Resolve the content identity.
            val mainId: String? = when (detailsKey) {
                is AnimeDetailsKey.AniList ->
                    contentRepository.getMainEntryByAniListId(detailsKey.animeId)?.mainId
                is AnimeDetailsKey.Extension ->
                    contentRepository.getMainEntryByExtension(detailsKey.sourceId, detailsKey.animeUrl)?.mainId
            }
            if (mainId == null) {
                com.confused.anikuta.core.common.Logger.w("MainActivity") {
                    "handleDownloadEpisode — no mainId for detailsKey=$detailsKey"
                }
                return@launch
            }

            // 2. Build the content + episode identity (cover is best-effort — null is fine).
            val content = contentRepository.getMainEntryByMainId(mainId)
            if (content == null) {
                com.confused.anikuta.core.common.Logger.w("MainActivity") {
                    "handleDownloadEpisode — no content for mainId=$mainId"
                }
                return@launch
            }
            // D-198: getAniListDetail + getExtensionDetail → getContentDetails.
            val details = contentRepository.getContentDetails(mainId)
            val coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl
            // D.FIX: Use details to fill in FK fields that are null in ContentRecord.
            val contentInfo = com.confused.anikuta.core.download.DownloadContentInfo(
                mainId = content.mainId,
                contentId = content.contentId,
                title = content.title,
                coverUrl = coverUrl,
                coverColor = null,
                contentFormat = content.contentFormat,
                contentType = content.contentType,
                // D-198: main_entry.description dropped — use content_details axes as fallback.
                description = details?.dataSynopsis ?: details?.extDescription,
                dataSourceId = content.dataSourceId,
                systemId = content.systemId,
                extensionRepoId = content.extensionRepoId,
                extensionId = content.extensionId ?: details?.extensionIdLong,
                sourceId = content.sourceId ?: details?.sourceId,
                animeUrl = content.animeUrl ?: details?.animeUrl,
                displaySource = content.displaySource,
                anilistId = details?.anilistId,
            )
            val episodeInfo = com.confused.anikuta.core.download.DownloadEpisodeInfo(
                episodeKey = episode.url,
                episodeNumber = episode.episode_number,
                name = episode.name,
            )

            // 3. Look up the extension source.
            val sourceId = content.sourceId
            val source = sourceId?.let {
                org.koin.core.context.GlobalContext.get()
                    .get<com.confused.anikuta.data.extension.manager.ExtensionManager>()
                    .getSource(it) as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
            }

            // 4. Enqueue.
            val result = orchestrator.enqueueDownload(
                source = source,
                episode = episode,
                content = contentInfo,
                episodeInfo = episodeInfo,
            )
            when (result) {
                is EnqueueResult.Success -> {
                    com.confused.anikuta.core.common.Logger.i("MainActivity") {
                        "Download enqueued: taskId=${result.taskId}"
                    }
                }
                is EnqueueResult.ShowPicker -> {
                    com.confused.anikuta.core.common.Logger.i("MainActivity") {
                        "Download picker needed (ASK fallback) — ${result.servers.size} servers"
                    }
                    // TODO: show the DownloadVideoPickerSheet (Phase D.6 follow-up).
                    // For now, log only — the auto-download engine handles 99% of cases.
                }
                is EnqueueResult.NoSources -> {
                    com.confused.anikuta.core.common.Logger.w("MainActivity") {
                        "Download failed — no extension source linked"
                    }
                }
                is EnqueueResult.Error -> {
                    com.confused.anikuta.core.common.Logger.e("MainActivity") {
                        "Download failed: ${result.message}"
                    }
                }
            }
        } catch (e: Exception) {
            com.confused.anikuta.core.common.Logger.e("MainActivity", e) {
                "handleDownloadEpisode — exception"
            }
        }
    }
}

/**
 * Handles downloading a SPECIFIC video that the user picked from the resolver sheet.
 *
 * D.FIX: When the user clicks the download button on an episode, the resolver sheet
 * appears (same as play). The user picks a video → this function is called → it
 * builds a DownloadContentInfo + DownloadEpisodeInfo + calls orchestrator.enqueueSpecific.
 *
 * @param episode The SEpisode to download.
 * @param video The ResolverVideo the user picked from the resolver sheet.
 * @param serverName The server name (from the resolver — the actual server
 *   the user picked, e.g. "Vidstream" / "HD-1", NOT the extension name).
 * @param sourceIdStr The source ID as a string.
 * @param audioLabel The audio version label (from the resolver — e.g. "SUB",
 *   "DUB", "HSUB". Empty string if the resolver didn't label it).
 */
private fun handleDownloadSpecificVideo(
    detailsKey: AnimeDetailsKey,
    episode: eu.kanade.tachiyomi.animesource.model.SEpisode,
    video: com.confused.anikuta.core.videoresolver.ResolvedVideo,
    serverName: String,
    sourceIdStr: String,
    audioLabel: String,
    orchestrator: DownloadOrchestrator,
    contentRepository: com.confused.anikuta.core.content.ContentRepository,
) {
    com.confused.anikuta.core.common.Logger.i("MainActivity") {
        "handleDownloadSpecificVideo — START: episode=${episode.url}, videoUrl=${video.url}, serverName=$serverName, sourceIdStr=$sourceIdStr"
    }
    val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    scope.launch {
        try {
            val mainId: String? = when (detailsKey) {
                is AnimeDetailsKey.AniList ->
                    contentRepository.getMainEntryByAniListId(detailsKey.animeId)?.mainId
                is AnimeDetailsKey.Extension ->
                    contentRepository.getMainEntryByExtension(detailsKey.sourceId, detailsKey.animeUrl)?.mainId
            }
            if (mainId == null) {
                com.confused.anikuta.core.common.Logger.w("MainActivity") {
                    "handleDownloadSpecificVideo — no mainId"
                }
                return@launch
            }

            val content = contentRepository.getMainEntryByMainId(mainId) ?: run {
                com.confused.anikuta.core.common.Logger.w("MainActivity") {
                    "handleDownloadSpecificVideo — no content for mainId=$mainId"
                }
                return@launch
            }
            // D-198: getAniListDetail + getExtensionDetail → getContentDetails.
            val details = contentRepository.getContentDetails(mainId)
            val coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl
            // D.FIX: Use details to fill in FK fields that are null in ContentRecord.
            // For extension-only content, ContentRecord.sourceId/animeUrl/extensionId can
            // be null — content_details always has them on the ext_* axis.
            // D-210 FIX: sourceIdStr (from the resolver sheet's LinkedSource) is
            // AUTHORITATIVE — for AniList-saved entries, content.sourceId and
            // details.sourceId are both null (the AniList path never writes
            // main_entry.source_id, and loadLinkedSource only sets the in-memory
            // _linkedSource — doesn't persist ext_* axis via linkExtensionToExisting).
            // Without this, downloads from AniList-linked content fail silently
            // with "no source for sourceId=null".
            val contentInfo = com.confused.anikuta.core.download.DownloadContentInfo(
                mainId = content.mainId,
                contentId = content.contentId,
                title = content.title,
                coverUrl = coverUrl,
                coverColor = null,
                contentFormat = content.contentFormat,
                contentType = content.contentType,
                // D-198: main_entry.description dropped — use content_details axes as fallback.
                description = details?.dataSynopsis ?: details?.extDescription,
                dataSourceId = content.dataSourceId,
                systemId = content.systemId,
                extensionRepoId = content.extensionRepoId,
                extensionId = content.extensionId ?: details?.extensionIdLong,
                sourceId = sourceIdStr.toLongOrNull() ?: content.sourceId ?: details?.sourceId,
                animeUrl = content.animeUrl ?: details?.animeUrl,
                displaySource = content.displaySource,
                anilistId = details?.anilistId,
            )
            val episodeInfo = com.confused.anikuta.core.download.DownloadEpisodeInfo(
                episodeKey = episode.url,
                episodeNumber = episode.episode_number,
                name = episode.name,
            )

            val sourceId = contentInfo.sourceId
            val source = sourceId?.let {
                org.koin.core.context.GlobalContext.get()
                    .get<com.confused.anikuta.data.extension.manager.ExtensionManager>()
                    .getSource(it) as? eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
            }

            if (source == null) {
                com.confused.anikuta.core.common.Logger.w("MainActivity") {
                    "handleDownloadSpecificVideo — no source for sourceId=$sourceId"
                }
                // D-210 FIX: surface the failure to the user instead of silently returning.
                showDownloadToast("Download failed: no extension source linked (sourceId=$sourceId). Try re-linking the source on the details page.")
                return@launch
            }

            val result = orchestrator.enqueueSpecific(
                source = source,
                episode = episode,
                content = contentInfo,
                episodeInfo = episodeInfo,
                video = com.confused.anikuta.core.videoresolver.ResolverVideo(
                    quality = video.quality,
                    url = video.url,
                    directUrl = video.directUrl,
                    videoHeaders = video.headers,
                    subtitleTracks = video.subtitleTracks,
                    audioTracks = video.audioTracks,
                ),
                serverName = serverName,
                audioLabel = audioLabel,
            )
            com.confused.anikuta.core.common.Logger.i("MainActivity") {
                "handleDownloadSpecificVideo — enqueueSpecific result: $result"
            }

            when (result) {
                is EnqueueResult.Success -> {
                    com.confused.anikuta.core.common.Logger.i("MainActivity") {
                        "Specific video download enqueued: taskId=${result.taskId}"
                    }
                    // D.FIX: Visual feedback — show a toast on the MAIN thread.
                    showDownloadToast("Download started")
                }
                is EnqueueResult.ShowPicker -> {
                    com.confused.anikuta.core.common.Logger.w("MainActivity") {
                        "handleDownloadSpecificVideo — ShowPicker (shouldn't happen for manual pick)"
                    }
                }
                is EnqueueResult.NoSources -> {
                    com.confused.anikuta.core.common.Logger.w("MainActivity") {
                        "handleDownloadSpecificVideo — no sources"
                    }
                    showDownloadToast("No extension source linked")
                }
                is EnqueueResult.Error -> {
                    com.confused.anikuta.core.common.Logger.e("MainActivity") {
                        "handleDownloadSpecificVideo failed: ${result.message}"
                    }
                    showDownloadToast("Download failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            com.confused.anikuta.core.common.Logger.e("MainActivity", e) {
                "handleDownloadSpecificVideo — exception"
            }
            showDownloadToast("Download failed: ${e.message}")
        }
    }
}

/**
 * Shows a toast on the main thread (safe to call from Dispatchers.IO).
 * D.FIX: Toast.makeText requires Looper.prepare() on the calling thread —
 * this helper posts to the main looper to avoid the NullPointerException.
 */
private fun showDownloadToast(message: String) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(
            org.koin.core.context.GlobalContext.get().get(),
            message,
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }
}

/**
 * Extracts a human-readable language label from a downloaded subtitle's
 * `content://` URI by parsing the on-disk filename.
 *
 * D-FIX-SUB: the download storage names subtitle files
 * `.subtitle_E{num}_{lang}_{index}.{ext}` (e.g. `.subtitle_E00001_english_0.srt`).
 * This extracts the `{lang}` segment + title-cases it ("english" → "English") so
 * the offline subtitle picker shows "English" / "Japanese" instead of "Subtitle 1".
 *
 * Falls back to `"Subtitle {index+1}"` for:
 * - Legacy filenames (`.subtitle_E{num}_{index}.{ext}` — pre-fix, no lang segment).
 * - URIs whose last path segment doesn't match the expected pattern.
 * - A `lang` segment of `"unknown"` (written when the track had no language).
 *
 * @param uri The subtitle `content://` URI.
 * @param index The 0-based track index (for the fallback label).
 */
private fun extractSubtitleLangFromUri(uri: String, index: Int): String {
    val fileName = android.net.Uri.parse(uri).lastPathSegment ?: return "Subtitle ${index + 1}"
    // Expected: .subtitle_E{num}_{lang}_{index}.{ext}
    // Strip the leading ".subtitle_E" prefix + the "E{num}_" episode segment.
    if (!fileName.startsWith("subtitle_E") && !fileName.startsWith(".subtitle_E")) return "Subtitle ${index + 1}"
    val withoutExt = fileName.substringBeforeLast('.')
    // withoutExt = ".subtitle_E00001_english_0" → split by '_' → [".subtitle", "E00001", "english", "0"]
    val segments = withoutExt.split('_')
    // Need at least 4 segments for the new format (prefix, enum, lang, index).
    // Legacy format has 3 segments (prefix, enum, index) → no lang → fallback.
    if (segments.size < 4) return "Subtitle ${index + 1}"
    val lang = segments[segments.size - 2] // second-to-last segment
    if (lang.isBlank() || lang == "unknown") return "Subtitle ${index + 1}"
    // Title-case: "english" → "English", "espanol-latino" → "Espanol Latino".
    return lang.split('-').joinToString(" ") { word ->
        word.replaceFirstChar { it.titlecase() }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onCancel: () -> Unit,
    onCategory: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Cancel — left
        SelectionButton(
            icon = androidx.compose.material.icons.Icons.Filled.Close,
            label = "Cancel",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        )
        // Category — center
        SelectionButton(
            icon = androidx.compose.material.icons.Icons.Filled.Category,
            label = "Category",
            color = MaterialTheme.colorScheme.primary,
            onClick = onCategory,
            modifier = Modifier.weight(1f),
        )
        // Delete — right
        SelectionButton(
            icon = androidx.compose.material.icons.Icons.Filled.Delete,
            label = "Delete",
            color = MaterialTheme.colorScheme.error,
            onClick = onDelete,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SelectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "selectionBtnScale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = title,
                collapsed = false,
                actions = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(50),
                            )
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .statusBarsPadding(),
            )
        }
    }
}

/**
 * D-151-fix: Builds a [WatchKey] for playing a downloaded episode, with ALL DB
 * queries + the subtitle disk-scan running off the main thread (called from
 * `withContext(Dispatchers.IO)` in the onPlayEpisode callback).
 *
 * Returns null if no local URI is found for the (mainId, episodeKey) pair.
 *
 * This was extracted from the old inline `onPlayEpisode` lambda which used
 * `runBlocking` (ANR risk) + called synchronous DB queries on the main thread.
 */
private suspend fun buildWatchKeyForDownloadedEpisode(
    mainId: String,
    episodeKey: String,
    downloadManager: com.confused.anikuta.core.download.DownloadManager,
    contentRepository: com.confused.anikuta.core.content.ContentRepository,
    dataCacheRepository: com.confused.anikuta.core.datacache.DataCacheRepository,
): WatchKey? {
    val localUri = downloadManager.getDownloadedEpisodeUri(mainId, episodeKey) ?: return null

    // Look up the downloaded episode for metadata.
    val allDownloaded = downloadManager.getDownloadedEpisodes().value
    val downloaded = allDownloaded
        .firstOrNull { it.content.mainId == mainId && it.episode.episodeKey == episodeKey }
        ?: allDownloaded.firstOrNull { it.content.mainId == mainId }
    val animeTitle = downloaded?.content?.title ?: "Downloaded"
    val epTitle = downloaded?.episode?.name ?: "Episode"
    val epNum = downloaded?.episode?.episodeNumber ?: 0f
    val quality = downloaded?.quality ?: ""

    // Load the FULL episode list from the data cache (not just downloaded episodes).
    val cachedEpisodes = dataCacheRepository.getEpisodeMetadata(mainId)
    val delim = com.confused.anikuta.core.common.EpisodeTitleParser.EPISODE_FIELD_DELIMITER
    val epListStr = if (cachedEpisodes.isNotEmpty()) {
        Logger.i("Anikuta:MainActivity") { "Downloads→Watch: loaded ${cachedEpisodes.size} episodes from cache for episode list" }
        cachedEpisodes
            .sortedBy { it.episodeNumber }
            .joinToString("\n") { meta ->
                "${meta.episodeUrl ?: episodeKey}${delim}${meta.episodeNumber}${delim}${meta.title ?: "Episode ${meta.episodeNumber.toInt()}"}"
            }
    } else {
        Logger.w("Anikuta:MainActivity") { "Downloads→Watch: no cached episodes — falling back to downloaded episodes only" }
        val allDl = downloadManager.getDownloadedEpisodes().value
            .filter { it.content.mainId == mainId }
            .sortedBy { it.episode.episodeNumber }
        allDl.joinToString("\n") { e ->
            "${e.episode.episodeKey}${delim}${e.episode.episodeNumber}${delim}${e.episode.name}"
        }
    }

    // Build episodeMetadataSerialized from the cache.
    val epMetaStr = if (cachedEpisodes.isNotEmpty()) {
        cachedEpisodes.joinToString("\n") { meta ->
            listOf(
                meta.episodeNumber.toInt().toString(),
                meta.title ?: "",
                meta.thumbnailUrl ?: "",
                (meta.airDate ?: 0L).toString(),
                meta.description ?: "",
                meta.scanlator ?: "",
            ).joinToString(delim)
        }
    } else ""

    // Pass local subtitle URIs from the downloaded episode.
    val subtitleUris = downloaded?.subtitleUris ?: emptyList()
    Logger.i("Anikuta:MainActivity") {
        "Downloads→Watch: downloaded=${downloaded != null}, " +
            "downloaded.subtitleUris.size=${subtitleUris.size}, " +
            "uris=${subtitleUris.joinToString("; ") { it.take(60) }}"
    }
    // Fallback: if subtitleUris is empty, scan the content folder's subtitles/ subfolder.
    // D-151-fix: was `runBlocking { ... }` (ANR risk) — now runs naturally on Dispatchers.IO.
    val effectiveSubUris = if (subtitleUris.isNotEmpty()) {
        subtitleUris
    } else {
        Logger.w("Anikuta:MainActivity") { "Downloads→Watch: subtitleUris empty in DB — trying disk scan" }
        scanSubtitleFilesOnDisk(mainId, downloaded?.episode?.episodeNumber?.toInt() ?: 0)
    }
    val subtitleTracksStr = effectiveSubUris.mapIndexed { index, uri ->
        val langLabel = extractSubtitleLangFromUri(uri, index)
        "$uri${delim}$langLabel"
    }.joinToString("\n")
    Logger.i("Anikuta:MainActivity") {
        "Downloads→Watch: passing ${effectiveSubUris.size} local subtitle track(s), subtitleTracksStr.length=${subtitleTracksStr.length}"
    }

    // Look up the sourceId so the watch screen can re-resolve non-downloaded episodes.
    val sourceId = contentRepository.getContentDetails(mainId)?.sourceId ?: 0L
    Logger.i("Anikuta:MainActivity") { "Downloads→Watch: sourceId=$sourceId (for episode switching)" }

    return WatchKey(
        videoUrl = localUri,
        animeTitle = animeTitle,
        quality = quality,
        episodeUrl = episodeKey,
        episodeNumber = epNum,
        episodeTitle = epTitle,
        episodeListSerialized = epListStr,
        videoHeaders = "",
        resolvedVideosKey = "",
        sourceId = sourceId,
        mainId = mainId,
        subtitleTracksSerialized = subtitleTracksStr,
        audioTracksSerialized = "",
        episodeMetadataSerialized = epMetaStr,
    )
}

/**
 * Fallback: scans the SAF storage for subtitle files for a specific episode.
 * Used when the downloaded_episode DB table doesn't have subtitleUris populated.
 *
 * Searches the content folder's "subtitles" subfolder (new) + the root (legacy)
 * for files matching the episode number pattern.
 */
private suspend fun scanSubtitleFilesOnDisk(mainId: String, episodeNumber: Int): List<String> {
    if (episodeNumber <= 0) return emptyList()
    val epNumPadded = String.format("%05d", episodeNumber)
    val results = mutableListOf<String>()

    try {
        val koin = org.koin.core.context.GlobalContext.get()
        val storage = koin.get<com.confused.anikuta.core.download.DownloadStorageProvider>()

        val contentDir = storage.findContentFolder(mainId) ?: return emptyList()

        val subtitlesDir = contentDir.listFiles().firstOrNull { it.name == "subtitles" && it.isDirectory }
        val searchDirs = if (subtitlesDir != null) listOf(subtitlesDir, contentDir) else listOf(contentDir)

        for (dir in searchDirs) {
            for (file in dir.listFiles()) {
                if (!file.isFile) continue
                val name = file.name ?: continue
                if ((name.startsWith("subtitle_E${epNumPadded}_") || name.startsWith(".subtitle_E${epNumPadded}_")) &&
                    (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass") || name.endsWith(".ssa"))
                ) {
                    results.add(file.uri.toString())
                    com.confused.anikuta.core.common.Logger.i("Anikuta:MainActivity") {
                        "scanSubtitleFilesOnDisk — found: ${name.take(60)}"
                    }
                }
            }
        }
    } catch (e: Exception) {
        com.confused.anikuta.core.common.Logger.w("Anikuta:MainActivity") {
            "scanSubtitleFilesOnDisk failed: ${e.message}"
        }
    }

    return results
}
