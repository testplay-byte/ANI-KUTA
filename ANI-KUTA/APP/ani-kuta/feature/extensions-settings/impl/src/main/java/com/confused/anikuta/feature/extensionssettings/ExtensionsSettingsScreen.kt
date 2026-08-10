package com.confused.anikuta.feature.extensionssettings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.extension.installer.InstallStep
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.model.AnimeExtension
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Extensions Settings screen — lists installed, untrusted, and available extensions.
 *
 * Three dedicated sections, each in its own card with a distinct background:
 * 1. Trusted Sources — installed + trusted extensions (long-press to reorder).
 * 2. Untrusted — installed but not yet trusted (trust / delete buttons).
 * 3. Available Extensions — listed in repos, not yet installed (install button
 *    with spinner animation during install).
 *
 * UI design (per user spec):
 * - CollapsingHeader "Extensions" that shrinks on scroll + ScrollBlurOverlay.
 * - Filters button at the top-right (NO default search bar). Tapping it reveals
 *   the search + sort bar.
 * - Each section in a dedicated background card with clear separation + spacing
 *   between rows.
 * - Available extensions filtered to exclude installed/untrusted.
 * - Download button shows a circular spinner during install.
 * - Trusted sources: long-press enters reorder mode (up/down arrows).
 * - Delete buttons on trusted + untrusted (with confirmation dialog).
 * - Extension icons shown via Coil AsyncImage (Drawable for installed/untrusted,
 *   URL for available).
 *
 * CORE_RULES §22: smooth animations (CollapsingHeader, fade-in items).
 * CORE_RULES §23: reactive state (StateFlow from ExtensionManager).
 * CORE_RULES §20: logged with tag "Anikuta:Feature:ExtensionsSettings".
 */
