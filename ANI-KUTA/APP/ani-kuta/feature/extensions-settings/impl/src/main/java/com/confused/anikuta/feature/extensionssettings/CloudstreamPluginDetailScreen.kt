package com.confused.anikuta.feature.extensionssettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.installer.CsSharedPluginFormat
import com.confused.anikuta.data.cloudstream.model.CloudstreamExtension
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale

/**
 * The CloudStream PLUGIN detail page (session 3, device round 2: "when the user
 * clicks on any of the extensions, then it will open up its extension details
 * page and all of its relevant details").
 *
 * One screen resolves the plugin across every manager state and renders:
 * - Identity — icon, name, version, NSFW.
 * - Status — Trusted (N providers) / Untrusted / Failed to load (+ reason) /
 *   Disabled by repo / Update available / Available (not installed).
 * - Catalog metadata captured at INSTALL time (authors, description, language,
 *   size, supported modes) — renders identically even after repo deletion.
 * - The LIVE provider list for trusted plugins (name, language, content types,
 *   provider type, browse support) — what actually serves content in Search.
 *
 * Actions per state: Trust (untrusted — loads the plugin), Untrust (trusted —
 * unloads it), Retry (errored), Uninstall (any installed state), Install
 * (available — the shared progress machine from ExtensionListChrome).
 *
 * The page consumes the manager's StateFlows directly (koinInject — the
 * settings-land convention, no ViewModel); every action re-renders the page via
 * the flow the mutation refreshes.
 */
