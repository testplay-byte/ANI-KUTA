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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.AnikutaBottomNavBar
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.common.Logger
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
import com.confused.anikuta.feature.extensionssettings.ExtensionsSettingsScreen
import com.confused.anikuta.feature.extensionssettings.AutoLinkSettingsKey
import com.confused.anikuta.feature.extensionssettings.AutoLinkSettingsScreen
import com.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsKey
import com.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsScreen
import com.confused.anikuta.feature.watch.WatchKey
import com.confused.anikuta.feature.watch.WatchScreen
import com.confused.anikuta.download.DownloadOrchestrator
import com.confused.anikuta.download.EnqueueResult
import com.confused.anikuta.settings.AppearanceGeneralScreen
import com.confused.anikuta.settings.AppearanceScreen
import com.confused.anikuta.settings.SettingsScreen
import com.confused.anikuta.settings.ThemeMode
import com.confused.anikuta.settings.ThemePreferences
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

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
}

@Serializable
object MoreKey : NavKey

@Serializable
object SettingsKey : NavKey

@Serializable
object AppearanceKey : NavKey

@Serializable
object AppearanceGeneralKey : NavKey

@Serializable
object EpisodeSettingsKey : NavKey

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
@Composable
fun AppRoot() {
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

    val backstack = remember {
        androidx.compose.runtime.mutableStateListOf<NavKey>(AnimeBrowseKey)
    }
    val currentKey = backstack.last()

    val pop: () -> Unit = {
        if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
    }

    // BackHandler: handle device back gesture properly
    BackHandler(enabled = backstack.size > 1) {
        pop()
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLibrarySelectionMode provides librarySelectionMode) {
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
                        onNavigateToWatch = { videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders, resolvedVideosKey, sourceId, subTracks, audioTracks, epMeta ->
                            backstack.add(WatchKey(videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders, resolvedVideosKey, sourceId, subTracks, audioTracks, epMeta))
                        },
                        onDownloadEpisode = { episode ->
                            handleDownloadEpisode(
                                detailsKey = currentKey,
                                episode = episode,
                                orchestrator = orchestrator,
                                contentRepository = contentRepository,
                            )
                        },
                    )
                    is AnimeDetailsKey.Extension -> DetailsScreen(
                        detailsKey = currentKey,
                        onBack = pop,
                        onNavigateToWatch = { videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders, resolvedVideosKey, sourceId, subTracks, audioTracks, epMeta ->
                            backstack.add(WatchKey(videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders, resolvedVideosKey, sourceId, subTracks, audioTracks, epMeta))
                        },
                        onDownloadEpisode = { episode ->
                            handleDownloadEpisode(
                                detailsKey = currentKey,
                                episode = episode,
                                orchestrator = orchestrator,
                                contentRepository = contentRepository,
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
            )
            is MoreKey -> MoreScreen(
                onOpenSettings = { backstack.add(SettingsKey) },
                onOpenDownloads = { backstack.add(DownloadsKey) },
            )
            is DownloadsKey -> DownloadsScreen(
                onBack = pop,
                onOpenSettings = { backstack.add(DownloadSettingsKey) },
                onOpenDownloaded = { backstack.add(DownloadedFilesKey) },
            )
            is DownloadedFilesKey -> DownloadedFilesScreen(
                onBack = pop,
                onPlayEpisode = { mainId, episodeKey ->
                    // D.6: Player integration — short-circuit the resolver when the
                    // episode is already downloaded. Build a WatchKey with the local
                    // content:// URI as the video URL.
                    val localUri = downloadManager.getDownloadedEpisodeUri(mainId, episodeKey)
                    if (localUri != null) {
                        backstack.add(
                            WatchKey(
                                videoUrl = localUri,
                                animeTitle = "Downloaded",
                                quality = "",
                                episodeUrl = episodeKey,
                                episodeNumber = 0f,
                                episodeTitle = "Downloaded episode",
                                episodeListSerialized = "",
                                videoHeaders = "",
                                resolvedVideosKey = "",
                                sourceId = 0L,
                                subtitleTracksSerialized = "",
                                audioTracksSerialized = "",
                                episodeMetadataSerialized = "",
                            ),
                        )
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
                onBack = pop,
            )
            is ExtensionsSettingsKey -> ExtensionsSettingsScreen(
                onBack = pop,
                onOpenRepoSettings = { backstack.add(ExtensionRepoSettingsKey) },
            )
            is ExtensionRepoSettingsKey -> ExtensionRepoSettingsScreen(
                onBack = pop,
            )
            is AutoLinkSettingsKey -> AutoLinkSettingsScreen(
                onBack = pop,
            )
            is AppearanceKey -> AppearanceScreen(
                onOpenGeneral = { backstack.add(AppearanceGeneralKey) },
                onOpenEpisodeSettings = { backstack.add(EpisodeSettingsKey) },
                onBack = pop,
            )
            is AppearanceGeneralKey -> AppearanceGeneralScreen(
                onBack = pop,
            )
            is EpisodeSettingsKey -> PlaceholderScreen(
                title = "Episode settings",
                description = "Episode display settings will be added in a future phase.",
                onBack = pop,
            )
            is WatchKey -> WatchScreen(
                watchKey = currentKey,
                onBack = pop,
            )
            else -> {}
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
                    contentRepository.getContentByAniListId(detailsKey.animeId)?.mainId
                is AnimeDetailsKey.Extension ->
                    contentRepository.getContentByExtension(detailsKey.sourceId, detailsKey.animeUrl)?.mainId
            }
            if (mainId == null) {
                com.confused.anikuta.core.common.Logger.w("MainActivity") {
                    "handleDownloadEpisode — no mainId for detailsKey=$detailsKey"
                }
                return@launch
            }

            // 2. Build the content + episode identity (cover is best-effort — null is fine).
            val content = contentRepository.getContentByMainId(mainId)
            if (content == null) {
                com.confused.anikuta.core.common.Logger.w("MainActivity") {
                    "handleDownloadEpisode — no content for mainId=$mainId"
                }
                return@launch
            }
            val anilistDetail = contentRepository.getAniListDetail(mainId)
            val extDetail = contentRepository.getExtensionDetail(mainId)
            val coverUrl = anilistDetail?.coverUrl ?: extDetail?.thumbnailUrl
            val contentInfo = com.confused.anikuta.core.download.DownloadContentInfo(
                mainId = content.mainId,
                contentId = content.contentId,
                title = content.title,
                coverUrl = coverUrl,
                coverColor = null,
                contentFormat = content.contentFormat,
                contentType = content.contentType,
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
