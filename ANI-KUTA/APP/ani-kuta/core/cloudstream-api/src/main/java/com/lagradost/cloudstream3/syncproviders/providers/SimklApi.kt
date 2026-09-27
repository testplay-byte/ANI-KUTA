// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 98 (D-670) — the v1.1.54 device round caught CineStream dying at
// CineSimklProvider.<init> line 56 with
//   NoSuchMethodError: No virtual method getSimklApi()
//     Lcom/lagradost/cloudstream3/syncproviders/providers/SimklApi;
//     in class Lcom/lagradost/cloudstream3/syncproviders/AccountManager$Companion;
// — the plugin grabs the shared Simkl sync instance and wraps it in a SyncRepo:
//   private val repo = SyncRepo(AccountManager.simklApi)
// The round-97 census (D-663) mirrored the companion's aniListApi but no census
// plugin referenced simklApi, so it was (correctly) left out — CineStream now
// proves real plugins read it too. Census of what the plugin does AFTER the
// grab (source: SaurabhKaperwan/CSX, CineSimklProvider.kt): repo.authUser()
// and repo.library() only — both land on the inert SyncRepo base (null /
// "not logged in" shapes), so the class itself can be INERT exactly like
// AniListApi. The plugin's BuildConfig.SIMKL_CLIENT_ID / SIMKL_API reads
// compile to INLINED string constants (Java static-final literals are
// compile-time constants in Kotlin too — no runtime BuildConfig reference
// exists in the plugin dex), so no BuildConfig mirror is needed.
package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName

/**
 * The inert Simkl sync API (see the file header) — the anchor
 * [com.lagradost.cloudstream3.syncproviders.AccountManager.simklApi] hands
 * plugins. Every member answers the upstream "not logged in" shape so a
 * plugin's sync-powered views render their logged-out state.
 */
open class SimklApi : SyncAPI() {
    override val mainUrl = "https://simkl.com"
    override val syncIdName: SyncIdName = SyncIdName.Simkl
}
