package com.confused.anikuta.feature.updates

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * NavKey for the Updates screen (Phase UP).
 *
 * Reached from the More screen → "Updates" row. Shows new-episode releases
 * for the user's library. Will host the Updates | Schedule tab strip
 * (Schedule added in Phase SC).
 */
@Serializable
object UpdatesKey : NavKey
