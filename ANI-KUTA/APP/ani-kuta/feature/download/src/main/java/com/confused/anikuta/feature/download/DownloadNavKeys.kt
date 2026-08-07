package com.confused.anikuta.feature.download

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * NavKey for the Downloads screen.
 *
 * D.6: Single-object key — the Downloads screen takes no parameters (it observes
 * the live [com.confused.anikuta.core.download.DownloadManager] queue directly).
 *
 * @Serializable with kotlinx.serialization for Nav3.
 */
@Serializable
object DownloadsKey : NavKey

/**
 * NavKey for the Downloaded Files screen.
 *
 * D.6: Reached from the Downloads screen's "Downloaded" icon.
 */
@Serializable
object DownloadedFilesKey : NavKey

/**
 * NavKey for the Download Settings screen.
 *
 * D.6: Reached from the Downloads screen's settings gear OR from the main
 * Settings screen.
 */
@Serializable
object DownloadSettingsKey : NavKey
