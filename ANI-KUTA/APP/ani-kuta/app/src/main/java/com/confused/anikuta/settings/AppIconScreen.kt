package com.confused.anikuta.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.R
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.AppIconPreferences
import org.koin.compose.koinInject

// ════════════════════════════════════════════════════════════════════════════
//  D-562 (round 74): the App Icon page — Settings → Appearance → App Icon.
// ════════════════════════════════════════════════════════════════════════════
//
// The v1.1.35 device round ordered two things:
//  - "the app icon functionality is not working… when I change the app icon
//    in the settings, then the app icon should actually be changed for the
//    application" — the D-432/D-561 machinery only swapped an IN-APP preview
//    (an exported PNG + an AsyncImage hero); the home-screen icon never
//    moved. Android forbids runtime launcher icons from arbitrary bitmaps,
//    so a REAL switch needs baked resources + activity-aliases — the proven
//    D-417 shape, restored: MAIN/LAUNCHER lives on 7 aliases (.icons.IconDefault
//    + one per preset), a pick enables the picked alias and disables the
//    previous one via PackageManager.setComponentEnabledSetting (target
//    FIRST — the launcher never has zero entries), DONT_KILL_APP.
//  - "the functionality that the application searches for the app icons in
//    the GitHub repository… remove this functionality and only keep the
//    preset app icons" — the entire catalog machinery (the network fetch,
//    the cache, the grid, the refresh, the empty note) is deleted outright.
//
// The page is now: the hero (the ACTIVE launcher icon) + the six BAKED
// presets. Same D-432 display rule: the FULL artwork in a rounded-corner
// cell — never a circle crop.

/** One BAKED preset icon (a drawable resource + its display name). */
data class PresetIcon(
    val key: String,
    val resId: Int,
    val name: String,
)

/**
 * The controller: the REAL launcher-icon switch. Each preset maps to a
 * manifest activity-alias carrying MAIN/LAUNCHER with its own baked icon;
 * applying a pick flips the enabled alias.
 *
 * Component-name safety (the D-417 verified pattern): the alias class
 * strings resolve against the NAMESPACE (com.confused.anikuta), NOT the
 * applicationId — so the .debug suffixed build uses the SAME strings, with
 * [ComponentName]'s package taken from [Context.getPackageName] (suffixed
 * in debug). Suffix-safe by construction.
 */