@Composable
fun ExtensionsSettingsScreen(
    onBack: () -> Unit,
    onOpenRepoSettings: () -> Unit,
    onOpenExtensionDetail: (String) -> Unit = {},
    extensionManager: ExtensionManager = koinInject(),
    repoRepository: ExtensionRepoRepository = koinInject(),
) {
    val installedExtensions by extensionManager.installedExtensions.collectAsState()
    val untrustedExtensions by extensionManager.untrustedExtensions.collectAsState()
    val availableExtensions by extensionManager.availableExtensions.collectAsState()
    val repos by repoRepository.repos.collectAsState()
    val installStates by extensionManager.installStates.collectAsState()

    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(ExtensionSortMode.NAME) }
    var showNsfw by remember { mutableStateOf(true) }
    var reorderMode by remember { mutableStateOf(false) }
    var reorderedInstalled by remember { mutableStateOf<List<AnimeExtension.Installed>>(emptyList()) }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    // Fetch available extensions when repos exist (auto-refresh on repo changes).
    LaunchedEffect(repos.size) {
        if (repos.isNotEmpty()) {
            isRefreshing = true
            extensionManager.findAvailableExtensions()
            isRefreshing = false
        }
    }

    // Keep reorderedInstalled in sync with installedExtensions (when not reordering).
    LaunchedEffect(installedExtensions) {
        if (!reorderMode) reorderedInstalled = installedExtensions
    }

    val installedPkgs = installedExtensions.map { it.pkgName }.toSet()
    val untrustedPkgs = untrustedExtensions.map { it.pkgName }.toSet()

    // ── Filtering + sorting ──
    val filteredInstalled = reorderedInstalled.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw)
    }.let { if (reorderMode) it else sortExtensions(it, sortMode) }
        // Phase 2d: disabled extensions sorted to the bottom (enabled first).
        .let { sorted -> if (reorderMode) sorted else sorted.sortedBy { !it.isEnabled } }

    val filteredUntrusted = untrustedExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw)
    }.let { sortExtensions(it, sortMode) }

    val filteredAvailable = availableExtensions
        .filter { it.pkgName !in installedPkgs && it.pkgName !in untrustedPkgs }
        .filter { ext ->
            matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw)
        }
        .let { sortExtensions(it, sortMode) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Extensions",
                collapsed = collapsed,
                actions = {
                    if (reorderMode) {
                        HeaderIconButton(
                            icon = Icons.Filled.Check,
                            contentDescription = "Done reordering",
                            onClick = {
                                reorderMode = false
                                // TODO: persist priority order (Phase 5d).
                            },
                        )
                    } else {
                        HeaderIconButton(
                            icon = Icons.Filled.FilterList,
                            contentDescription = "Filters",
                            onClick = { showFilters = !showFilters },
                        )
                        HeaderIconButton(
                            icon = Icons.Filled.Settings,
                            contentDescription = "Repository settings",
                            onClick = onOpenRepoSettings,
                        )
                    }
                    HeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                },
            )

            // ── Filters bar (hidden by default, revealed on tap) ──
            AnimatedVisibility(
                visible = showFilters,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                ExtensionFiltersBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    showNsfw = showNsfw,
                    onToggleNsfw = { showNsfw = !showNsfw },
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ── Trusted Sources ──
                    item {
                        ExtensionSectionCard(title = "Trusted Sources", count = filteredInstalled.size) {
                            if (filteredInstalled.isEmpty()) {
                                EmptySectionBody("No trusted sources. Install an extension to get started.")
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    filteredInstalled.forEachIndexed { index, ext ->
                                        InstalledExtensionRow(
                                        extension = ext,
                                        isReordering = reorderMode,
                                        canMoveUp = reorderMode && index > 0,
                                        canMoveDown = reorderMode && index < filteredInstalled.lastIndex,
                                        onMoveUp = {
                                            reorderedInstalled = reorderedInstalled.toMutableList().apply {
                                                val i = indexOf(ext)
                                                if (i > 0) {
                                                    val tmp = this[i - 1]; this[i - 1] = this[i]; this[i] = tmp
                                                }
                                            }
                                        },
                                        onMoveDown = {
                                            reorderedInstalled = reorderedInstalled.toMutableList().apply {
                                                val i = indexOf(ext)
                                                if (i < lastIndex) {
                                                    val tmp = this[i + 1]; this[i + 1] = this[i]; this[i] = tmp
                                                }
                                            }
                                        },
                                        onLongPress = { reorderMode = true },
                                        onClickExtension = { onOpenExtensionDetail(ext.pkgName) },
                                        onToggleEnabled = {
                                            if (ext.isEnabled) extensionManager.disableExtension(ext.pkgName)
                                            else extensionManager.enableExtension(ext.pkgName)
                                        },
                                        onUntrust = { extensionManager.untrustExtension(ext) },
                                        onDelete = { extensionManager.uninstallExtension(ext) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Untrusted ──
                if (filteredUntrusted.isNotEmpty()) {
                    item {
                        ExtensionSectionCard(title = "Untrusted", count = filteredUntrusted.size) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                filteredUntrusted.forEach { ext ->
                                    UntrustedExtensionRow(
                                        extension = ext,
                                        onTrust = { extensionManager.trustExtension(ext) },
                                        onDelete = { extensionManager.uninstallExtension(ext) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Available Extensions ──
                item {
                    ExtensionSectionCard(title = "Available Extensions", count = filteredAvailable.size) {
                        when {
                            repos.isEmpty() -> EmptySectionBody("No repositories configured. Tap the settings icon to add one.")
                            isRefreshing && filteredAvailable.isEmpty() -> Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                            filteredAvailable.isEmpty() -> EmptySectionBody("No extensions found in your repositories.")
                            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                filteredAvailable.forEach { ext ->
                                    val installStep = installStates[ext.pkgName]
                                    AvailableExtensionRow(
                                        extension = ext,
                                        installStep = installStep,
                                        onInstall = {
                                            scope.launch {
                                                extensionManager.installExtension(ext).collectLatest { }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                }

                // Phase 3: scroll blur overlay inside the Box (below the header, on top of the list).
                ScrollBlurOverlay(
                    scrollOffset = {
                        if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else listState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Filters bar (revealed when user taps the filter button)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExtensionFiltersBar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortMode: ExtensionSortMode,
    onSortModeChange: (ExtensionSortMode) -> Unit,
    showNsfw: Boolean,
    onToggleNsfw: () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search extensions", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape).clickable { onQueryChange("") },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp)) }
                    }
                } else null,
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = RobotoFamily, fontSize = 13.sp),
            )
            Spacer(Modifier.width(8.dp))
            Box {
                HeaderIconButton(
                    icon = Icons.Filled.FilterList,
                    contentDescription = "Sort",
                    onClick = { showSortMenu = !showSortMenu },
                )
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    ExtensionSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label, fontFamily = RobotoFamily) },
                            onClick = { onSortModeChange(mode); showSortMenu = false },
                            trailingIcon = if (sortMode == mode) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (showNsfw) "Hide NSFW" else "Show NSFW", fontFamily = RobotoFamily) },
                        onClick = { onToggleNsfw(); showSortMenu = false },
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Section card (dedicated background with clear separation)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExtensionSectionCard(
    title: String,
    count: Int,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "($count)",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp,
            )
            Box(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Row composables
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun InstalledExtensionRow(
    extension: AnimeExtension.Installed,
    isReordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onLongPress: () -> Unit,
    onClickExtension: () -> Unit,
    onToggleEnabled: () -> Unit,
    onUntrust: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (extension.isEnabled) 1f else 0.45f }
            .combinedClickable(
                onClick = onClickExtension,
                onLongClick = onLongPress,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isReordering) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ActionIconButton(
                        icon = Icons.Filled.ArrowUpward,
                        contentDescription = "Move up",
                        onClick = onMoveUp,
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                        enabled = canMoveUp,
                    )
                    ActionIconButton(
                        icon = Icons.Filled.ArrowDownward,
                        contentDescription = "Move down",
                        onClick = onMoveDown,
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent,
                        enabled = canMoveDown,
                    )
                }
                Spacer(Modifier.width(8.dp))
            } else {
                ExtensionIcon(extension.icon, extension.name)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = buildString {
                        append("v${extension.versionName}")
                        extension.lang?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                        if (extension.hasUpdate) append(" · Update available")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!isReordering) {
                // Phase 2c: enable/disable toggle removed from list — moved to detail page.
                ActionIconButton(
                    icon = Icons.Filled.VerifiedUser,
                    contentDescription = "Untrust",
                    onClick = onUntrust,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    onClick = { showDeleteConfirm = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall extension?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will uninstall ${extension.name} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

@Composable
private fun UntrustedExtensionRow(
    extension: AnimeExtension.Untrusted,
    onTrust: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtensionIcon(extension.icon, extension.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "Untrusted · v${extension.versionName}",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ActionIconButton(
                icon = Icons.Filled.VerifiedUser,
                contentDescription = "Trust",
                onClick = onTrust,
                tint = MaterialTheme.colorScheme.primary,
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Delete",
                onClick = { showDeleteConfirm = true },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall extension?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will uninstall ${extension.name} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

@Composable
private fun AvailableExtensionRow(
    extension: AnimeExtension.Available,
    installStep: InstallStep?,
    onInstall: () -> Unit,
) {
    val isInstalling = installStep != null && !installStep.isCompleted()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = extension.name,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = buildString {
                        append("v${extension.versionName}")
                        extension.lang?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (isInstalling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                ActionIconButton(
                    icon = Icons.Filled.Download,
                    contentDescription = "Install",
                    onClick = onInstall,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Extension icon (Drawable for installed/untrusted, URL for available)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExtensionIcon(icon: Drawable?, fallbackName: String) {
    if (icon != null) {
        // Coil's AsyncImage accepts a Drawable as the model.
        AsyncImage(
            model = icon,
            contentDescription = fallbackName,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        ExtensionIconPlaceholder(fallbackName)
    }
}

@Composable
private fun ExtensionIconPlaceholder(name: String) {
    val firstLetter = name.firstOrNull()?.uppercase() ?: "?"
    val colors = listOf(
        Color(0xFFB1F256), Color(0xFF7CC8FA), Color(0xFFFF8A65),
        Color(0xFFE57C9F), Color(0xFFFFB300),
    )
    val color = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = firstLetter,
                fontFamily = RobotoFamily,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Shared UI helpers
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptySectionBody(message: String) {
    Text(
        text = message,
        fontFamily = RobotoFamily,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    enabled: Boolean = true,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(150),
        label = "actionAlpha",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = alpha),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Filtering + sorting helpers ──

private fun matchesSearch(name: String, query: String): Boolean =
    query.isBlank() || name.contains(query, ignoreCase = true)

private enum class ExtensionSortMode(val label: String) {
    NAME("Sort by name"),
    LANGUAGE("Sort by language"),
    NSFW("NSFW first"),
}

private fun <T : AnimeExtension> sortExtensions(list: List<T>, mode: ExtensionSortMode): List<T> = when (mode) {
    ExtensionSortMode.NAME -> list.sortedBy { it.name.lowercase() }
    ExtensionSortMode.LANGUAGE -> list.sortedBy { (it.lang ?: "zz").lowercase() }
    ExtensionSortMode.NSFW -> list.sortedByDescending { it.isNsfw }
}
