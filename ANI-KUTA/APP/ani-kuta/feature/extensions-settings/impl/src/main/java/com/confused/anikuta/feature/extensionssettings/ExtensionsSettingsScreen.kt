package com.confused.anikuta.feature.extensionssettings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
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
import com.confused.anikuta.core.providerapi.InstallStep
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
    csManager: com.confused.anikuta.data.cloudstream.CloudstreamPluginManager = koinInject(),
    csRepoRepository: com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository = koinInject(),
) {
    val installedExtensions by extensionManager.installedExtensions.collectAsState()
    val untrustedExtensions by extensionManager.untrustedExtensions.collectAsState()
    val erroredExtensions by extensionManager.erroredExtensions.collectAsState()
    val availableExtensions by extensionManager.availableExtensions.collectAsState()
    val repos by repoRepository.repos.collectAsState()
    val installStates by extensionManager.installStates.collectAsState()
    val updateCheckState by extensionManager.updateCheckState.collectAsState()

    // ── Task 41: the unified source tabs (doc 23 §5.4, the user's G3-adjacent gate). ──
    // The CloudStream tab appears once that system has content (saved repos or
    // installed plugins); the aniyomi tab is the built-in default. When only one
    // system has content the tab row is hidden — nothing to switch between.
    // Session 2: installed plugins count as content EVEN with zero repos —
    // deleting a repository no longer cascades to its plugins, so the tab (and
    // its Trusted Sources section) survives as long as one plugin is installed.
    val csInstalled by csManager.installed.collectAsState()
    val csErrored by csManager.errored.collectAsState()
    val csAvailable by csManager.available.collectAsState()
    val csRepos by csRepoRepository.repos.collectAsState()
    val csHasContent = csRepos.isNotEmpty() || csInstalled.isNotEmpty() || csErrored.isNotEmpty()
    var activeTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("aniyomi") }
    val showCloudstreamTab = activeTab == "cloudstream" && csHasContent

    val scope = rememberCoroutineScope()
    var showFilters by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(ExtensionSortMode.NAME) }
    var showNsfw by remember { mutableStateOf(true) }

    // Session 2: ONE filters bar drives BOTH tabs. The aniyomi NSFW state stays
    // session-local (default on, unchanged behavior); the CloudStream NSFW
    // state is the persisted G4 gate (default OFF). Each tab reads whichever
    // toggle is active — one shared control, two independent settings.
    val appPreferences = koinInject<com.confused.anikuta.core.preferences.AppPreferences>()
    var csShowNsfw by remember { mutableStateOf(appPreferences.cloudstreamShowNsfw) }

    var langFilter by remember { mutableStateOf<String?>(null) }
    var reorderMode by remember { mutableStateOf(false) }
    var reorderedInstalled by remember { mutableStateOf<List<AnimeExtension.Installed>>(emptyList()) }

    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20

    // D-301: auto update-check when the user enters the extensions page — smooth
    // (throttled to once per 30 min inside the manager) + non-blocking.
    LaunchedEffect(Unit) {
        extensionManager.checkForUpdates()
        csManager.checkForUpdates()
    }

    // Force a fresh check whenever the repo set changes.
    LaunchedEffect(repos.size) {
        if (repos.isNotEmpty()) {
            extensionManager.checkForUpdates(force = true)
        }
    }

    // Same for CloudStream repos.
    LaunchedEffect(csRepos.size) {
        if (csRepos.isNotEmpty()) {
            csManager.checkForUpdates(force = true)
        }
    }

    // Keep reorderedInstalled in sync with installedExtensions (when not reordering).
    LaunchedEffect(installedExtensions) {
        if (!reorderMode) reorderedInstalled = installedExtensions
    }

    val installedPkgs = installedExtensions.map { it.pkgName }.toSet()
    val untrustedPkgs = untrustedExtensions.map { it.pkgName }.toSet()

    // D-298: language filter — the distinct set of languages across ALL sections
    // of BOTH tabs (session 2: the shared filters bar serves aniyomi + CloudStream,
    // so the dropdown must cover both ecosystems' languages).
    val allLanguages = remember(
        installedExtensions, untrustedExtensions, erroredExtensions, availableExtensions,
        csInstalled, csErrored, csAvailable,
    ) {
        (installedExtensions.mapNotNull { it.lang } +
            untrustedExtensions.mapNotNull { it.lang } +
            erroredExtensions.mapNotNull { it.lang } +
            availableExtensions.mapNotNull { it.lang } +
            csInstalled.mapNotNull { it.language } +
            csErrored.mapNotNull { it.language } +
            csAvailable.mapNotNull { it.plugin.language })
            .distinct()
            .sorted()
    }

    // ── Filtering + sorting ──
    val filteredInstalled = reorderedInstalled.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.let { if (reorderMode) it else sortExtensions(it, sortMode) }
        // Phase 2d: disabled extensions sorted to the bottom (enabled first).
        .let { sorted -> if (reorderMode) sorted else sorted.sortedBy { !it.isEnabled } }

    val filteredErrored = erroredExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.let { sortExtensions(it, sortMode) }

    val filteredUntrusted = untrustedExtensions.filter { ext ->
        matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
            (langFilter == null || ext.lang == langFilter)
    }.let { sortExtensions(it, sortMode) }

    val filteredAvailable = availableExtensions
        .filter { it.pkgName !in installedPkgs && it.pkgName !in untrustedPkgs }
        .filter { ext ->
            matchesSearch(ext.name, searchQuery) && (showNsfw || !ext.isNsfw) &&
                (langFilter == null || ext.lang == langFilter)
        }
        .let { sortExtensions(it, sortMode) }

    val isCheckingUpdates = updateCheckState == ExtensionManager.UpdateCheckState.Checking

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

            // ── Task 41: source tabs (only when both systems have content) ──
            if (csHasContent) {
                SourceTabRow(
                    activeTab = activeTab,
                    onSelect = { activeTab = it },
                )
            }

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
                    // Session 2: the bar controls whichever tab is ACTIVE — the
                    // aniyomi session-local toggle or the persisted CS gate (G4).
                    showNsfw = if (showCloudstreamTab) csShowNsfw else showNsfw,
                    onToggleNsfw = {
                        if (showCloudstreamTab) {
                            csShowNsfw = !csShowNsfw
                            appPreferences.cloudstreamShowNsfw = csShowNsfw
                        } else {
                            showNsfw = !showNsfw
                        }
                    },
                    languages = allLanguages,
                    langFilter = langFilter,
                    onLangFilterChange = { langFilter = it },
                )
            }

            if (showCloudstreamTab) {
                // ── CloudStream tab content (doc 23 §5.4) ──
                // Session 2: rendered with the SAME section chrome + row anatomy
                // as the aniyomi tab (ExtensionListChrome.kt) and driven by the
                // SAME filters bar — search, sort, language and the NSFW gate
                // all flow in from the shared controls above.
                CloudstreamExtensionsSection(
                    csManager = csManager,
                    searchQuery = searchQuery,
                    sortMode = sortMode,
                    langFilter = langFilter,
                    showNsfw = csShowNsfw,
                )
            } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // D-299: every section header is its own item and every row is its
                    // own item (keys + contentType) — the Available section (80+ rows
                    // from a full repo) previously composed ALL rows inside a single
                    // non-virtualized item.

                    // ── Trusted Sources ──
                    item(key = "header-installed", contentType = "sectionHeader") {
                        SectionHeader(
                            title = "Trusted Sources",
                            count = filteredInstalled.size,
                            isEmpty = filteredInstalled.isEmpty(),
                            emptyMessage = "No trusted sources. Install an extension to get started.",
                        )
                    }
                    items(
                        filteredInstalled,
                        key = { "installed-${it.pkgName}" },
                        contentType = { "installedRow" },
                    ) { ext ->
                        val index = filteredInstalled.indexOf(ext)
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
                            // D-301: direct update action when a newer version is
                            // available from the configured repos.
                            onUpdate = if (ext.hasUpdate) {
                                {
                                    availableExtensions.find { it.pkgName == ext.pkgName }?.let { latest ->
                                        scope.launch {
                                            extensionManager.installExtension(latest).collectLatest { }
                                        }
                                    }
                                }
                            } else null,
                            // D-309: live install state for the progress animation.
                            installStep = installStates[ext.pkgName],
                        )
                    }

                    // ── Failed to Load (D-296) ──
                    if (filteredErrored.isNotEmpty()) {
                        item(key = "header-errored", contentType = "sectionHeader") {
                            SectionHeader(title = "Failed to Load", count = filteredErrored.size, isEmpty = false)
                        }
                        items(
                            filteredErrored,
                            key = { "errored-${it.pkgName}" },
                            contentType = { "erroredRow" },
                        ) { ext ->
                            ErroredExtensionRow(
                                extension = ext,
                                onRetry = { extensionManager.retryExtension(ext) },
                                onUntrust = { extensionManager.untrustExtension(ext) },
                                onDelete = { extensionManager.uninstallExtension(ext) },
                            )
                        }
                    }

                    // ── Untrusted ──
                    if (filteredUntrusted.isNotEmpty()) {
                        item(key = "header-untrusted", contentType = "sectionHeader") {
                            SectionHeader(title = "Untrusted", count = filteredUntrusted.size, isEmpty = false)
                        }
                        items(
                            filteredUntrusted,
                            key = { "untrusted-${it.pkgName}" },
                            contentType = { "untrustedRow" },
                        ) { ext ->
                            UntrustedExtensionRow(
                                extension = ext,
                                onTrust = { extensionManager.trustExtension(ext) },
                                onDelete = { extensionManager.uninstallExtension(ext) },
                            )
                        }
                    }

                    // ── Available Extensions ──
                    item(key = "header-available", contentType = "sectionHeader") {
                        SectionHeader(title = "Available Extensions", count = filteredAvailable.size, isEmpty = false)
                    }
                    when {
                        repos.isEmpty() -> item(key = "available-empty-repos", contentType = "availableBody") {
                            EmptySectionBody("No repositories configured. Tap the settings icon to add one.")
                        }
                        isCheckingUpdates && filteredAvailable.isEmpty() -> item(key = "available-loading", contentType = "availableBody") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                        }
                        filteredAvailable.isEmpty() -> item(key = "available-empty", contentType = "availableBody") {
                            EmptySectionBody("No extensions found in your repositories.")
                        }
                        else -> items(
                            filteredAvailable,
                            key = { "available-${it.pkgName}-${it.versionCode}" },
                            contentType = { "availableRow" },
                        ) { ext ->
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
}

