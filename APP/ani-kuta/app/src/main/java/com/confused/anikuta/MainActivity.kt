package com.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.confused.anikuta.core.designsystem.component.AnikutaBottomNavBar
import com.confused.anikuta.core.designsystem.component.NavIcons
import com.confused.anikuta.core.designsystem.component.NavItem
import com.confused.anikuta.core.designsystem.theme.AnikutaTheme
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animebrowse.AnimeBrowseKey
import com.confused.anikuta.feature.animebrowse.BrowseScreen
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import com.confused.anikuta.feature.animedetails.DetailsScreen
import com.confused.anikuta.feature.animelibrary.AnimeLibraryKeyImpl
import com.confused.anikuta.feature.animelibrary.LibraryScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnikutaTheme {
                AppRoot()
            }
        }
    }
}

/**
 * ANI-KUTA navigation root.
 *
 * Phase 4a: Bottom navigation with 4 tabs (Browse, Library, Search, More).
 * The bottom nav is a floating pill overlay — content scrolls behind it.
 *
 * FIX: Removed AnimatedContent — it was disposing ViewModels on navigation,
 * causing the Browse page to crash/blank when returning from Details.
 * Using a simple when() block instead (like Phase 2). Animations will be
 * added per-screen (fade-in on content) rather than on the container level.
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

    Box(modifier = Modifier.fillMaxSize()) {
        // Content — simple when() block (no AnimatedContent to avoid ViewModel disposal)
        when (currentKey) {
            is AnimeBrowseKey -> BrowseScreen(
                onNavigate = { navKey -> backstack.add(navKey) }
            )
            is AnimeDetailsKey -> DetailsScreen(
                animeId = currentKey.animeId,
                onBack = { backstack.removeAt(backstack.lastIndex) }
            )
            is AnimeLibraryKeyImpl -> LibraryScreen(
                onNavigateToDetails = { animeId ->
                    backstack.add(AnimeDetailsKey(animeId))
                }
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
                    "search" -> backstack.add(AnimeBrowseKey) // Placeholder — Phase 4c
                    "more" -> backstack.add(AnimeBrowseKey) // Placeholder — Phase 4d
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
