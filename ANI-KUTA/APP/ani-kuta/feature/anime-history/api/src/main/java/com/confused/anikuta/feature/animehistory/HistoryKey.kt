package com.confused.anikuta.feature.animehistory

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * NavKey for the History screen (Phase HI).
 *
 * Reached from the More screen → "History" row. Shows recently-watched episodes
 * grouped by day (Today / Yesterday / This Week / Earlier), with per-row
 * swipe-to-delete + a "Clear all" action.
 */
@Serializable
object HistoryKey : NavKey
