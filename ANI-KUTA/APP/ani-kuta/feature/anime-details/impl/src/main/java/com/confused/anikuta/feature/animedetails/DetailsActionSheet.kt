package com.confused.anikuta.feature.animedetails

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.model.DataSourcePriority
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.share.ShareLink
import com.confused.anikuta.core.share.ShareTargetKind

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 101 (WS-C): DetailsActionSheet — the three-dot menu redesign
// ════════════════════════════════════════════════════════════════════════════
//
//  The user's spec: the old text-only DropdownMenu's UI was "not that good…
//  not that much properly handled". This is its replacement — the app's
//  standard action surface (a ModalBottomSheet, the same shape every other
//  details-page sheet uses) with icon rows grouped into sections:
//
//    DATA SOURCE  — the AniList | Extension segmented toggle (linked entries;
//                   the D-134 selector, same behavior, better home)
//    ACTIONS      — Refresh · Share… · View in WebView
//    TRACKING     — the TrackSheet entry (D-242)
//    LINK         — Link to AniList / Unlink AniList (extension entries)
//
//  Every action that existed before keeps its EXACT behavior — this is a UI
//  container swap, not a semantics change. Share… opens [ShareContentSheet]
//  (the :core:share system's UI); View in WebView launches the app's internal
//  WebView with the content's absolute page URL (D-209 activity, works for
//  BOTH aniyomi and CloudStream-bridged sources — they share the
//  AnimeCatalogueSource baseUrl contract).
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsActionSheet(
    onDismiss: () -> Unit,
    contentTitle: String,
    // ── Data source section (rendered only when BOTH sources are linked) ──
    hasBothDataSources: Boolean = false,
    currentDataSourcePriority: DataSourcePriority = DataSourcePriority.EXTENSION,
    onSwitchDataSource: (DataSourcePriority) -> Unit = {},
    // ── Actions ──
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onViewInWebView: () -> Unit,
    // The WebView row hides when no source-linked page URL exists.
    canViewInWebView: Boolean = false,
    // ── Tracking ──
    onOpenTracking: () -> Unit,
    // ── AniList link section (extension entries only) ──
    isExtensionEntry: Boolean = false,
    isAniListLinked: Boolean = false,
    onLinkAniList: () -> Unit = {},
    onUnlinkAniList: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.75f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            // ── Title ──
            Text(
                text = "More options",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = contentTitle,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )

            // ── Data source section (D-134 selector, sheet form) ──
            if (hasBothDataSources) {
                SheetSectionLabel("Data source", Modifier.padding(top = 18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        DataSourcePriority.ANILIST to "AniList",
                        DataSourcePriority.EXTENSION to "Extension",
                    ).forEach { (priority, label) ->
                        val isSelected = currentDataSourcePriority == priority
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSwitchDataSource(priority)
                                    onDismiss()
                                },
                        ) {
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            }

            // ── Actions ──
            SheetSectionLabel("Actions", Modifier.padding(top = 18.dp))
            ActionSheetRow(
                icon = Icons.Filled.Refresh,
                label = "Refresh",
                description = "Reload details, episodes and tracking",
                onClick = {
                    onDismiss()
                    onRefresh()
                },
                modifier = Modifier.padding(top = 6.dp),
            )
            ActionSheetRow(
                icon = Icons.Filled.Share,
                label = "Share",
                description = "Extension link, AniList, or an ANI-KUTA link",
                onClick = {
                    onDismiss()
                    onShare()
                },
                modifier = Modifier.padding(top = 4.dp),
            )
            if (canViewInWebView) {
                ActionSheetRow(
                    icon = Icons.Filled.Language,
                    label = "View in WebView",
                    description = "Open this content's page inside the app",
                    onClick = {
                        onDismiss()
                        onViewInWebView()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // ── Tracking ──
            SheetSectionLabel("Tracking", Modifier.padding(top = 18.dp))
            ActionSheetRow(
                icon = Icons.Filled.TrackChanges,
                label = "Tracking",
                description = "AniList status, progress and score",
                onClick = {
                    onDismiss()
                    onOpenTracking()
                },
                modifier = Modifier.padding(top = 6.dp),
            )

            // ── AniList link (extension entries only — same rule as the old menu) ──
            if (isExtensionEntry) {
                SheetSectionLabel("AniList", Modifier.padding(top = 18.dp))
                if (isAniListLinked) {
                    ActionSheetRow(
                        icon = Icons.Filled.LinkOff,
                        label = "Unlink AniList",
                        description = "Keep extension data only — auto-link stays off",
                        isDestructive = true,
                        onClick = {
                            onDismiss()
                            onUnlinkAniList()
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    ActionSheetRow(
                        icon = Icons.Filled.Link,
                        label = "Link to AniList",
                        description = "Search AniList and pick the matching entry",
                        onClick = {
                            onDismiss()
                            onLinkAniList()
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** A small all-caps section label — the sheet's grouping rhythm. */
@Composable
private fun SheetSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontFamily = RobotoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/**
 * One icon row of the action sheet: a tinted icon disc + label/description
 * column, the whole row tappable. [isDestructive] shifts the icon tint to
 * error (used by Unlink — matching the app's destructive-action language).
 */
@Composable
private fun ActionSheetRow(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = if (isDestructive) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        },
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        fontFamily = RobotoFamily,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 101 (WS-C): ShareContentSheet — the share system's UI
// ════════════════════════════════════════════════════════════════════════════
//
//  One row per :core:share ShareLink, in the factory's order:
//    1. the extension's page URL (opens the site in any browser)
//    2. each data-source provider page (AniList today, future providers
//       append via DataSourceLink without touching this sheet)
//    3. ANI-KUTA's own deep link (opens the app straight on the content —
//       v1 is deliberately limited to exactly this behavior)
//
//  Tapping a row fires the OS share chooser with "title — url"; the trailing
//  copy button puts the bare link on the clipboard instead.
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareContentSheet(
    onDismiss: () -> Unit,
    contentTitle: String,
    links: List<ShareLink>,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            Text(
                text = "Share",
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = contentTitle,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            links.forEach { link ->
                ShareLinkRow(
                    link = link,
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                if (contentTitle.isBlank()) link.url else "${contentTitle} — ${link.url}",
                            )
                        }
                        context.startActivity(Intent.createChooser(send, "Share via"))
                    },
                    onCopy = {
                        clipboard.setText(AnnotatedString(link.url))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (links.isEmpty()) {
                Text(
                    text = "Nothing to share for this content yet.",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

/** One share-target row: kind icon + label/description + the copy action. */
@Composable
private fun ShareLinkRow(
    link: ShareLink,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon, tint) = when (link.kind) {
        ShareTargetKind.EXTENSION_URL -> Icons.Filled.Language to MaterialTheme.colorScheme.primary
        ShareTargetKind.DATA_SOURCE -> Icons.Filled.Star to MaterialTheme.colorScheme.primary
        ShareTargetKind.APP_DEEP_LINK -> Icons.Filled.AppShortcut to MaterialTheme.colorScheme.primary
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onShare),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.label,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = link.url,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Copy — the quiet alternative to the OS chooser.
            Surface(
                color = Color.Transparent,
                shape = CircleShape,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCopy),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy link",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
