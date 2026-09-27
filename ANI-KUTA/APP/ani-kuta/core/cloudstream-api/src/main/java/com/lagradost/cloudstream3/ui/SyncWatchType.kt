// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — referenced by [com.lagradost.cloudstream3.syncproviders.SyncAPI]'s
// AbstractSyncStatus.status; nothing in the census touches it directly, but the
// member shape keeps the sync surface source-compatible with upstream plugins.
package com.lagradost.cloudstream3.ui

/** The watch states a sync service tracks (upstream's AniList-aligned set). */
enum class SyncWatchType(val status: Int) {
    PLANNING(0),
    WATCHING(1),
    COMPLETED(2),
    DROPPED(3),
    PAUSED(4),
    REWATCHING(5),
}
