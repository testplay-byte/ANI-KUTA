// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — the census (StreamPlay, TorraStream) reads exactly one
// member: AccountManager.Companion.getAniListApi() — plugins grab the shared
// AniListApi instance and wrap it in a SyncRepo. Upstream's companion also
// carries MAL/Kitsu/Simkl/local-list instances, an accounts cache backed by
// DataStore keys and an allApis registry; NONE of it was referenced by the
// census, and wiring our own account storage in here would couple the inert
// compat layer to the app's data stack — so the companion exposes the one
// instance the plugins need (documented divergence, doc 79 §4).
//
// ROUND 98 (D-670) — CineStream proved the Simkl twin is real-plugin surface
// too (CineSimklProvider's constructor dies on getSimklApi() — see
// providers/SimklApi.kt's header), so the companion now carries BOTH inert
// instances the ecosystem has been observed to grab.
package com.lagradost.cloudstream3.syncproviders

import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi

/**
 * The account-manager anchor plugins reference. Abstract upstream; kept
 * abstract here so plugins subclassing it (none in the census) still link.
 */
abstract class AccountManager {
    companion object {
        /** The shared inert AniList API the census plugins wrap in a [SyncRepo]. */
        val aniListApi: AniListApi = AniListApi()

        /** The shared inert Simkl API CineStream wraps in a [SyncRepo] (D-670). */
        val simklApi: SimklApi = SimklApi()
    }
}
