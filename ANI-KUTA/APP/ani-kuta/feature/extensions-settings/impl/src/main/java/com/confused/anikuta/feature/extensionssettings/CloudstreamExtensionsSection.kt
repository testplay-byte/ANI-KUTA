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
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
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
    // Round 82 (D-572): ascending/descending, toggled from the sort menu.
    sortAscending: Boolean = true,
    langFilter: String? = null,
    showNsfw: Boolean = false,
    onOpenPluginDetail: (internalName: String) -> Unit = {},
) {
    val installed by csManager.installed.collectAsState()
    val untrusted by csManager.untrusted.collectAsState()
    val errored by csManager.errored.collectAsState()
    val available by csManager.available.collectAsState()
    val installStates by csManager.installStates.collectAsState()
    // Task 44: the retry spinner state (device round 3: "no animation while it
    // was reloading").
    val retryingNames by csManager.retrying.collectAsState()
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
        .let { sortCsExtensions(it, sortMode, sortAscending) }

    val filteredUntrusted = untrusted
        .filter {
            matchesSearch(it.name, searchQuery) && (showNsfw || !it.isNsfw) &&
                (langFilter == null || it.language == langFilter)
        }
        .let { sortCsUntrusted(it, sortMode, sortAscending) }

    val filteredErrored = errored
        .filter {
            matchesSearch(it.name, searchQuery) && (showNsfw || !it.isNsfw) &&
                (langFilter == null || it.language == langFilter)
        }
        .let { sortCsErrored(it, sortMode, sortAscending) }

    val filteredAvailable = available
        .filter { showNsfw || !it.isNsfw }
        .filter {
            matchesSearch(it.plugin.name, searchQuery) &&
                (langFilter == null || it.plugin.language == langFilter)
        }
        .let { sortCsAvailable(it, sortMode, sortAscending) }

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
                    modifier = Modifier.animateItem(),
                    extension = ext,
                    installStep = installStates[ext.internalName],
                    onUpdate = ext.availableUpdateVersion?.let {
                        {
                            // Task 62 (round 22): the online catalog target is
                            // resolved through the identity ladder — a LINKED
                            // manual import was never in the Available list
                            // under its own (drifted) internalName, so the old
                            // exact-name lookup missed it and the pill was a
                            // no-op. installPlugin updates the existing record
                            // in place.
                            csManager.availableUpdateTarget(ext.internalName)
                                ?.let(csManager::installPlugin)
                        }
                    },
                    onUninstall = { csManager.uninstallPlugin(ext) },
                    // Task 45 (round-4 report): untrust directly from the LIST —
                    // the row keeps its detail-page actions too, but the user
                    // shouldn't have to open the plugin page to untrust.
                    onUntrust = { csManager.untrustPlugin(ext) },
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
                        modifier = Modifier.animateItem(),
                        extension = ext,
                        retrying = ext.internalName in retryingNames,
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
                        modifier = Modifier.animateItem(),
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
                        modifier = Modifier.animateItem(),
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
    modifier: Modifier = Modifier,
    extension: CloudstreamExtension.Installed,
    installStep: InstallStep?,
    onUpdate: (() -> Unit)?,
    onUninstall: () -> Unit,
    onUntrust: () -> Unit,
    onClick: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // D-580 (round 84): the in-app AlertDialog is the confirmation, so the
    // exit choreography runs AFTER it — the row plays the same settle-dip →
    // slide+fade motion as the Downloads page, and the plugin delete fires
    // only when the exit finishes (the data removal then closes the gap via
    // the row's animateItem placement). Already the round-85 contract: the
    // animation fires on the CONFIRMED deletion (the dialog's OK).
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onUninstall()
        delay(2500)
        deleteExit.restoreFromExit()
        removing = false
    }
    // ROUND 85: UNTRUST gets the same exit choreography, tap-driven — the
    // row visibly leaves Trusted Sources before the data change fires.
    var exitingForUntrust by remember { mutableStateOf(false) }
    LaunchedEffect(exitingForUntrust) {
        if (!exitingForUntrust) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onUntrust()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
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
            // Task 45 (round-4 report): UNTRUST directly from the Trusted
            // Sources list. Task 46 (round-5 report): NO confirmation dialog —
            // aniyomi's untrust is a one-tap action, and the CS list now
            // matches it (the round-5 feedback explicitly asked for parity).
            // Untrusting unloads the plugin's code (providers vanish from the
            // search picker + the source bridge) but KEEPS the file — Trust
            // brings it back without a re-download.
            ActionIconButton(
                icon = Icons.Filled.RemoveModerator,
                contentDescription = "Untrust plugin (keep file)",
                onClick = { if (!exitingForUntrust) exitingForUntrust = true },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                TextButton(onClick = { showDeleteConfirm = false; if (!removing) removing = true }) {
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
    modifier: Modifier = Modifier,
    extension: CloudstreamExtension.Errored,
    retrying: Boolean = false,
    onRetry: () -> Unit,
    onUninstall: () -> Unit,
    onClick: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // D-580 (round 84): same exit choreography as the installed CS row.
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onUninstall()
        delay(2500)
        deleteExit.restoreFromExit()
        removing = false
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
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
                // Task 44: the retry icon becomes a spinner while the reload
                // is in flight (device round 3: "no animation while reloading").
                if (retrying) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    ActionIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Retry loading plugin",
                        onClick = onRetry,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
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
                TextButton(onClick = { showDeleteConfirm = false; if (!removing) removing = true }) {
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
    modifier: Modifier = Modifier,
    extension: CloudstreamExtension.Untrusted,
    onTrust: () -> Unit,
    onUninstall: () -> Unit,
    onClick: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // D-580 (round 84): same exit choreography as the installed CS row —
    // it fires on the CONFIRMED deletion (the dialog's OK), per the round-85
    // contract.
    var removing by remember { mutableStateOf(false) }
    val deleteExit = rememberDeleteExitState()
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onUninstall()
        delay(2500)
        deleteExit.restoreFromExit()
        removing = false
    }
    // ROUND 85: TRUST gets the same exit choreography, tap-driven — the row
    // visibly leaves the untrusted section and re-enters Trusted Sources.
    var exitingForTrust by remember { mutableStateOf(false) }
    LaunchedEffect(exitingForTrust) {
        if (!exitingForTrust) return@LaunchedEffect
        deleteExit.runExitChoreography()
        onTrust()
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .deleteExitLayer(deleteExit)
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
            // Task 46 (round-5 report): the trust action uses the SHIELD
            // icon (VerifiedUser) — the old checkmark-badge glyph (Verified)
            // read as "already verified" instead of "trust this plugin".
            // ROUND 85: the trust tap plays the exit choreography first.
            ActionIconButton(
                icon = Icons.Filled.VerifiedUser,
                contentDescription = "Trust plugin",
                onClick = { if (!exitingForTrust) exitingForTrust = true },
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
                TextButton(onClick = { showDeleteConfirm = false; if (!removing) removing = true }) {
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
    modifier: Modifier = Modifier,
    extension: CloudstreamExtension.Available,
    installStep: InstallStep?,
    onInstall: () -> Unit,
    onClick: () -> Unit,
) {
    val plugin = extension.plugin
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
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

private fun <T> sortCsBase(
    list: List<T>,
    mode: ExtensionSortMode,
    ascending: Boolean,
    name: (T) -> String,
    lang: (T) -> String?,
    nsfw: (T) -> Boolean,
): List<T> = when (mode) {
    ExtensionSortMode.NAME ->
        if (ascending) list.sortedBy { name(it).lowercase() }
        else list.sortedByDescending { name(it).lowercase() }
    ExtensionSortMode.LANGUAGE ->
        if (ascending) list.sortedBy { (lang(it) ?: "zz").lowercase() }
        else list.sortedByDescending { (lang(it) ?: "").lowercase() }
    ExtensionSortMode.NSFW ->
        if (ascending) list.sortedByDescending(nsfw)
        else list.sortedBy(nsfw)
}

private fun sortCsExtensions(list: List<CloudstreamExtension.Installed>, mode: ExtensionSortMode, ascending: Boolean): List<CloudstreamExtension.Installed> =
    sortCsBase(list, mode, ascending, name = { it.name }, lang = { it.language }, nsfw = { it.isNsfw })

private fun sortCsErrored(list: List<CloudstreamExtension.Errored>, mode: ExtensionSortMode, ascending: Boolean): List<CloudstreamExtension.Errored> =
    sortCsBase(list, mode, ascending, name = { it.name }, lang = { it.language }, nsfw = { it.isNsfw })

private fun sortCsUntrusted(list: List<CloudstreamExtension.Untrusted>, mode: ExtensionSortMode, ascending: Boolean): List<CloudstreamExtension.Untrusted> =
    sortCsBase(list, mode, ascending, name = { it.name }, lang = { it.language }, nsfw = { it.isNsfw })

private fun sortCsAvailable(list: List<CloudstreamExtension.Available>, mode: ExtensionSortMode, ascending: Boolean): List<CloudstreamExtension.Available> =
    sortCsBase(list, mode, ascending, name = { it.plugin.name }, lang = { it.plugin.language }, nsfw = { it.isNsfw })
