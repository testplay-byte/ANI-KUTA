package com.confused.anikuta.settings.search

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * D-558 — the search → screen hand-off. When a search result is tapped, the
 * navigation layer (MainActivity) calls [request] with the destination page
 * and the row anchor, then pushes the backstack keys; the destination screen
 * calls [takeAnchor] ONCE on entry to learn whether IT is the highlight
 * target (and to consume the pending request).
 *
 * A plain process-wide holder is the right size here: the pending anchor is
 * single-shot, in-memory, and meaningless across process death (a dead
 * highlight is a no-op, not a crash). The [takeAnchor] page check makes
 * chained navigation safe — pushing Appearance → General consumes the anchor
 * on General and leaves Appearance clean.
 */
object SettingsSearchNavigator {

    private val pending = MutableStateFlow<Pair<SettingsSearchPage, String>?>(null)

    val pendingRequest: StateFlow<Pair<SettingsSearchPage, String>?> = pending.asStateFlow()

    /** The search result tap → "navigate to [page] and highlight [anchor]". */
    fun request(page: SettingsSearchPage, anchor: String?) {
        pending.value = if (anchor.isNullOrBlank()) null else page to anchor
    }

    /**
     * The destination screen's consume-once read: the anchor ONLY when the
     * pending request targets [page]. A mismatched read leaves the request
     * intact for the screen it belongs to.
     */
    fun takeAnchor(page: SettingsSearchPage): String? {
        val current = pending.value ?: return null
        if (current.first != page) return null
        pending.value = null
        return current.second
    }
}