// ════════════════════════════════════════════════════════════════════════════
// Task 41: source tab row — Aniyomi / CloudStream (doc 23 §5.4)
// Session-2 device round: chips sit flush against the RIGHT edge of the row
// (was: left-aligned right under the title — user report).
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SourceTabRow(
    activeTab: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        SourceTabChip(
            label = "Aniyomi",
            selected = activeTab != "cloudstream",
            onClick = { onSelect("aniyomi") },
        )
        SourceTabChip(
            label = "CloudStream",
            selected = activeTab == "cloudstream",
            onClick = { onSelect("cloudstream") },
        )
    }
}

@Composable
private fun SourceTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(200),
        label = "tabChipColor",
    )
    androidx.compose.material3.Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
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
    languages: List<String>,
    langFilter: String?,
    onLangFilterChange: (String?) -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }

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
            // D-298: language filter — All + the distinct languages across every section.
            if (languages.isNotEmpty()) {
                Box {
                    HeaderIconButton(
                        icon = Icons.Filled.Language,
                        contentDescription = "Filter by language",
                        onClick = { showLangMenu = !showLangMenu },
                    )
                    DropdownMenu(expanded = showLangMenu, onDismissRequest = { showLangMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All languages", fontFamily = RobotoFamily) },
                            onClick = { onLangFilterChange(null); showLangMenu = false },
                            trailingIcon = if (langFilter == null) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang, fontFamily = RobotoFamily) },
                                onClick = { onLangFilterChange(lang); showLangMenu = false },
                                trailingIcon = if (langFilter == lang) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            }
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
    onUpdate: (() -> Unit)? = null,
    // D-309: live install state (from ExtensionManager.installStates) so the
    // update control can animate the download progress. Previously the row
    // ignored install state entirely — no feedback during an update download.
    installStep: InstallStep? = null,
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
                // D-301/D-309: update control — a filled "Update" pill (was a bare
                // Refresh icon indistinguishable from Retry) that transforms into a
                // live download-progress animation while the update installs.
                ExtensionUpdateControl(
                    installStep = installStep,
                    onUpdate = onUpdate,
                )
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
private fun ErroredExtensionRow(
    extension: AnimeExtension.Errored,
    onRetry: () -> Unit,
    onUntrust: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = "Failed to load · v${extension.versionName}".ifEmpty { "Failed to load" },
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // D-296: Retry (re-attempt the load — e.g. after an app update
                // shipped the missing APIs), Untrust (back to the untrusted list),
                // Delete (uninstall).
                ActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    onClick = onRetry,
                    tint = MaterialTheme.colorScheme.primary,
                )
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
            // The actual failure reason straight from the loader (exception class
            // + message per source class) — no more silent vanishing.
            Text(
                text = extension.message,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
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
            // Session 2: the shared install control (ExtensionListChrome.kt) —
            // identical state machine for the aniyomi AND CloudStream available
            // rows: Download button → animated ring + % → pulsing "Installing"
            // → check + "Done" beat (D-309/D-311 lineage).
            AvailableInstallControl(
                installStep = installStep,
                onInstall = onInstall,
            )
        }
    }
}


// ── Screen-header circular icon button (screen-local; rows use the shared
//    ActionIconButton from ExtensionListChrome.kt) ──

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

