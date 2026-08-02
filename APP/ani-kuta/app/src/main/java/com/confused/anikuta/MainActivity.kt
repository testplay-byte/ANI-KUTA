package com.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.confused.anikuta.core.designsystem.theme.AnikutaTheme
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
 * Nav3 pattern (architecture plan §5): state-owned backstack as a list of @Serializable NavKey.
 * The backstack is a mutableStateListOf — survives configuration changes (rotation).
 * Phase 3: migrate to rememberSaveable + custom Saver for process-death survival.
 *
 * Note: This is a simple implementation of the Nav3 pattern. When we need Nav3's advanced
 * features (deep linking, scene strategies, overlay sheets), we'll adopt the full
 * androidx.navigation3.NavDisplay API. For Phase 2, this validates the type-safe route
 * pattern + multi-module navigation.
 */
@Composable
fun AppRoot() {
    val backstack = remember { mutableStateListOf<NavKey>(AnimeBrowseKey) }
    val currentKey = backstack.last()

    when (currentKey) {
        is AnimeBrowseKey -> BrowseScreen(
            onNavigate = { key -> backstack.add(key) }
        )
        is AnimeDetailsKey -> DetailsScreen(
            animeId = currentKey.animeId,
            onBack = { backstack.removeAt(backstack.lastIndex) }
        )
        // NavKey is not sealed (cross-module) — unknown keys are a no-op.
        // Phase 3+ will add more NavKey types as features are built.
        else -> {}
    }
}
