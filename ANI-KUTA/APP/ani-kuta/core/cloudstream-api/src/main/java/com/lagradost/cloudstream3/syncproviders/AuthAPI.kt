// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — WHY THIS FILE EXISTS: the v1.1.53 device round caught
// StreamPlay / TorraStream / CineStream dying with
// NoClassDefFoundError: com/lagradost/cloudstream3/syncproviders/SyncRepo —
// phisher98's extensions register their AniList-powered library views through
// the syncproviders surface. The dex census (doc 79 §2) of those .cs3 files
// shows the exact referenced member set; every declaration below mirrors those
// interop facts, and every implementation is inert (no accounts, no network) —
// the app's own AniList integration is untouched.
package com.lagradost.cloudstream3.syncproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.unixTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A logged-in account's display identity (interop shape: name / id /
 * profilePicture / profilePictureHeaders). Plugins read it from
 * [SyncRepo.authUser]; our inert implementation always answers null
 * ("not logged in"), exactly like upstream behaves with no account attached.
 */
@Serializable
data class AuthUser(
    @JsonProperty("name") @SerialName("name") val name: String?,
    @JsonProperty("id") @SerialName("id") val id: Int,
    @JsonProperty("profilePicture") @SerialName("profilePicture") val profilePicture: String? = null,
    @JsonProperty("profilePictureHeaders") @SerialName("profilePictureHeaders")
    val profilePictureHeaders: Map<String, String>? = null,
)

/**
 * The token pair a sync service hands out (interop shape). Kept as data only —
 * our inert sync APIs never mint or refresh tokens.
 */
@Serializable
data class AuthToken(
    @JsonProperty("accessToken") @SerialName("accessToken") val accessToken: String? = null,
    @JsonProperty("refreshToken") @SerialName("refreshToken") val refreshToken: String? = null,
    /** UnixTime (sec) when the access token expires. */
    @JsonProperty("accessTokenLifetime") @SerialName("accessTokenLifetime")
    val accessTokenLifetime: Long? = null,
    /** UnixTime (sec) when the refresh token expires. */
    @JsonProperty("refreshTokenLifetime") @SerialName("refreshTokenLifetime")
    val refreshTokenLifetime: Long? = null,
    /** Catch-all payload slot some services use for username/password storage. */
    @JsonProperty("payload") @SerialName("payload") val payload: String? = null,
) {
    fun isAccessTokenExpired(marginSec: Long = 10L) =
        accessTokenLifetime != null && unixTime + marginSec >= accessTokenLifetime

    fun isRefreshTokenExpired(marginSec: Long = 10L) =
        refreshTokenLifetime != null && unixTime + marginSec >= refreshTokenLifetime
}

/** The account + token pair the sync APIs take (our implementations always receive null). */
@Serializable
data class AuthData(
    @JsonProperty("user") @SerialName("user") val user: AuthUser,
    @JsonProperty("token") @SerialName("token") val token: AuthToken,
)
