package com.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
 * Nav3 pattern: state-owned backstack. The current tab + navigated screens
 * are tracked in a mutableStateListOf. Tab switches replace the backstack root.
 *
 * CORE_RULES §22: smooth animations on tab switches (fade transition).
 * CORE_RULES §23: reactive state (StateFlow from ViewModels).
 */
@Composable
fun AppRoot() {
    // ── Bottom nav state ──
    val navItems = remember {
        listOf(
            NavItem("browse", "Browse", NavIcons.Browse),
            NavItem("library", "Library", NavIcons.Library),
            NavItem("search", "Search", NavIcons.Search),
            NavItem("more", "More", NavIcons.More),
        )
    }
    var currentTab by remember { mutableStateOf("browse") }

    // ── Navigation backstack ──
    // For now, we use a simple list. Phase 4b will adopt full Nav3 NavDisplay.
    val backstack = remember { androidx.compose.runtime.mutableStateListOf<NavKey>(AnimeBrowseKey) }
    val currentKey = backstack.last()

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Content (scrolls behind the nav bar) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 0.dp), // No bottom padding — content scrolls behind nav
        ) {
            // Animated content switch (CORE_RULES §22)
            androidx.compose.animation.AnimatedContent(
                targetState = currentKey,
                transitionSpec = {
                    fadeIn(tween(Motion.DurationStandard)) togetherWith
                        fadeOut(tween(Motion.DurationShort))
                },
                label = "screenTransition",
            ) { key ->
                when (key) {
                    is AnimeBrowseKey -> BrowseScreen(
                        onNavigate = { navKey -> backstack.add(navKey) }
                    )
                    is AnimeDetailsKey -> DetailsScreen(
                        animeId = key.animeId,
                        onBack = { backstack.removeAt(backstack.lastIndex) }
                    )
                    // Phase 4c will add Library, Search screens
                    // Phase 4d will add More screen
                    else -> {}
                }
            }
        }

        // ── Bottom navigation (floating pill overlay) ──
        AnikutaBottomNavBar(
            items = navItems,
            currentRoute = currentTab,
            onSelect = { route ->
                currentTab = route
                // Reset backstack to the tab's root
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

// Import for AnimatedContent transition
private infix fun <T> androidx.compose.animation.EnterTransition.togetherWith(
    exit: androidx.compose.animation.ExitTransition
): androidx.compose.animation.ContentTransform {
    return androidx.compose.animation.togetherWith(this, exit)
}

// Extension to make 'togetherWith' work with AnimatedContent
private fun androidx.compose.animation.togetherWith(
    enter: androidx.compose.animation.EnterTransition,
    exit: androidx.compose.animation.ExitTransition,
): androidx.compose.animation.ContentTransform {
    return androidx.compose.animation.ContentTransform(enter, exit, 1)
}