class AppIconController(
    private val context: Context,
    private val preferences: AppIconPreferences,
) {

    /** The persisted launcher choice ("" = the app's default icon). */
    val activeLauncherKey: String get() = preferences.launcherIconKey

    /**
     * Applies [key] (null = back to the default icon) as the REAL launcher
     * icon: the target alias is enabled FIRST, then the previously-active
     * alias is disabled — the app never has zero enabled launcher entries.
     * [PackageManager.DONT_KILL_APP] keeps the process alive through the
     * switch. Persisted so the choice (and the reconcile below) survives
     * process death.
     */
    fun applyLauncherIcon(key: String?) {
        val pm = context.packageManager
        val target = aliasClassFor(key)
        val previous = aliasClassFor(preferences.launcherIconKey.takeIf { it.isNotBlank() })
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, target),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        if (previous != target) {
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, previous),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        preferences.launcherIconKey = key.orEmpty()
    }

    /**
     * Self-heal for the app-UPDATE gap: PackageManager component states
     * survive an update, but an install that resets them to the manifest
     * defaults (all presets disabled, the default alias enabled) leaves the
     * persisted [AppIconPreferences.launcherIconKey] pointing at an alias
     * that is no longer enabled — the launcher would show the default icon
     * while the page claims otherwise. Re-apply the saved choice when its
     * alias is not the one enabled right now. Cheap (one binder read, only
     * when a preset is persisted) — called on every process start
     * ([com.confused.anikuta.AnikutaApp]) and on every page open.
     */
    fun reconcileLauncherIcon() {
        val saved = preferences.launcherIconKey.takeIf { it.isNotBlank() } ?: return
        val pm = context.packageManager
        // applyLauncherIcon always sets its pick EXPLICITLY enabled — a state
        // of DEFAULT (manifest reset: the alias is disabled by default) or
        // DISABLED means the launcher is NOT showing the saved choice.
        val state = pm.getComponentEnabledSetting(
            ComponentName(context.packageName, aliasClassFor(saved)),
        )
        if (state != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            applyLauncherIcon(saved)
        }
    }

    private companion object {
        /**
         * "" → `.icons.IconDefault`; "dark" → `.icons.IconDark`; … The alias
         * names MUST match the manifest exactly (D-562). The FQCN is
         * namespace-resolved — see the class doc.
         */
        fun aliasClassFor(key: String?): String {
            val suffix = key?.takeIf { it.isNotBlank() } ?: "Default"
            // Capitalize WITHOUT replaceFirstChar — its (Char)->Char and
            // (Char)->CharSequence overloads are resolution-ambiguous for a
            // mixed lambda on this Kotlin line (the round-74 first CI run
            // tripped exactly there).
            val capped = suffix.substring(0, 1).uppercase() + suffix.substring(1)
            return "com.confused.anikuta.icons.Icon$capped"
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  The screen
// ════════════════════════════════════════════════════════════════════════════

/** The shared display shape — a rounded-corner square (the D-432 format). */
private val IconCellShape = RoundedCornerShape(16.dp)

/**
 * The BAKED PRESETS — the user's six provided artworks (RAW_ICONS.zip),
 * center-cropped + baked at 512px into drawable-nodpi AND wired as REAL
 * launcher icons (each carries a manifest alias + an adaptive mipmap —
 * see [AppIconController]). Always present, no network, no repository.
 * The keys ARE the alias suffixes (`.icons.Icon<Capitalized>`).
 */
private val PRESET_ICONS = listOf(
    PresetIcon("dark", R.drawable.preset_icon_dark, "Dark"),
    PresetIcon("teal", R.drawable.preset_icon_teal, "Teal"),
    PresetIcon("sky", R.drawable.preset_icon_sky, "Sky"),
    PresetIcon("gold", R.drawable.preset_icon_gold, "Gold"),
    PresetIcon("green", R.drawable.preset_icon_green, "Green"),
    PresetIcon("pink", R.drawable.preset_icon_pink, "Pink"),
)

/** The active icon's drawable for the hero — a preset's artwork or the app's. */
private fun activeIconRes(activeKey: String): Int =
    PRESET_ICONS.firstOrNull { it.key == activeKey }?.resId ?: R.drawable.icon_current

@Composable
fun AppIconScreen(
    onBack: () -> Unit,
    /** D-558: the search-landing anchor (see SettingsSearchNavigator). */
    highlightAnchor: String? = null,
    preferences: AppIconPreferences = koinInject(),
) {
    val context = LocalContext.current
    val controller = remember { AppIconController(context, preferences) }

    // The persisted launcher choice — hoisted so the hero AND the grid's
    // selected rings read the same value. Re-reconciled on open (the
    // update-reset self-heal; belt-and-braces next to AnikutaApp's start
    // call).
    var activeKey by remember { mutableStateOf(controller.activeLauncherKey) }
    LaunchedEffect(Unit) {
        controller.reconcileLauncherIcon()
        activeKey = controller.activeLauncherKey
    }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "App Icon",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                // ── D-558: the search-landing scroll (0 hero · …).
                com.confused.anikuta.settings.search.rememberSettingsAnchorScroll(
                    anchor = highlightAnchor,
                    anchorIndexFor = { anchor ->
                        if (anchor == "app_icon") 0 else null
                    },
                    listState = lazyListState,
                )
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── The current icon hero ──
                    item(key = "hero") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // D-432: the hero shows the FULL artwork in the
                                // rounded-corner format — never a circle crop.
                                // D-562: it reads the ACTIVE LAUNCHER icon —
                                // the exact artwork the home screen shows.
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(RoundedCornerShape(20.dp)),
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(
                                            activeIconRes(activeKey),
                                        ),
                                        contentDescription = "Current app icon",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Current icon",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = PRESET_ICONS
                                            .firstOrNull { it.key == activeKey }?.name
                                            ?: "The app's icon",
                                        fontFamily = RobotoFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                    if (activeKey.isNotBlank()) {
                                        Text(
                                            // The pick is the REAL launcher icon now
                                            // (the alias switch) — say so, quietly.
                                            text = "Launcher icon",
                                            fontFamily = RobotoFamily,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── The BAKED PRESETS — the six provided artworks, the
                    // ONLY icons (the GitHub catalog is removed, D-562). ──
                    item(key = "presets-header") {
                        SettingsSectionLabel("Presets")
                    }
                    PRESET_ICONS.chunked(4).forEach { rowIcons ->
                        item(key = "preset-row-${rowIcons.first().key}") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowIcons.forEach { preset ->
                                    PresetCell(
                                        preset = preset,
                                        selected = activeKey == preset.key,
                                        onClick = {
                                            controller.applyLauncherIcon(preset.key)
                                            activeKey = controller.activeLauncherKey
                                            Toast.makeText(
                                                context,
                                                "App icon updated",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    if (activeKey.isNotBlank()) {
                        item(key = "reset-default") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        controller.applyLauncherIcon(null)
                                        activeKey = controller.activeLauncherKey
                                        Toast.makeText(
                                            context,
                                            "App icon updated",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                            ) {
                                Text(
                                    text = "Back to the app's icon",
                                    fontFamily = RobotoFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth(),
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

/**
 * One BAKED preset grid cell — identical visual language to the D-561 cell.
 * The artwork IS the baked drawable, so the cell paints it straight from
 * the resource (no file I/O anywhere on this page anymore).
 */
@Composable
private fun PresetCell(
    preset: PresetIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        // The D-432 display rule holds: the FULL artwork in a rounded-corner
        // cell — never a circle crop.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(IconCellShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = IconCellShape,
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(preset.resId),
                contentDescription = preset.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = preset.name,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
