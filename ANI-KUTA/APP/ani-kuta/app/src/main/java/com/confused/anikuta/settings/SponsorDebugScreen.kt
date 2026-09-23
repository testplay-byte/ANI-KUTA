package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.confused.anikuta.core.ads.AdPreferences
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.koinInject

/**
 * D-562 (round 74) — the hidden debug page (Settings → LONG-PRESS
 * "Debug options" → this page).
 *
 * The v1.1.35 device round re-specced the D-561 page in three ways:
 *  - "The heading of it is not proper" → the page IS the debug-options page
 *    (it opens from that row), so the heading says **"Debug options"** — the
 *    D-561 "Always sponsor" heading repeated the toggle's own title below it.
 *  - "that does not need to be given a description" → the toggle row is the
 *    title + the Switch, NOTHING else.
 *  - The trigger moved OFF app-open ONTO the entry click: "what I wanted
 *    was that every time the user clicks on an entry, then it will show the
 *    sponsor rather than every time opening up the app." The gate lives in
 *    [com.confused.anikuta.core.ads.AdsCoordinator.requestNavigation] — the
 *    one helper every navigate-to-details tap goes through — so ON = EVERY
 *    entry click into a details page shows the sponsor interstitial, and
 *    completing it navigates to the tapped entry. OFF = the normal ad
 *    system, byte-for-byte.
 *
 * ONE toggle, nothing more: [AdPreferences.alwaysSponsor] (default OFF).
 * The page ships in BOTH build types — it is a debug TOOL, not a
 * debug-build-only row (the same reasoning as the Debug page itself: the
 * user tests release APKs).
 */
@Composable
fun SponsorDebugScreen(
    onBack: () -> Unit,
    adPreferences: AdPreferences = koinInject(),
) {
    // Write-through reactive read — the same pattern as the Debug page's
    // switches (a Flow-backed preference, so the Switch never lies).
    val alwaysSponsor by adPreferences.alwaysSponsorFlow()
        .collectAsStateWithLifecycle(initialValue = adPreferences.alwaysSponsor)

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Debug options",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SettingsSectionLabel("Debug")
                    }
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // D-562: the title + the Switch, NOTHING else —
                                // "that does not need to be given a description".
                                Text(
                                    text = "Always sponsor",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(12.dp))
                                Switch(
                                    checked = alwaysSponsor,
                                    onCheckedChange = { adPreferences.alwaysSponsor = it },
                                )
                            }
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}
