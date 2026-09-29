package com.confused.anikuta.feature.animedetails

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.model.DataSourcePriority
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.designsystem.theme.SuccessDark
import com.confused.anikuta.core.designsystem.theme.SuccessLight
import com.confused.anikuta.core.share.ShareLink
import com.confused.anikuta.core.share.ShareTargetKind

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-1): DetailsActionMenu — the anchored menu's SECOND PASS.
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.59 device round kept the anchored-dropdown FORM ("it opens up
//  properly and it kind of looks clean") but ordered the look brought back to
//  the app's aesthetic:
//
//  • THE CRIMP — "you can crimp the menu a bit where the separations are…
//    crimp the menu where the data source section ends, and the other options
//    end, and also you can crimp the sides where the other sections get
//    started." Every section is its own INSET ROUNDED CARD (16dp corners on a
//    quiet surfaceVariant tint) with a visible gap between the groups — the
//    inset-grouped rhythm, replacing the flat list + hairline dividers.
//
//  • THE ROW-FORMAT DATA SOURCE — "in a row kind of format rather than a
//    column, like one at the top and one at the bottom. Not like that. They
//    should be shown just like how they were previously shown": the round-101
//    sheet's side-by-side segmented chips are back. The LEFT chip carries ONLY
//    the list system's NAME ("AniList" — future data-source systems append
//    here); the RIGHT chip carries the EXTENSION TEXT (the linked source's
//    actual name, not the generic word "Extension"). BELOW the row sits the
//    link/unlink option ("below it it should show the option to unlink any
//    list") — the round-102 standalone AniList section folds into this one.
//
//  • THE PREVIOUS LOGOS — "I liked the previous kind of logos which you had
//    with the bottom-up menu": every action row wears the round-101
//    DetailsActionSheet's ICON DISC — a 38dp tinted circle carrying a 19dp
//    glyph (the bare 20dp DropdownMenuItem icons are gone).
//
//  • "Open in Web View" — the label the user ordered (was "View in WebView").
//
//  • THE TRACKING STATE speaks through COLOR, not words: the text says only
//    "Tracking"; NOT connected = the greyed disc + a greyish row background +
//    greyish text; connected = a greenish tone on all three. (ROUND 103
//    WS-2 — the state legend lives on the row itself.)
//
//  THE SHARE SUBMENU keeps its round-102 behavior exactly (the user: "much
//  proper now… leave it as it is") — the same anchor, the same direct OS
//  chooser; only its rows inherit the disc language so the two pages of one
//  menu speak one visual language.
//
//  THEMING: composed INSIDE the details body's AdaptiveAccentTheme scope →
//  the dropdown (a Compose popup inheriting the ambient MaterialTheme) picks
//  up the per-content accent — the themed-menu behavior the user confirmed.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The three-dot anchored menu. [expanded] is the PARENT's open state (the
 * three-dots button toggles it); the Share page swap is INTERNAL so the
 * transition stays smooth at one anchor. Call [onDismissRequest] for any
 * full close (outside tap, back, after an action).
 *
 * [extensionSourceName] is the linked extension's display name (the RIGHT
 * data-source chip's text; falls back to "Extension" when unknown).
 */
