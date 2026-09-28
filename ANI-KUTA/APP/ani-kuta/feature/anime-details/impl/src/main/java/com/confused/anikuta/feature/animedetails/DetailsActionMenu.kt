package com.confused.anikuta.feature.animedetails

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.model.DataSourcePriority
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.share.ShareLink
import com.confused.anikuta.core.share.ShareTargetKind

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 102 (WS-B + WS-D): DetailsActionMenu — the three-dot menu as an
//  ANCHORED DROPDOWN, replacing the round-101 bottom sheet.
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.58 device round's spec:
//  • "A menu which would smoothly appear from the three dots buttons… just
//    like how it was previously implemented but in a better UI" — an M3
//    DropdownMenu composed in a Box AROUND the three-dots button anchors and
//    animates exactly there (scale + fade popup). A dropdown has NO grab
//    area at all, so the hidden-grab-area rule every bottom sheet follows is
//    satisfied by construction.
//  • "Keep them [the icons] with the menu" — every row carries a leading
//    icon (the sheet's icon language, kept).
//  • "Remove the descriptions for each one of them" — label-only rows.
//  • "There is no need to show the refresh button" — REMOVED; the data-source
//    switch now auto-refreshes the target axis (the round-102 WS-C contract)
//    and pull-to-refresh remains for everything else.
//
//  THE SHARE SUBMENU (WS-D): tapping Share swaps this menu for the share
//  targets at the SAME anchor — "there should only be the share option the
//    user can pick from, and it will automatically open up the share options
//  of the device itself": no bottom sheet, no per-row descriptions, no copy
//  button. Picking a target fires the OS chooser with "title — url".
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
 */
@Composable
fun DetailsActionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    contentTitle: String,
    // ── Data source section (rendered only when BOTH sources are linked) ──
    hasBothDataSources: Boolean,
    currentDataSourcePriority: DataSourcePriority,
    onSwitchDataSource: (DataSourcePriority) -> Unit,
    // ── Actions ──
    onViewInWebView: () -> Unit,
    canViewInWebView: Boolean,
    // ── Tracking (ROUND 102 WS-E: the two-state label) ──
    onOpenTracking: () -> Unit,
    isTracked: Boolean,
    // ── AniList link (extension entries only) ──
    isExtensionEntry: Boolean,
    isAniListLinked: Boolean,
    onLinkAniList: () -> Unit,
    onUnlinkAniList: () -> Unit,
    // ── Share (WS-D): the targets, built on demand when the submenu opens ──
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
    ) {
        if (hasBothDataSources) {
            MenuSectionLabel("Data source")
            listOf(
                DataSourcePriority.ANILIST to "AniList",
                DataSourcePriority.EXTENSION to "Extension",
            ).forEach { (priority, label) ->
                DropdownMenuItem(
                    text = {
                        MenuLabel(
                            label,
                            emphasize = currentDataSourcePriority == priority,
                        )
                    },
                    leadingIcon = { MenuLeadingIcon(Icons.Filled.Star.takeIf { priority == DataSourcePriority.ANILIST } ?: Icons.Filled.Language) },
                    trailingIcon = {
                        if (currentDataSourcePriority == priority) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = {
                        onDismissRequest()
                        onSwitchDataSource(priority)
                    },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }

        DropdownMenuItem(
            text = { MenuLabel("Share") },
            leadingIcon = { MenuLeadingIcon(Icons.Filled.Share) },
            onClick = { sharePage = true },
        )
        if (canViewInWebView) {
            DropdownMenuItem(
                text = { MenuLabel("View in WebView") },
                leadingIcon = { MenuLeadingIcon(Icons.Filled.Language) },
                onClick = {
                    onDismissRequest()
                    onViewInWebView()
                },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        // ROUND 102 (WS-E): the tracking entry carries its STATE — "Tracking
        // now" vs "Not tracking" — so the user sees the tracking contract's
        // answer before opening the sheet.
        DropdownMenuItem(
            text = { MenuLabel(if (isTracked) "Tracking now" else "Not tracking") },
            leadingIcon = { MenuLeadingIcon(Icons.Filled.TrackChanges) },
            onClick = {
                onDismissRequest()
                onOpenTracking()
            },
        )

        if (isExtensionEntry) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            if (isAniListLinked) {
                DropdownMenuItem(
                    text = { MenuLabel("Unlink AniList", destructive = true) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.LinkOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                    ),
                    onClick = {
                        onDismissRequest()
                        onUnlinkAniList()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { MenuLabel("Link to AniList") },
                    leadingIcon = { MenuLeadingIcon(Icons.Filled.Link) },
                    onClick = {
                        onDismissRequest()
                        onLinkAniList()
                    },
                )
            }
        }
    }

    // ── The SHARE submenu — the same anchor, the three targets, the OS
    // chooser fired DIRECTLY on pick (no sheet, no copy button). ──
    DropdownMenu(
        expanded = expanded && sharePage,
        onDismissRequest = onDismissRequest,
    ) {
        MenuSectionLabel("Share via")
        // Built on open (not per recomposition of the whole page): the links
        // reflect the content's CURRENT state at the moment of sharing.
        val links = remember(sharePage, expanded) { buildShareLinks() }
        if (links.isEmpty()) {
            DropdownMenuItem(
                text = {
                    MenuLabel("Nothing to share for this content yet")
                },
                enabled = false,
                onClick = {},
            )
        } else {
            links.forEach { link ->
                val (icon, tint) = when (link.kind) {
                    ShareTargetKind.EXTENSION_URL -> Icons.Filled.Language to MaterialTheme.colorScheme.primary
                    ShareTargetKind.DATA_SOURCE -> Icons.Filled.Star to MaterialTheme.colorScheme.primary
                    ShareTargetKind.APP_DEEP_LINK -> Icons.Filled.AppShortcut to MaterialTheme.colorScheme.primary
                }
                DropdownMenuItem(
                    text = { MenuLabel(link.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    onClick = {
                        onDismissRequest()
                        fireOsShareChooser(context, link, contentTitle)
                    },
                )
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

/** A small all-caps section label INSIDE the dropdown (the grouping rhythm). */
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
            .padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** One row's label — label-only per the round-102 order (no descriptions). */
@Composable
private fun MenuLabel(
    text: String,
    emphasize: Boolean = false,
    destructive: Boolean = false,
) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.5.sp,
        fontWeight = if (emphasize) FontWeight.ExtraBold else FontWeight.Medium,
        color = when {
            destructive -> MaterialTheme.colorScheme.error
            emphasize -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
    )
}

/** The standard leading icon of a menu row (20dp, primary-tinted). */
@Composable
private fun MenuLeadingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp),
    )
}
