// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
package com.lagradost.cloudstream3.syncproviders

/** Sync service identity enum (MainAPI.supportedSyncNames / getLoadUrl surface). */
enum class SyncIdName {
    Anilist,
    MyAnimeList,
    Kitsu,
    Trakt,
    Imdb,
    Simkl,
    LocalList,
}
