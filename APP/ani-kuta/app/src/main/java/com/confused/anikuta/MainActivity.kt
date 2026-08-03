package com.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.AnikutaBottomNavBar
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
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
import com.confused.anikuta.feature.animelibrary.LibraryScreen
import com.confused.anikuta.feature.animesearch.AnimeSearchKey
import com.confused.anikuta.feature.animesearch.SearchScreen
import com.confused.anikuta.feature.extensionssettings.ExtensionsSettingsKey
import com.confused.anikuta.feature.extensionssettings.ExtensionsSettingsScreen
import com.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsKey
import com.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsScreen
import com.confused.anikuta.feature.watch.WatchKey
import com.confused.anikuta.feature.watch.WatchScreen
import com.confused.anikuta.settings.AppearanceGeneralScreen
import com.confused.anikuta.settings.AppearanceScreen
import com.confused.anikuta.settings.SettingsScreen
import com.confused.anikuta.settings.ThemeMode
import com.confused.anikuta.settings.ThemePreferences
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

    val backstack = remember {
        androidx.compose.runtime.mutableStateListOf<NavKey>(AnimeBrowseKey)
    }
    val currentKey = backstack.last()

    val pop: () -> Unit = {
        if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
    }

    // BackHandler: handle device back gesture properly
    // If backstack has more than 1 item, pop. Otherwise, let the system handle (exit).
    BackHandler(enabled = backstack.size > 1) {
        pop()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (currentKey) {
            is AnimeBrowseKey -> BrowseScreen(
                onNavigate = { navKey -> backstack.add(navKey) }
            )
            is AnimeDetailsKey -> DetailsScreen(
                animeId = currentKey.animeId,
                onBack = pop,
                onNavigateToWatch = { videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders ->
                    backstack.add(WatchKey(videoUrl, animeTitle, quality, epUrl, epNum, epTitle, epList, videoHeaders))
                },
            )
            is AnimeLibraryKeyImpl -> LibraryScreen(
                onNavigateToDetails = { animeId ->
                    backstack.add(AnimeDetailsKey(animeId))
                }
            )
            is AnimeSearchKey -> SearchScreen(
                onNavigateToDetails = { animeId ->
                    backstack.add(AnimeDetailsKey(animeId))
                }
            )
            is MoreKey -> MoreScreen(
                onOpenSettings = { backstack.add(SettingsKey) },
            )
            is SettingsKey -> SettingsScreen(
                onOpenAppearance = { backstack.add(AppearanceKey) },
                onOpenExtensions = { backstack.add(ExtensionsSettingsKey) },
                onBack = pop,
            )
            is ExtensionsSettingsKey -> ExtensionsSettingsScreen(
                onBack = pop,
                onOpenRepoSettings = { backstack.add(ExtensionRepoSettingsKey) },
            )
            is ExtensionRepoSettingsKey -> ExtensionRepoSettingsScreen(
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
            )
        }
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
