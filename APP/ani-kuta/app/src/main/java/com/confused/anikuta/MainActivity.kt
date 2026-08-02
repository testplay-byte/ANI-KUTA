package com.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()
        setContent {
            val prefs = koinInject<ThemePreferences>()
            val themeMode = prefs.themeMode.value
            val amoled = prefs.amoled.value
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AnikutaTheme(darkTheme = isDark, amoled = amoled) {
                AppRoot()
            }
        }
    }
}

/**
 * NavKey for the More screen. Lives in `:app` (not a feature module) because
 * the More screen composes entries from multiple feature modules.
 */
@Serializable
object MoreKey : NavKey

/**
 * NavKey for the Settings hub.
 */
@Serializable
object SettingsKey : NavKey

/**
 * NavKey for the Appearance screen.
 */
@Serializable
object AppearanceKey : NavKey

/**
 * NavKey for the Appearance → General screen.
 */
@Serializable
object AppearanceGeneralKey : NavKey

/**
 * NavKey for the Episode Settings hub (placeholder — Phase 5+).
 */
@Serializable
object EpisodeSettingsKey : NavKey

/**
 * ANI-KUTA navigation root.
 *
 * Phase 4a: Bottom navigation with 4 tabs (Browse, Library, Search, More).
 * The bottom nav is a floating pill overlay — content scrolls behind it.
 *
 * FIX: Removed AnimatedContent — it was disposing ViewModels on navigation,
 * causing the Browse page to crash/blank when returning from Details.
 * Using a simple when() block instead. Animations will be added per-screen
 * (fade-in on content) rather than at the container level.
 *
 * CORE_RULES §22: animations handled per-screen, not at container level.
 * CORE_RULES §23: reactive state — ViewModels survive navigation.
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

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentKey) {
            is AnimeBrowseKey -> BrowseScreen(
                onNavigate = { navKey -> backstack.add(navKey) }
            )
            is AnimeDetailsKey -> DetailsScreen(
                animeId = currentKey.animeId,
                onBack = pop,
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
            else -> {}
        }

        // Bottom navigation (floating pill overlay)
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

/**
 * A simple placeholder screen used for nav targets that don't have a real
 * implementation yet (e.g. EpisodeSettingsKey).
 */
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
