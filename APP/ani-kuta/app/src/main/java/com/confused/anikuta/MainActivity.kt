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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * CORE_RULES §22: smooth animations on tab switches (fade transition).
 * CORE_RULES §23: reactive state (StateFlow from ViewModels).
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
        // Content (scrolls behind the nav bar)
        AnimatedContent(
            targetState = currentKey,
            transitionSpec = {
                ContentTransform(
                    fadeIn(tween(Motion.DurationStandard)),
                    fadeOut(tween(Motion.DurationShort)),
                    1,
                )
            },
            label = "screenTransition",
            contentKey = { it::class },
        ) { key ->
            when (key) {
                is AnimeBrowseKey -> BrowseScreen(
                    onNavigate = { navKey -> backstack.add(navKey) }
                )
                is AnimeDetailsKey -> DetailsScreen(
                    animeId = key.animeId,
                    onBack = { backstack.removeAt(backstack.lastIndex) }
                )
                else -> {}
            }
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
                    "library" -> backstack.add(AnimeBrowseKey) // Placeholder — Phase 4c
                    "search" -> backstack.add(AnimeBrowseKey) // Placeholder — Phase 4c
                    "more" -> backstack.add(AnimeBrowseKey) // Placeholder — Phase 4d
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
