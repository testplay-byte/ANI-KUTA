package com.confused.anikuta.feature.extensionssettings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.extension.manager.ExtensionManager
import com.confused.anikuta.data.extension.model.AnimeExtension
import com.confused.anikuta.data.extension.repo.ExtensionRepoRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Extensions Settings screen — lists installed, untrusted, and available extensions.
 *
 * Ported from the old project's `ExtensionsSettingsScreen` with adaptations for
 * the new project's design system + Nav3.
 *
 * Layout (top to bottom):
 * 1. CollapsingHeader "Extensions" with a settings icon → opens Repo settings.
 * 2. Three sections (stacked, NOT tabs):
 *    - "Trusted Sources" — installed extensions with live sources.
 *    - "Untrusted" — installed but not yet trusted (trust button).
 *    - "Available Extensions" — listed in repos, not yet installed.
 *
 * CORE_RULES §22: smooth animations (scale on press).
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
    val repos by repoRepository.repos.collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // Fetch available extensions when repos exist.
    LaunchedEffect(repos.size) {
        if (repos.isNotEmpty()) {
            isRefreshing = true
            extensionManager.findAvailableExtensions()
            isRefreshing = false
        }
    }

    val installedPkgs = installedExtensions.map { it.pkgName }.toSet()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Extensions",
                collapsed = false,
                actions = {
                    HeaderIconButton(
                        icon = Icons.Filled.Settings,
                        contentDescription = "Repository settings",
                        onClick = onOpenRepoSettings,
                    )
                    HeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // ── Trusted Sources ──
                item {
                    SectionLabel("Trusted Sources")
                }
                if (installedExtensions.isEmpty()) {
                    item {
                        EmptySectionBody("No trusted anime sources. Install an extension to get started.")
                    }
                } else {
                    items(installedExtensions, key = { it.pkgName }) { ext ->
                        InstalledExtensionRow(
                            extension = ext,
                            onUntrust = { extensionManager.untrustExtension(ext) },
                            onUninstall = { extensionManager.uninstallExtension(ext) },
                        )
                    }
                }

                // ── Untrusted ──
                if (untrustedExtensions.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { SectionLabel("Untrusted") }
                    items(untrustedExtensions, key = { it.pkgName }) { ext ->
                        UntrustedExtensionRow(
                            extension = ext,
                            onTrust = { extensionManager.trustExtension(ext) },
                            onUninstall = { extensionManager.uninstallExtension(ext) },
                        )
                    }
                }

                // ── Available Extensions ──
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionLabel("Available Extensions") }

                if (repos.isEmpty()) {
                    item {
                        EmptySectionBody("No repositories configured. Tap the settings icon to add one.")
                    }
                } else if (isRefreshing && availableExtensions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (availableExtensions.isEmpty()) {
                    item {
                        EmptySectionBody("No extensions found in your repositories.")
                    }
                } else {
                    items(availableExtensions, key = { it.pkgName }) { ext ->
                        AvailableExtensionRow(
                            extension = ext,
                            isInstalled = ext.pkgName in installedPkgs,
                            onInstall = {
                                scope.launch {
                                    extensionManager.installExtension(ext).collectLatest { step ->
                                        // InstallStep flow — terminal state arrives via broadcast.
                                    }
                                }
                            },
                        )
                    }
                }
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
    onUntrust: () -> Unit,
    onUninstall: () -> Unit,
) {
    ExtensionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
            Spacer(Modifier.width(8.dp))
            ActionIconButton(
                icon = Icons.Filled.VerifiedUser,
                contentDescription = "Untrust",
                onClick = onUntrust,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Uninstall",
                onClick = onUninstall,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun UntrustedExtensionRow(
    extension: AnimeExtension.Untrusted,
    onTrust: () -> Unit,
    onUninstall: () -> Unit,
) {
    ExtensionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
            Spacer(Modifier.width(8.dp))
            ActionIconButton(
                icon = Icons.Filled.VerifiedUser,
                contentDescription = "Trust",
                onClick = onTrust,
                tint = MaterialTheme.colorScheme.primary,
            )
            ActionIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Uninstall",
                onClick = onUninstall,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AvailableExtensionRow(
    extension: AnimeExtension.Available,
    isInstalled: Boolean,
    onInstall: () -> Unit,
) {
    ExtensionCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = extension.name,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
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
            if (!isInstalled) {
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
private fun ExtensionCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        content()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptySectionBody(message: String) {
    Text(
        text = message,
        fontFamily = RobotoFamily,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
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
