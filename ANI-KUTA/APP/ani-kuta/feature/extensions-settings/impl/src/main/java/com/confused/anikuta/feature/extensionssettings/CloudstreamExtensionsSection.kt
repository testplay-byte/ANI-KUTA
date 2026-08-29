package com.confused.anikuta.feature.extensionssettings

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.model.CloudstreamExtension
import com.confused.anikuta.data.cloudstream.repo.CloudstreamRepoRepository
import org.koin.compose.koinInject

/**
 * The CloudStream tab of the unified Extensions screen (doc 23 §5.4, gate
 * G3-adjacent: the user's unified-extensions-page decision).
 *
 * SESSION-2 DEVICE ROUND — this tab renders with the EXACT same structure,
 * chrome and row anatomy as the aniyomi tab above it (the user's consistency
 * report), built from the shared pieces in [ExtensionListChrome.kt].
 *
 * 1. Trusted Sources — installed + TRUSTED plugins (providers live).
 * 2. Failed to Load — only when a plugin genuinely fails (conditional, same as
 *    the aniyomi tab; D-295/D-296: never silent).
 * 3. Untrusted — installed but not yet trusted (session 3 trust flow); the
 *    Trust action loads the plugin + moves the row into Trusted Sources.
 * 4. Available Extensions — the repo catalog, same rows, same normal Download
 *    button, same progress machine.
 *
 * Every row is CLICKABLE → the CloudStream plugin detail page (session 3,
 * device round 2): description, authors, version, status, size, supported
 * modes, language, and the live provider list.
 *
 * Search / sort / language / NSFW all flow in from the ONE filters bar shared
 * with the aniyomi tab (G4: NSFW here is the persisted gate, default OFF).
 */