@Composable
fun DetailsActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    contentTitle: String,
    // ── Data source section ──
    hasBothDataSources: Boolean,
    currentDataSourcePriority: DataSourcePriority,
    onSwitchDataSource: (DataSourcePriority) -> Unit,
    extensionSourceName: String? = null,
    // ── Actions ──
    onViewInWebView: () -> Unit,
    canViewInWebView: Boolean,
    // ── Tracking (ROUND 103 WS-2: the color-coded state) ──
    onOpenTracking: () -> Unit,
    isTracked: Boolean,
    // ── AniList link (extension entries only — folded INTO the data-source
    //    section, below the chips, per the round-103 order) ──
    isExtensionEntry: Boolean,
    isAniListLinked: Boolean,
    onLinkAniList: () -> Unit,
    onUnlinkAniList: () -> Unit,
    // ── Share: the targets, built on demand when the submenu opens ──
    buildShareLinks: () -> List<ShareLink>,
) {
    val context = LocalContext.current
    // The Share page swap — internal so both menus share ONE anchor Box.
    // Reset when fully closed so the next open starts on the main page.
    var sharePage by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (!expanded) sharePage = false
    }

    // ── The MAIN menu (hidden while the Share page shows) ──
    DropdownMenu(
        expanded = expanded && !sharePage,
        onDismissRequest = onDismissRequest,
        // ROUND 103: the softer panel — 20dp corners + a gentle tonal lift
        // (the crimped cards inside carry the structure).
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // ══ SECTION: DATA SOURCE — the chip row + the link/unlink row ══
            // (shows when there is a choice to make OR a list to link/unlink)
            if (hasBothDataSources || isExtensionEntry) {
                MenuSectionLabel("Data source")
                MenuSectionCard {
                    if (hasBothDataSources) {
                        // The round-101 segmented pair, side by side: LEFT =
                        // the list system's name (AniList today; future
                        // systems append), RIGHT = the extension's text.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MenuSourceChip(
                                label = "AniList",
                                selected = currentDataSourcePriority == DataSourcePriority.ANILIST,
                                onClick = {
                                    onDismissRequest()
                                    onSwitchDataSource(DataSourcePriority.ANILIST)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            MenuSourceChip(
                                label = extensionSourceName?.takeIf { it.isNotBlank() } ?: "Extension",
                                selected = currentDataSourcePriority == DataSourcePriority.EXTENSION,
                                onClick = {
                                    onDismissRequest()
                                    onSwitchDataSource(DataSourcePriority.EXTENSION)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // "below it it should show the option to unlink any
                    // list" — the link/unlink row lives under the chips
                    // (extension entries; the round-102 standalone AniList
                    // section is folded into here).
                    if (isExtensionEntry) {
                        if (isAniListLinked) {
                            MenuDiscRow(
                                icon = Icons.Filled.LinkOff,
                                label = "Unlink AniList",
                                destructive = true,
                                onClick = {
                                    onDismissRequest()
                                    onUnlinkAniList()
                                },
                            )
                        } else {
                            MenuDiscRow(
                                icon = Icons.Filled.Link,
                                label = "Link to AniList",
                                onClick = {
                                    onDismissRequest()
                                    onLinkAniList()
                                },
                            )
                        }
                    }
                }
            }

            // ══ SECTION: ACTIONS ══
            MenuSectionLabel("Actions")
            MenuSectionCard {
                MenuDiscRow(
                    icon = Icons.Filled.Share,
                    label = "Share",
                    onClick = { sharePage = true },
                )
                if (canViewInWebView) {
                    MenuDiscRow(
                        icon = Icons.Filled.Language,
                        // ROUND 103: the label the user ordered.
                        label = "Open in Web View",
                        onClick = {
                            onDismissRequest()
                            onViewInWebView()
                        },
                    )
                }
            }

            // ══ SECTION: TRACKING — the state speaks through color ══
            MenuSectionLabel("Tracking")
            MenuSectionCard {
                MenuTrackingRow(
                    isTracked = isTracked,
                    onClick = {
                        onDismissRequest()
                        onOpenTracking()
                    },
                )
            }
        }
    }

    // ── The SHARE submenu — the same anchor, the three targets, the OS
    // chooser fired DIRECTLY on pick (no sheet, no copy button). Behavior
    // unchanged from round 102 (the user approved it); only the rows wear
    // the disc language now. ──
    DropdownMenu(
        expanded = expanded && sharePage,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            MenuSectionLabel("Share via")
            // Built on open (not per recomposition of the whole page): the
            // links reflect the content's CURRENT state at the moment of
            // sharing.
            val links = remember(sharePage, expanded) { buildShareLinks() }
            MenuSectionCard {
                if (links.isEmpty()) {
                    Text(
                        text = "Nothing to share for this content yet",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                } else {
                    links.forEach { link ->
                        // The round-102 icon mapping, unchanged (the user
                        // approved this submenu's content).
                        val icon = when (link.kind) {
                            ShareTargetKind.EXTENSION_URL -> Icons.Filled.Language
                            ShareTargetKind.DATA_SOURCE -> Icons.Filled.Star
                            ShareTargetKind.APP_DEEP_LINK -> Icons.Filled.AppShortcut
                        }
                        MenuDiscRow(
                            icon = icon,
                            label = link.label,
                            onClick = {
                                onDismissRequest()
                                fireOsShareChooser(context, link, contentTitle)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * WS-D: hand the picked link straight to the DEVICE's share sheet —
 * `Intent.ACTION_SEND` wrapped in `createChooser`, with "title — url" as
 * the payload (the same text shape the round-101 sheet shared). Called from
 * a click handler with the composition's context (the activity is alive and
 * foregrounded at that moment).
 */
private fun fireOsShareChooser(
    context: android.content.Context,
    link: ShareLink,
    contentTitle: String,
) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            if (contentTitle.isBlank()) link.url else "${contentTitle} — ${link.url}",
        )
    }
    context.startActivity(Intent.createChooser(send, "Share via"))
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103: the crimped-menu vocabulary — labels, cards, chips, discs
// ════════════════════════════════════════════════════════════════════════════

/** A small all-caps section label ABOVE each crimped card (the grouping rhythm). */
@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 4.dp, bottom = 1.dp),
    )
}

/**
 * ONE crimped section — "crimp the menu a bit where the separations are":
 * an inset rounded card on a quiet surfaceVariant tint, holding its rows;
 * the gaps BETWEEN the cards are the menu's separations.
 */
@Composable
private fun MenuSectionCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

/**
 * ROUND 103 (WS-1): one data-source chip of the side-by-side pair — the
 * round-101 sheet's segmented look exactly (10dp corners; selected = the
 * filled primary + onPrimary ExtraBold; unselected = the quiet tint +
 * onSurfaceVariant Medium). The selection IS the fill — no checkmark.
 */
@Composable
private fun MenuSourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
    }
}

/**
 * ROUND 103 (WS-1): the action row wearing the PREVIOUS logos — the
 * round-101 DetailsActionSheet's icon DISC: a 38dp tinted circle carrying a
 * 19dp glyph, then the label (14sp Bold — label-only, no descriptions).
 * [destructive] shifts the disc to the error language (Unlink).
 */
@Composable
private fun MenuDiscRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * ROUND 103 (WS-2): the Tracking row — the text says ONLY "Tracking"; the
 * STATE speaks through color, exactly per the device round:
 *  • NOT connected — "the icon's color will be greyed out, and also the
 *    background of it will be a little bit kind of a grayish tone… the text
 *    will be a little bit grayish too."
 *  • Connected — "a little bit kind of a greenish tone" on the background,
 *    the icon and the text.
 * The row is crimped INSIDE its section card (the 6dp inset + its own
 * rounded tint) so the state tone reads as the row's own surface.
 */
@Composable
private fun MenuTrackingRow(
    isTracked: Boolean,
    onClick: () -> Unit,
) {
    val trackingGreen = if (isSystemInDarkTheme()) SuccessDark else SuccessLight
    val rowTone = if (isTracked) {
        trackingGreen.copy(alpha = 0.13f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val tint = if (isTracked) trackingGreen
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(rowTone)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(38.dp)
                .background(tint.copy(alpha = 0.14f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.Filled.TrackChanges,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Tracking",
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = if (isTracked) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
