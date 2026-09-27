// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — the class whose absence broke StreamPlay / TorraStream /
// CineStream (NoClassDefFoundError at trust time). The census references:
//   • <init>(SyncAPI)          — plugins wrap OUR [AccountManager.aniListApi]
//   • authUser(): AuthUser     — the account read (upstream: inherited from
//     AuthRepo; declared directly here — no census plugin references AuthRepo)
//   • library-IoAF18A(...)     — the suspend library read returning
//     Result<SyncAPI.LibraryMetadata?>. The mangled suffix was verified
//     empirically (doc 79 §3): this exact declaration shape compiles to the
//     same library-IoAF18A JVM name the plugin dexes reference.
//
// Upstream's SyncRepo wraps each API call in runCatching and refreshes auth
// first; ours keeps the same contract but the inert APIs answer null, so the
// results carry the upstream "not logged in" shapes (success(null)).
package com.lagradost.cloudstream3.syncproviders

/** The safe wrapper plugins hold a [SyncAPI] through. */
open class SyncRepo(
    open val api: SyncAPI,
) {
    val syncIdName: SyncIdName? get() = api.syncIdName

    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) {
            api.requireLibraryRefresh = value
        }

    /** The account behind this repo — null when nobody is logged in (our steady state). */
    fun authUser(): AuthUser? = null

    /** The auth pair for API calls — null when nobody is logged in. */
    protected suspend fun freshAuth(): AuthData? = null

    suspend fun updateStatus(id: String, newStatus: SyncAPI.AbstractSyncStatus): Result<Boolean> =
        runCatching {
            api.updateStatus(freshAuth() ?: return@runCatching false, id, newStatus)
                .also { requireLibraryRefresh = true }
        }

    suspend fun status(id: String): Result<SyncAPI.AbstractSyncStatus?> = runCatching {
        api.status(freshAuth(), id)
    }

    suspend fun load(id: String): Result<SyncAPI.SyncResult?> = runCatching {
        api.load(freshAuth(), id)
    }

    suspend fun library(): Result<SyncAPI.LibraryMetadata?> = runCatching {
        api.library(freshAuth())
    }
}