@Composable
internal fun CloudstreamExtensionsSection(
    csManager: CloudstreamPluginManager = koinInject(),
    csRepoRepository: CloudstreamRepoRepository = koinInject(),
    searchQuery: String = "",
    sortMode: ExtensionSortMode = ExtensionSortMode.NAME,
    langFilter: String? = null,
    showNsfw: Boolean = false,
    onOpenPluginDetail: (internalName: String) -> Unit = {},
) {
    val installed by csManager.installed.collectAsState()
    val untrusted by csManager.untrusted.collectAsState()
    val errored by csManager.errored.collectAsState()
    val available by csManager.available.collectAsState()
    val installStates by csManager.installStates.collectAsState()
    val updateCheckState by csManager.updateCheckState.collectAsState()
    val csRepos by csRepoRepository.repos.collectAsState()

    val listState = rememberLazyListState()
    val isChecking = updateCheckState is CloudstreamPluginManager.UpdateCheckState.Checking

    // ── Filtering + sorting (same rules as the aniyomi tab's lists) ──
    val filteredInstalled = installed
        .filter {
            matchesSearch(it.name, searchQuery) && (showNsfw || !it.isNsfw) &&
                (langFilter == null || it.language == langFilter)
        }
        .let { sortCsExtensions(it, sortMode) }

    val filteredUntrusted = untrusted
        .filter {
            matchesSearch(it.name, searchQuery) && (showNsfw || !it.isNsfw) &&
                (langFilter == null || it.language == langFilter)
        }
        .let { sortCsUntrusted(it, sortMode) }

    val filteredErrored = errored
        .filter {
            matchesSearch(it.name, searchQuery) && (showNsfw || !it.isNsfw) &&
                (langFilter == null || it.language == langFilter)
        }
        .let { sortCsErrored(it, sortMode) }

    val filteredAvailable = available
        .filter { showNsfw || !it.isNsfw }
        .filter {
            matchesSearch(it.plugin.name, searchQuery) &&
                (langFilter == null || it.plugin.language == langFilter)
        }
        .let { sortCsAvailable(it, sortMode) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // D-299 pattern: every section header + row is its own virtualized item.

            // ── Trusted Sources ──
            item(key = "cs-header-installed", contentType = "csSectionHeader") {
                SectionHeader(
                    title = "Trusted Sources",
                    count = filteredInstalled.size,
                    isEmpty = filteredInstalled.isEmpty(),
                    emptyMessage = "No trusted sources. Install a plugin to get started.",
                )
            }
            items(
                filteredInstalled,
                key = { "cs-installed-${it.internalName}" },
                contentType = { "csInstalledRow" },
            ) { ext ->
                CsInstalledRow(
                    extension = ext,
                    installStep = installStates[ext.internalName],
                    onUpdate = ext.availableUpdateVersion?.let {
                        {
                            available.firstOrNull { it.plugin.internalName == ext.internalName }
                                ?.let(csManager::installPlugin)
                        }
                    },
                    onUninstall = { csManager.uninstallPlugin(ext) },
                    onClick = { onOpenPluginDetail(ext.internalName) },
                )
            }

            // ── Failed to Load (conditional — D-296 pattern, never silent) ──
            if (filteredErrored.isNotEmpty()) {
                item(key = "cs-header-errored", contentType = "csSectionHeader") {
                    SectionHeader(title = "Failed to Load", count = filteredErrored.size, isEmpty = false)
                }
                items(
                    filteredErrored,
                    key = { "cs-errored-${it.internalName}" },
                    contentType = { "csErroredRow" },
                ) { ext ->
                    CsErroredRow(
                        extension = ext,
                        onRetry = { csManager.retryPlugin(ext) },
                        onUninstall = { csManager.uninstallPlugin(ext) },
                        onClick = { onOpenPluginDetail(ext.internalName) },
                    )
                }
            }

            // ── Untrusted (session 3 trust flow) ──
            if (filteredUntrusted.isNotEmpty()) {
                item(key = "cs-header-untrusted", contentType = "csSectionHeader") {
                    SectionHeader(title = "Untrusted", count = filteredUntrusted.size, isEmpty = false)
                }
                items(
                    filteredUntrusted,
                    key = { "cs-untrusted-${it.internalName}" },
                    contentType = { "csUntrustedRow" },
                ) { ext ->
                    CsUntrustedRow(
                        extension = ext,
                        onTrust = { csManager.trustPlugin(ext) },
                        onUninstall = { csManager.uninstallPlugin(ext) },
                        onClick = { onOpenPluginDetail(ext.internalName) },
                    )
                }
            }

            // ── Available Extensions ──
            item(key = "cs-header-available", contentType = "csSectionHeader") {
                SectionHeader(title = "Available Extensions", count = filteredAvailable.size, isEmpty = false)
            }
            when {
                csRepos.isEmpty() -> item(key = "cs-available-empty-repos", contentType = "csSectionBody") {
                    EmptySectionBody("No repositories configured. Tap the settings icon to add one.")
                }
                isChecking && filteredAvailable.isEmpty() -> item(key = "cs-available-loading", contentType = "csSectionBody") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                }
                filteredAvailable.isEmpty() -> item(key = "cs-available-empty", contentType = "csSectionBody") {
                    EmptySectionBody("No plugins found in your repositories.")
                }
                else -> items(
                    filteredAvailable,
                    key = { "cs-available-${it.plugin.internalName}" },
                    contentType = { "csAvailableRow" },
                ) { ext ->
                    CsAvailableRow(
                        extension = ext,
                        installStep = installStates[ext.plugin.internalName],
                        onInstall = { csManager.installPlugin(ext) },
                        onClick = { onOpenPluginDetail(ext.plugin.internalName) },
                    )
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
//  Rows — same card anatomy as the aniyomi rows: 40dp icon, ExtraBold name,
//  one metadata line, action icon buttons on the trailing edge. Built from the
//  shared ExtensionListChrome pieces so both tabs stay pixel-identical.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsInstalledRow(
    extension: CloudstreamExtension.Installed,
    installStep: InstallStep?,
    onUpdate: (() -> Unit)?,
    onUninstall: () -> Unit,
    onClick: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CsPluginIcon(iconUrl = extension.iconUrl, name = extension.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name.removeSuffix("Provider"),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("v${extension.version}")
                        extension.language?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                        if (extension.availableUpdateVersion != null) append(" · Update available")
                        // Repo-side kill switch (plugins.json status 0, doc 04 §4.5).
                        if (extension.isDisabledByRepo) append(" · Disabled by repo")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // D-301/D-309 lineage: the shared "Update" pill → live download ring.
            ExtensionUpdateControl(
                installStep = installStep,
                onUpdate = onUpdate,
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Uninstall plugin",
                onClick = { showDeleteConfirm = true },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall plugin?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will remove ${extension.name.removeSuffix("Provider")} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onUninstall() }) {
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
private fun CsErroredRow(
    extension: CloudstreamExtension.Errored,
    onRetry: () -> Unit,
    onUninstall: () -> Unit,
    onClick: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CsPluginIcon(iconUrl = extension.iconUrl, name = extension.name)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name.removeSuffix("Provider"),
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Failed to load · v${extension.version}",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // D-296: Retry (re-attempt the load) + Delete (uninstall).
                ActionIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Retry loading plugin",
                    onClick = onRetry,
                    tint = MaterialTheme.colorScheme.primary,
                )
                ActionIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Uninstall plugin",
                    onClick = { showDeleteConfirm = true },
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            // The real failure reason straight from the loader — no silent vanishing.
            Text(
                text = extension.message,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp),
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall plugin?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will remove ${extension.name.removeSuffix("Provider")} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onUninstall() }) {
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

/**
 * Untrusted row (session 3 trust flow — the aniyomi UntrustedExtensionRow
 * pattern): the plugin is on disk but its code has never executed. Trust
 * loads it + moves it to Trusted Sources; Delete uninstalls.
 */
@Composable
private fun CsUntrustedRow(
    extension: CloudstreamExtension.Untrusted,
    onTrust: () -> Unit,
    onUninstall: () -> Unit,
    onClick: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CsPluginIcon(iconUrl = extension.iconUrl, name = extension.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name.removeSuffix("Provider"),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("Untrusted · v${extension.version}")
                        extension.language?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ActionIconButton(
                icon = Icons.Filled.Verified,
                contentDescription = "Trust plugin",
                onClick = onTrust,
                tint = MaterialTheme.colorScheme.primary,
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Uninstall plugin",
                onClick = { showDeleteConfirm = true },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall plugin?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = { Text("This will remove ${extension.name.removeSuffix("Provider")} from your device.", fontFamily = RobotoFamily) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onUninstall() }) {
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

/**
 * Available catalog row — byte-for-byte the aniyomi AvailableExtensionRow
 * anatomy: icon, name, ONE metadata line (version · language · NSFW — no file
 * size, no description per the device report), and the shared install control.
 */
@Composable
private fun CsAvailableRow(
    extension: CloudstreamExtension.Available,
    installStep: InstallStep?,
    onInstall: () -> Unit,
    onClick: () -> Unit,
) {
    val plugin = extension.plugin
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CsPluginIcon(iconUrl = plugin.iconUrl, name = plugin.name)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.name.removeSuffix("Provider"),
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("v${plugin.version}")
                        plugin.language?.let { append(" · $it") }
                        if (extension.isNsfw) append(" · NSFW")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // The SAME state machine + normal Download button as the aniyomi
            // rows (the round's cloud-shaped button is gone).
            AvailableInstallControl(
                installStep = installStep,
                onInstall = onInstall,
            )
        }
    }
}

/**
 * Plugin icon via the shared chrome (session 3 — moved to ExtensionListChrome.kt
 * so the plugin detail screen shares the same treatment).
 */

// ── Sorting (CloudStream twins of the shared aniyomi comparators) ───────────

private fun <T> sortCsBase(list: List<T>, mode: ExtensionSortMode, name: (T) -> String, lang: (T) -> String?, nsfw: (T) -> Boolean): List<T> =
    when (mode) {
        ExtensionSortMode.NAME -> list.sortedBy { name(it).lowercase() }
        ExtensionSortMode.LANGUAGE -> list.sortedBy { (lang(it) ?: "zz").lowercase() }
        ExtensionSortMode.NSFW -> list.sortedByDescending(nsfw)
    }

private fun sortCsExtensions(list: List<CloudstreamExtension.Installed>, mode: ExtensionSortMode): List<CloudstreamExtension.Installed> =
    sortCsBase(list, mode, name = { it.name }, lang = { it.language }, nsfw = { it.isNsfw })

private fun sortCsErrored(list: List<CloudstreamExtension.Errored>, mode: ExtensionSortMode): List<CloudstreamExtension.Errored> =
    sortCsBase(list, mode, name = { it.name }, lang = { it.language }, nsfw = { it.isNsfw })

private fun sortCsUntrusted(list: List<CloudstreamExtension.Untrusted>, mode: ExtensionSortMode): List<CloudstreamExtension.Untrusted> =
    sortCsBase(list, mode, name = { it.name }, lang = { it.language }, nsfw = { it.isNsfw })

private fun sortCsAvailable(list: List<CloudstreamExtension.Available>, mode: ExtensionSortMode): List<CloudstreamExtension.Available> =
    sortCsBase(list, mode, name = { it.plugin.name }, lang = { it.plugin.language }, nsfw = { it.isNsfw })
