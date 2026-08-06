package com.confused.anikuta.feature.animelibrary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * D-143: Holds the library selection mode state so AppRoot can access it.
 *
 * The [LibraryScreen] sets [isSelectionMode] + the callbacks when the user
 * enters/exists selection mode. [AppRoot] reads [isSelectionMode] to decide
 * whether to show the normal nav bar or the selection action bar.
 *
 * Uses a static CompositionLocal so we don't need to thread the state through
 * every composable — AppRoot just reads [LocalLibrarySelectionMode].
 */
class LibrarySelectionMode {
    var isSelectionMode: Boolean by mutableStateOf(false)
        private set

    var selectedCount: Int by mutableStateOf(0)
        private set

    var onCancel: (() -> Unit)? = null
    var onCategory: (() -> Unit)? = null
    var onDelete: (() -> Unit)? = null

    fun enter(count: Int, cancel: () -> Unit, category: () -> Unit, delete: () -> Unit) {
        isSelectionMode = true
        selectedCount = count
        onCancel = cancel
        onCategory = category
        onDelete = delete
    }

    fun updateCount(count: Int) {
        selectedCount = count
    }

    fun exit() {
        isSelectionMode = false
        selectedCount = 0
        onCancel = null
        onCategory = null
        onDelete = null
    }
}

val LocalLibrarySelectionMode = staticCompositionLocalOf { LibrarySelectionMode() }
