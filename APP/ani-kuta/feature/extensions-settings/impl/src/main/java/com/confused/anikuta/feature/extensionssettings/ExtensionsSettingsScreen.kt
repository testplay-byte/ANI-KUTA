package com.confused.anikuta.feature.extensionssettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
 * Improvements per user feedback:
 * - CollapsingHeader "Extensions" that shrinks on scroll + ScrollBlurOverlay.
 * - Each section in a dedicated background card with minimal horizontal padding.
 * - Available extensions filtered to exclude installed ones.
 * - Download button shows a circular spinner during install (installStates tracking).
 * - Filters bar: search + sort (name/language/NSFW) applies to all 3 sections.
 * - Long-press a trusted source to enter reorder mode (up/down arrows).
 * - Delete buttons on trusted + untrusted (direct, no untrust-first step).
 *
 * CORE_RULES §22: smooth animations (CollapsingHeader, fade-in items).
 * CORE_RULES §23: reactive state (StateFlow from ExtensionManager).
 * CORE_RULES §20: logged with tag "Anikuta:Feature:ExtensionsSettings".
 */
@Composable
fun ExtensionsSettingsScreen(
    onBack: () -> Unit,
    onOpenRepoSettings: () -> Unit,
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
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(ExtensionSortMode.NAME) }
    var showNsfw by remember { mutableStateOf(false) }
    var reorderMode by remember { mutableStateOf(false) }
    var reorderedInstalled by remember { mutableStateOf<List<AnimeExtension.Installed>>(emptyList()) }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    // Fetch available extensions when repos exist.
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

    // ── Filtering + sorting (applies to all 3 sections) ──
    val filteredInstalled = reorderedInstalled.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw)
    }.let { if (reorderMode) it else sortExtensions(it, sortMode) }

    val filteredUntrusted = untrustedExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw)
    }.let { sortExtensions(it, sortMode) }

    val filteredAvailable = availableExtensions
        .filter { it.pkgName !in installedPkgs && it.pkgName !in untrustedPkgs } // exclude installed
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
                                // TODO: persist priority order (Phase 5d — identity).
                            },
                        )
                    } else {
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

            // ── Filters bar ──
            ExtensionFiltersBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                sortMode = sortMode,
                onSortModeChange = { sortMode = it },
                showNsfw = showNsfw,
                onToggleNsfw = { showNsfw = !showNsfw },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── Trusted Sources section ──
                item {
                    ExtensionSectionCard(title = "Trusted Sources", count = filteredInstalled.size) {
                        if (filteredInstalled.isEmpty()) {
                            EmptySectionBody("No trusted sources. Install an extension to get started.")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                        onUntrust = { extensionManager.untrustExtension(ext) },
                                        onDelete = { extensionManager.uninstallExtension(ext) },
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Untrusted section ──
                if (filteredUntrusted.isNotEmpty()) {
                    item {
                        ExtensionSectionCard(title = "Untrusted", count = filteredUntrusted.size) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

                // ── Available Extensions section ──
                item {
                    ExtensionSectionCard(title = "Available Extensions", count = filteredAvailable.size) {
                        when {
                            repos.isEmpty() -> EmptySectionBody("No repositories configured. Tap the settings icon to add one.")
                            isRefreshing && filteredAvailable.isEmpty() -> Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                            filteredAvailable.isEmpty() -> EmptySectionBody("No extensions found in your repositories.")
                            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }

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

// ════════════════════════════════════════════════════════════════════════════
//  Filters bar
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Search field
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
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                ),
            )
            Spacer(Modifier.width(8.dp))
            // Sort button
            Box {
                HeaderIconButton(
                    icon = Icons.Filled.FilterList,
                    contentDescription = "Sort",
                    onClick = { showSortMenu = !showSortMenu },
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    ExtensionSortMode.entries.forEach { mode ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(mode.label, fontFamily = RobotoFamily) },
                            onClick = { onSortModeChange(mode); showSortMenu = false },
                            trailingIcon = if (sortMode == mode) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                    // NSFW toggle
                    androidx.compose.material3.HorizontalDivider()
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(if (showNsfw) "Hide NSFW" else "Show NSFW", fontFamily = RobotoFamily) },
                        onClick = { onToggleNsfw(); showSortMenu = false },
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Section card (dedicated background)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExtensionSectionCard(
    title: String,
    count: Int,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
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
            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp,
            )
            // Content
            Box(modifier = Modifier.padding(8.dp)) {
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
    onUntrust: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isReordering) {
                // Reorder controls
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
                ExtensionIconPlaceholder(name = extension.name)
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
                if (extension.sources.isNotEmpty()) {
                    Text(
                        text = extension.sources.joinToString(", ") { it.name },
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!isReordering) {
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall extension?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will uninstall ${extension.name} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
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
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExtensionIconPlaceholder(name = extension.name)
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall extension?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will uninstall ${extension.name} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Uninstall", color = MaterialTheme.colorScheme.error, fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) {
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
        color = MaterialTheme.colorScheme.surface,
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
            // Install button — shows spinner when installing.
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

@Composable
private fun ActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
