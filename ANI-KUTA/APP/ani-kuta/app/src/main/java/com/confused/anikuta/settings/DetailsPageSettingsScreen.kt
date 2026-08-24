package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.AppPreferences
import org.koin.compose.koinInject

// D-237: Reuse the shared helpers from AppearanceGeneralScreen (same package).
// SettingsSectionLabel is public in SettingsScreen.kt; SwitchCard + SettingsCard
// are private in AppearanceGeneralScreen.kt — so we define local copies here.

/**
 * D-237: Dedicated settings screen for the Details page background customization.
 *
 * Contains: accent tint toggle, background image source (cover/banner),
 * animated background toggle. Lives under Appearance > Details page.
 */
@Composable
fun DetailsPageSettingsScreen(
    onBack: () -> Unit,
) {
    val appPrefs = koinInject<AppPreferences>()

    // D-237: Use local Compose state snapshots that flip on change AND write-through
    // to prefs (D-132 reactivity pattern — same as AutoLinkSettingsScreen).
    var tintEnabled by remember { mutableStateOf(appPrefs.detailsBannerTint) }
    var bgSource by remember { mutableStateOf(appPrefs.detailsBackgroundSource) }
    var animationEnabled by remember { mutableStateOf(appPrefs.detailsBannerAnimation) }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Details page",
                collapsed = collapsed,
                actions = {
                    BackAction(onBack)
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── Accent tint ──
                    item {
                        SettingsSectionLabel("Background")
                    }
                    item {
                        SwitchCard(
                            title = "Accent tint",
                            subtitle = "Tint the background image with the cover-derived accent color",
                            checked = tintEnabled,
                            onCheckedChange = {
                                tintEnabled = it
                                appPrefs.detailsBannerTint = it
                            },
                        )
                    }
                    item {
                        SettingsCard {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Background image",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Choose which image to show. Banner falls back to cover if unavailable.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    listOf("COVER" to "Cover", "BANNER" to "Banner").forEach { (value, label) ->
                                        val isSelected = bgSource == value
                                        Surface(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    bgSource = value
                                                    appPrefs.detailsBackgroundSource = value
                                                },
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontFamily = RobotoFamily,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SwitchCard(
                            title = "Animated background",
                            subtitle = "Slowly pan the background image for a dynamic effect",
                            checked = animationEnabled,
                            onCheckedChange = {
                                animationEnabled = it
                                appPrefs.detailsBannerAnimation = it
                            },
                        )
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

// ── Shared UI helpers (local copies — the ones in AppearanceGeneralScreen are private) ──

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