@Composable
fun CloudstreamPluginDetailScreen(
    internalName: String,
    onBack: () -> Unit,
    csManager: CloudstreamPluginManager = koinInject(),
) {
    val installed by csManager.installed.collectAsState()
    val untrusted by csManager.untrusted.collectAsState()
    val errored by csManager.errored.collectAsState()
    val available by csManager.available.collectAsState()
    val installStates by csManager.installStates.collectAsState()
    // Task 44: the retry spinner state (device round 3: "no animation while it
    // was reloading").
    val retrying by csManager.retrying.collectAsState()

    // Resolve the plugin's CURRENT state (a trust/uninstall flips it live).
    val trustedExt = installed.find { it.internalName == internalName }
    val untrustedExt = untrusted.find { it.internalName == internalName }
    val erroredExt = errored.find { it.internalName == internalName }
    val availableExt = available.find { it.plugin.internalName == internalName }

    // Field-level extracts across the mutually-exclusive installed states
    // (the sealed base doesn't expose filePath/repoUrl, so a mixed elvis chain
    // would resolve to the base type and fail). NOTE: Errored carries no
    // repoUrl — a plugin that failed to load simply omits the row.
    val diskFilePath: String? =
        trustedExt?.filePath ?: untrustedExt?.filePath ?: erroredExt?.filePath
    val recordRepoUrl: String? = trustedExt?.repoUrl ?: untrustedExt?.repoUrl

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val collapsed = listState.firstVisibleItemIndex > 0 ||
        listState.firstVisibleItemScrollOffset > 20
    // Task 58 (round 18 — plugin sharing): the export handler runs against the
    // local context (cache-dir export copy + the system share sheet).
    val shareContext = LocalContext.current

    // Common display metadata, resolved across the installed states (metadata
    // captured at install) or the catalog entry (available).
    val meta: PluginMeta? = trustedExt?.toMeta()
        ?: untrustedExt?.toMeta()
        ?: erroredExt?.toMeta()
        ?: availableExt?.toMeta()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Plugin Details",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            if (meta == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Plugin not found",
                            fontFamily = RobotoFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "It may have been uninstalled.",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        TextButton(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Go back", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 110.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // ── Header: icon + name + version + status ──
                        item(key = "header") {
                            PluginHeader(
                                name = meta.name,
                                version = meta.version,
                                iconUrl = meta.iconUrl,
                                statusText = when {
                                    trustedExt != null && erroredExt == null ->
                                        if (trustedExt.providerCount > 0) {
                                            "Trusted · ${trustedExt.providerCount} provider(s)"
                                        } else "Trusted"
                                    untrustedExt != null -> "Untrusted"
                                    erroredExt != null -> "Failed to load"
                                    else -> "Available"
                                },
                                statusColor = when {
                                    trustedExt != null -> MaterialTheme.colorScheme.primary
                                    untrustedExt != null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    erroredExt != null -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }

                        // ── Trust gate (untrusted): the prominent primary action,
                        // WITH Uninstall beside it (Task 44, device round 3: "the
                        // uninstall button should show on the right side of the
                        // trust plugin button") ──
                        if (untrustedExt != null) {
                            item(key = "trust-cta") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    DetailAction(
                                        text = "Trust Plugin",
                                        icon = Icons.Filled.VerifiedUser,
                                        filled = true,
                                        onClick = { csManager.trustPlugin(untrustedExt) },
                                        modifier = Modifier.weight(1f),
                                    )
                                    DetailAction(
                                        text = "Uninstall",
                                        icon = Icons.Filled.Delete,
                                        color = MaterialTheme.colorScheme.error,
                                        onClick = { showDeleteConfirm = true },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            item(key = "trust-note") {
                                Text(
                                    text = "This plugin is installed but not trusted — its code has never " +
                                        "run. Trusting it loads its providers so they appear in Search.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            // Task 58 (round 18 — plugin sharing): the untrusted
                            // state gets the SAME Share row (sharing needs only
                            // the file on disk, not trust).
                            if (diskFilePath != null) {
                                item(key = "share-untrusted") {
                                    SharePluginRow(
                                        filePath = diskFilePath,
                                        internalName = internalName,
                                        context = shareContext,
                                    )
                                }
                            }
                        }

                        // ── Info card ──
                        item(key = "info") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    InfoRow("Internal name", internalName)
                                    meta.authors.takeIf { it.isNotEmpty() }?.let {
                                        Spacer(Modifier.height(8.dp)); InfoRow("Authors", it.joinToString(", "))
                                    }
                                    meta.language?.let {
                                        Spacer(Modifier.height(8.dp)); InfoRow("Language", it)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    InfoRow(
                                        "File size",
                                        formatBytes(
                                            diskBytes = diskFilePath
                                                ?.let { File(it).takeIf(File::exists)?.length() },
                                            catalogBytes = meta.fileSizeBytes,
                                        ),
                                    )
                                    if (meta.isNsfw) {
                                        Spacer(Modifier.height(8.dp)); InfoRow("NSFW", "Yes")
                                    }
                                    availableExt?.let {
                                        Spacer(Modifier.height(8.dp)); InfoRow("Repository", it.repoName)
                                    }
                                    recordRepoUrl?.let {
                                        Spacer(Modifier.height(8.dp)); InfoRow("Repository URL", it)
                                    }
                                    // Task 58 (round 18): repo-less records (shared-file
                                    // imports with no matching added repository) say so
                                    // instead of showing nothing.
                                    if (recordRepoUrl == null && availableExt == null && diskFilePath != null) {
                                        Spacer(Modifier.height(8.dp))
                                        InfoRow("Source", "Shared file (no repository)")
                                    }
                                    trustedExt?.availableUpdateVersion?.let { updateVersion ->
                                        Spacer(Modifier.height(8.dp))
                                        InfoRow("Update", "v$updateVersion available")
                                    }
                                    if (trustedExt?.isDisabledByRepo == true) {
                                        Spacer(Modifier.height(8.dp))
                                        InfoRow("Repo status", "Disabled by repository")
                                    }
                                }
                            }
                        }

                        // ── Supported modes (tvTypes chips) ──
                        if (meta.tvTypes.isNotEmpty()) {
                            item(key = "modes") {
                                DetailCard(title = "Supported Modes") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        meta.tvTypes.forEach { type -> ModeChip(type) }
                                    }
                                }
                            }
                        }

                        // ── Description ──
                        meta.description?.takeIf { it.isNotBlank() }?.let { desc ->
                            item(key = "description") {
                                DetailCard(title = "Description") {
                                    Text(
                                        text = desc,
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        // ── Failure reason (errored) ──
                        erroredExt?.let { err ->
                            item(key = "error") {
                                DetailCard(title = "Load Failure") {
                                    Text(
                                        text = err.message,
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }

                        // ── Live providers (trusted) ──
                        trustedExt?.takeIf { it.providers.isNotEmpty() }?.let { ext ->
                            item(key = "providers-header") {
                                DetailCard(title = "Providers (${ext.providers.size})") {}
                            }
                            ext.providers.forEach { provider ->
                                item(key = "provider-${provider.name}") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = provider.name,
                                                    fontFamily = RobotoFamily,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                )
                                                if (provider.usesWebView) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Public,
                                                        contentDescription = "Uses WebView",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                            Text(
                                                text = buildString {
                                                    append(provider.lang)
                                                    append(" · ${provider.providerTypeName}")
                                                    append(if (provider.hasMainPage) " · Browsable" else " · Search only")
                                                },
                                                fontFamily = RobotoFamily,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 2.dp),
                                                maxLines = 1,
                                            )
                                            if (provider.supportedTypes.isNotEmpty()) {
                                                Text(
                                                    text = provider.supportedTypes.joinToString(" · "),
                                                    fontFamily = RobotoFamily,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(top = 2.dp),
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ── Actions row (TRUSTED / ERRORED states) ──
                        // Task 44: the UNTRUSTED state's Uninstall moved up beside
                        // the Trust button (device round 3); this bottom row now
                        // serves the trusted ([Untrust][Uninstall] — approved as-is
                        // in round 3) and errored ([Retry][Uninstall]) states.
                        if (trustedExt != null || erroredExt != null) {
                            item(key = "actions") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (erroredExt != null) {
                                        DetailAction(
                                            text = "Retry",
                                            icon = Icons.Filled.Refresh,
                                            loading = internalName in retrying,
                                            onClick = { csManager.retryPlugin(erroredExt) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (trustedExt != null) {
                                        DetailAction(
                                            text = "Untrust",
                                            icon = Icons.Filled.VerifiedUser,
                                            onClick = { csManager.untrustPlugin(trustedExt) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    DetailAction(
                                        text = "Uninstall",
                                        icon = Icons.Filled.Delete,
                                        color = MaterialTheme.colorScheme.error,
                                        onClick = { showDeleteConfirm = true },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            // Task 58 (round 18 — plugin sharing): the share export
                            // for installed (trusted/errored) plugins — full width,
                            // below the state actions.
                            if (diskFilePath != null) {
                                item(key = "share-installed") {
                                    SharePluginRow(
                                        filePath = diskFilePath,
                                        internalName = internalName,
                                        context = shareContext,
                                    )
                                }
                            }
                        }

                        // ── Install (available): the shared progress machine, at
                        // the VERY BOTTOM and as wide as possible (Task 44, device
                        // round 3: "the download button should be shown at the very
                        // bottom … as wide as it can be") ──
                        if (availableExt != null) {
                            item(key = "install-cta") {
                                val step = installStates[internalName]
                                if (step == null) {
                                    DetailAction(
                                        text = "Install",
                                        icon = Icons.Filled.Download,
                                        filled = true,
                                        onClick = { csManager.installPlugin(availableExt) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    // In-flight/terminal install states reuse the
                                    // shared compact progress machine, centered on
                                    // the full-width footprint.
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AvailableInstallControl(
                                            installStep = step,
                                            onInstall = { csManager.installPlugin(availableExt) },
                                        )
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
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Uninstall plugin?", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold) },
            text = {
                Text(
                    "This will remove ${meta?.name?.removeSuffix("Provider") ?: "this plugin"} from your device.",
                    fontFamily = RobotoFamily,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    val ext = trustedExt ?: untrustedExt ?: erroredExt ?: return@TextButton
                    csManager.uninstallPlugin(ext)
                    onBack()
                }) {
                    Text(
                        "Uninstall",
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = RobotoFamily,
                        fontWeight = FontWeight.ExtraBold,
                    )
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

// ════════════════════════════════════════════════════════════════════════════
//  Pieces
// ════════════════════════════════════════════════════════════════════════════

/** The display metadata the detail page renders for any plugin state. */
private data class PluginMeta(
    val name: String,
    val version: Int,
    val language: String?,
    val iconUrl: String?,
    val isNsfw: Boolean,
    val authors: List<String>,
    val description: String?,
    val tvTypes: List<String>,
    val fileSizeBytes: Long?,
)

private fun CloudstreamExtension.Installed.toMeta() = PluginMeta(
    name, version, language, iconUrl, isNsfw, authors, description, tvTypes, fileSizeBytes,
)

private fun CloudstreamExtension.Untrusted.toMeta() = PluginMeta(
    name, version, language, iconUrl, isNsfw, authors, description, tvTypes, fileSizeBytes,
)

private fun CloudstreamExtension.Errored.toMeta() = PluginMeta(
    name, version, language, iconUrl, isNsfw, authors, description, tvTypes, fileSizeBytes,
)

private fun CloudstreamExtension.Available.toMeta() = PluginMeta(
    plugin.name,
    plugin.version,
    plugin.language,
    plugin.iconUrl,
    isNsfw,
    plugin.authors,
    plugin.description,
    plugin.tvTypes ?: emptyList(),
    plugin.fileSize,
)

@Composable
private fun PluginHeader(
    name: String,
    version: Int,
    iconUrl: String?,
    statusText: String,
    statusColor: Color,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CsPluginIcon(iconUrl = iconUrl, name = name, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.removeSuffix("Provider"),
                    fontFamily = RobotoFamily,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                Text(
                    text = "v$version",
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = statusText,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (title.isNotEmpty()) Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeChip(type: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = type,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * A wide action button (the aniyomi detail page's ActionButton pattern).
 * Task 44: `loading = true` swaps the icon for a spinner (the Retry button's
 * in-flight state — the device round-3 "no animation while reloading" report).
 */
@Composable
private fun DetailAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = false,
    loading: Boolean = false,
) {
    val container = if (filled) color else color.copy(alpha = 0.12f)
    val content = if (filled) MaterialTheme.colorScheme.onPrimary else color
    Surface(
        color = container,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(44.dp)
            .clickable(enabled = !loading, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = content,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = content,
            )
        }
    }
}

/** Prefers the on-disk size (ground truth); falls back to the catalog size. */
private fun formatBytes(diskBytes: Long?, catalogBytes: Long?): String {
    val bytes = diskBytes ?: catalogBytes ?: return "Unknown"
    if (bytes <= 0) return "Unknown"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Task 58 (round 18): the plugin SHARE export (.moviebox.WHITECAT)
// ════════════════════════════════════════════════════════════════════════════

/**
 * The full-width "Share" action (the user's spec: every plugin detail page —
 * trusted OR untrusted — carries ONE share option). Exports the installed
 * .cs3 bytes under our custom extension via the system share sheet.
 */
@Composable
private fun SharePluginRow(
    filePath: String,
    internalName: String,
    context: android.content.Context,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailAction(
            text = "Share",
            icon = Icons.Filled.IosShare,
            onClick = { sharePluginFile(context, filePath, internalName) },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = "Exports this plugin as a .${CsSharedPluginFormat.SHARED_EXTENSION} file — " +
            "the receiver opens it with ANI-KUTA to install.",
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/**
 * The export itself — the ConsoleLogsScreen.shareLogReport pattern: a fresh
 * copy in cacheDir/exports/ (the FileProvider already exposes the whole
 * cacheDir) named `<internalName>.moviebox.WHITECAT`, then the system share
 * sheet with a granted read URI. The copy (not the installed original) is
 * shared so the exported NAME is deterministic regardless of the install
 * path's repo salt — and the .setReadOnly() original stays untouched.
 */
private fun sharePluginFile(context: android.content.Context, filePath: String, internalName: String) {
    runCatching {
        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val export = java.io.File(exportDir, CsSharedPluginFormat.sharedFileName(internalName))
        java.io.File(filePath).copyTo(export, overwrite = true)
        // The share target only READS it — make sure the export isn't read-only
        // from a copied flag (copyTo copies the source's permissions).
        export.setReadable(true, false)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", export)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ANI-KUTA plugin: $internalName")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("plugin", uri)
        }
        context.startActivity(Intent.createChooser(intent, "Share plugin"))
        com.confused.anikuta.core.common.Logger.i("Anikuta:CS:PluginDetail") {
            "shared plugin $internalName as ${export.name} (${export.length() / 1024} KB)"
        }
    }.onFailure { t ->
        com.confused.anikuta.core.common.Logger.e("Anikuta:CS:PluginDetail", t) { "share failed" }
        Toast.makeText(context, "Couldn't share the plugin file", Toast.LENGTH_SHORT).show()
    }
}

