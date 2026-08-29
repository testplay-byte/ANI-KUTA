package com.confused.anikuta.feature.extensionssettings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.AppPreferences
import com.confused.anikuta.core.providerapi.InstallStep
import com.confused.anikuta.data.cloudstream.CloudstreamPluginManager
import com.confused.anikuta.data.cloudstream.model.CloudstreamExtension
import org.koin.compose.koinInject

/**
 * The CloudStream tab of the unified Extensions screen (doc 23 §5.4, gate G3-adjacent:
 * the user's unified-extensions-page decision). Sectioned exactly like the aniyomi
 * list above it: Installed → Failed to Load → Available. NSFW catalog entries are
 * gated by the persisted app toggle (G4 direction — default OFF).
 */
@Composable
fun CloudstreamExtensionsSection(
    csManager: CloudstreamPluginManager = koinInject(),
    appPreferences: AppPreferences = koinInject(),
) {
    val installed by csManager.installed.collectAsState()
    val errored by csManager.errored.collectAsState()
    val available by csManager.available.collectAsState()
    val installStates by csManager.installStates.collectAsState()
    val updateCheckState by csManager.updateCheckState.collectAsState()

    var showNsfw by remember { mutableStateOf(appPreferences.cloudstreamShowNsfw) }
    var uninstallTarget by remember { mutableStateOf<CloudstreamExtension?>(null) }

    val listState = rememberLazyListState()
    val isChecking = updateCheckState is CloudstreamPluginManager.UpdateCheckState.Checking

    val visibleAvailable = available.filter { showNsfw || !it.isNsfw }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // ── Installed ──
            item(key = "cs-header-installed", contentType = "csSectionHeader") {
                CsSectionHeader(
                    title = "Installed Plugins",
                    count = installed.size,
                    nsfwEnabled = showNsfw,
                    onToggleNsfw = {
                        showNsfw = !showNsfw
                        appPreferences.cloudstreamShowNsfw = showNsfw
                    },
                )
            }
            if (installed.isEmpty()) {
                item(key = "cs-installed-empty", contentType = "csSectionBody") {
                    CsEmptyBody("No CloudStream plugins installed. Add a repository and install one below.")
                }
            } else {
                items(
                    installed,
                    key = { "cs-installed-${it.internalName}" },
                    contentType = { "csInstalledRow" },
                ) { ext ->
                    CsInstalledRow(
                        extension = ext,
                        onToggleEnabled = { csManager.setEnabled(ext, !ext.isEnabled) },
                        onUninstall = { uninstallTarget = ext },
                        onUpdate = ext.availableUpdateVersion?.let { v ->
                            {
                                available.firstOrNull { it.plugin.internalName == ext.internalName }
                                    ?.let { csManager.installPlugin(it) }
                            }
                        },
                    )
                }
            }

            // ── Failed to Load (D-295/D-296 pattern — never silent) ──
            if (errored.isNotEmpty()) {
                item(key = "cs-header-errored", contentType = "csSectionHeader") {
                    CsSectionHeader(title = "Failed to Load", count = errored.size)
                }
                items(
                    errored,
                    key = { "cs-errored-${it.internalName}" },
                    contentType = { "csErroredRow" },
                ) { ext ->
                    CsErroredRow(
                        extension = ext,
                        onRetry = { csManager.retryPlugin(ext) },
                        onUninstall = { uninstallTarget = ext },
                    )
                }
            }

            // ── Available ──
            item(key = "cs-header-available", contentType = "csSectionHeader") {
                CsSectionHeader(title = "Available Plugins", count = visibleAvailable.size)
            }
            when {
                isChecking && visibleAvailable.isEmpty() -> item(key = "cs-available-loading", contentType = "csSectionBody") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                }
                visibleAvailable.isEmpty() -> item(key = "cs-available-empty", contentType = "csSectionBody") {
                    CsEmptyBody("No CloudStream plugins available. Add a CloudStream repository in repository settings.")
                }
                else -> items(
                    visibleAvailable,
                    key = { "cs-available-${it.plugin.internalName}" },
                    contentType = { "csAvailableRow" },
                ) { ext ->
                    CsAvailableRow(
                        extension = ext,
                        installStep = installStates[ext.plugin.internalName],
                        onInstall = { csManager.installPlugin(ext) },
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

    // Uninstall confirmation (destructive action — AlertDialog per house convention).
    uninstallTarget?.let { target ->
        val targetName = when (target) {
            is CloudstreamExtension.Installed -> target.name
            is CloudstreamExtension.Errored -> target.name
            is CloudstreamExtension.Available -> target.plugin.name
        }
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = {
                Text(
                    "Uninstall plugin?",
                    fontFamily = RobotoFamily,
                    fontWeight = FontWeight.ExtraBold,
                )
            },
            text = {
                Text(
                    "\"$targetName\" will be removed from this device.",
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    csManager.uninstallPlugin(target)
                    uninstallTarget = null
                }) {
                    Text("Uninstall", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) {
                    Text("Cancel", fontFamily = RobotoFamily)
                }
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Rows
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsInstalledRow(
    extension: CloudstreamExtension.Installed,
    onToggleEnabled: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: (() -> Unit)?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (extension.isDisabledByRepo) 0.6f else 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                        if (extension.providerCount > 0) append("  ·  ${extension.providerCount} provider${if (extension.providerCount == 1) "" else "s"}")
                        extension.repoName?.let { append("  ·  $it") }
                        if (extension.isDisabledByRepo) append("  ·  disabled by repository")
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (onUpdate != null) {
                TextButton(onClick = onUpdate, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text("Update", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
            Switch(
                checked = extension.isEnabled,
                onCheckedChange = { onToggleEnabled() },
                modifier = Modifier.padding(start = 4.dp),
            )
            IconButton(onClick = onUninstall, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Uninstall plugin",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CsErroredRow(
    extension: CloudstreamExtension.Errored,
    onRetry: () -> Unit,
    onUninstall: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name.removeSuffix("Provider"),
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "v${extension.version}",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Retry loading plugin",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onUninstall, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Uninstall plugin",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = extension.message,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CsAvailableRow(
    extension: CloudstreamExtension.Available,
    installStep: InstallStep?,
    onInstall: () -> Unit,
) {
    val plugin = extension.plugin
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CsPluginIcon(iconUrl = plugin.iconUrl, name = plugin.name)
            Spacer(Modifier.size(12.dp))
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
                    text = listOfNotNull(
                        "v${plugin.version}",
                        plugin.language,
                        plugin.fileSize?.let { formatKb(it) },
                        if (extension.isNsfw) "NSFW" else null,
                    ).joinToString("  ·  "),
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (!plugin.description.isNullOrBlank()) {
                    Text(
                        text = plugin.description!!,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // Install action — mirrors the aniyomi install-button state machine.
            AnimatedContent(
                targetState = installStep,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                label = "csInstallState",
            ) { step ->
                when (step) {
                    is InstallStep.Downloading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    ) {
                        if (step.progress >= 0) {
                            CircularProgressIndicator(
                                progress = { step.progress / 100f },
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    InstallStep.Installing -> CircularProgressIndicator(
                        modifier = Modifier.size(28.dp).padding(2.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    else -> IconButton(onClick = onInstall) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = "Install plugin",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Plugin icon via iconUrl (%size% substitution, doc 04 §3.3) with initial fallback. */
@Composable
private fun CsPluginIcon(iconUrl: String?, name: String) {
    val resolved = iconUrl?.replace("%size%", "64")?.replace("%exact_size%", "64")
    if (resolved != null) {
        AsyncImage(
            model = resolved,
            contentDescription = "$name icon",
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontFamily = RobotoFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// Section chrome
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsSectionHeader(
    title: String,
    count: Int,
    nsfwEnabled: Boolean? = null,
    onToggleNsfw: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "  $count",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (onToggleNsfw != null && nsfwEnabled != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                onClick = onToggleNsfw,
            ) {
                Text(
                    text = if (nsfwEnabled) "NSFW shown" else "NSFW hidden",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CsEmptyBody(message: String) {
    Text(
        text = message,
        fontFamily = RobotoFamily,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
    )
}

private fun formatKb(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576f)
    bytes >= 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
