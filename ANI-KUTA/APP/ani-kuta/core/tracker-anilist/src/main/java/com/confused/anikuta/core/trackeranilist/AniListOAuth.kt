package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.common.Logger

/**
 * AniList OAuth2 configuration.
 *
 * D-220: Implemented the OAuth2 Implicit Grant flow (same as AniMiru/Aniyomi).
 * The user opens a browser → authorizes → AniList redirects to
 * `anikuta://anilist-auth#access_token=...&expires_in=31536000&token_type=bearer`.
 * The token is in the URL **fragment** (not query), valid for ~1 year.
 * No client_secret, no PKCE, no token exchange POST — simplest flow.
 *
 * **Configuration:**
 * - CLIENT_ID = 48714 (registered at https://anilist.co/settings/developer)
 * - REDIRECT_URI = `anikuta://anilist-auth` (must be set on the AniList developer portal)
 * - The user must set this exact redirect URI on the AniList developer settings page.
 *
 * **Manifest:**
 * - An intent-filter for `anikuta://anilist-auth` is registered on MainActivity
 *   (D-220) to catch the OAuth redirect.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Tracker:AniList:OAuth".
 */
object AniListOAuth {

    private const val TAG = "Anikuta:Core:Tracker:AniList:OAuth"

    // ── Configuration ──
    // D-220: real CLIENT_ID from the user's AniList app registration.
    // To change later: update this constant + the redirect URI on the AniList
    // developer portal (https://anilist.co/settings/developer).
    internal const val CLIENT_ID = "48714"
    internal const val REDIRECT_URI = "anikuta://anilist-auth"
    internal const val AUTH_URL = "https://anilist.co/api/v2/oauth/authorize"
    internal const val TOKEN_URL = "https://anilist.co/api/v2/oauth/token"

    // Token expiry: AniList implicit-grant tokens last ~1 year (31536000 seconds).
    internal const val TOKEN_EXPIRY_MS = 31536000000L // 365 days in milliseconds.

    /**
     * Get the OAuth authorization URL for the Implicit Grant flow.
     *
     * The user opens this URL in a browser. After login, AniList redirects to
     * `anikuta://anilist-auth#access_token=...&expires_in=...&token_type=bearer`.
     * The token is in the URL fragment (NOT query).
     */
    fun getAuthUrl(): String {
        val url = "$AUTH_URL?client_id=$CLIENT_ID&response_type=token"
        Logger.i(TAG) { "Auth URL generated (implicit grant, client_id=$CLIENT_ID)" }
        return url
    }

    /**
     * Check if the client ID is configured (not the placeholder).
     */
    fun isConfigured(): Boolean = CLIENT_ID != "YOUR_CLIENT_ID"

    /**
     * Get the redirect URI (for intent filtering in AndroidManifest).
     * The user must set this exact value on the AniList developer portal.
     */
    fun getRedirectUri(): String = REDIRECT_URI

    /**
     * Parse the access_token from the OAuth redirect URL fragment.
     * AniList implicit grant returns: `anikuta://anilist-auth#access_token=...&expires_in=...`
     *
     * @param fragment the URL fragment (after #), e.g. "access_token=xxx&expires_in=31536000&token_type=bearer"
     * @return the access token, or null if not found.
     */
    fun parseAccessToken(fragment: String): String? {
        if (fragment.isBlank()) return null
        // Parse "access_token=xxx&expires_in=31536000&token_type=bearer"
        val params = fragment.split("&").mapNotNull { pair ->
            val idx = pair.indexOf("=")
            if (idx > 0) pair.substring(0, idx) to pair.substring(idx + 1)
            else null
        }.toMap()
        val token = params["access_token"]
        if (token.isNullOrBlank()) {
            Logger.w(TAG) { "OAuth redirect fragment has no access_token: ${fragment.take(50)}..." }
            return null
        }
        Logger.i(TAG) { "Access token parsed from fragment (length=${token.length})" }
        return token
    }
}

/**
 * AniList API constants.
 */
object AniListApiConstants {
    const val GRAPHQL_URL = "https://graphql.anilist.co"
    const val API_URL = "https://anilist.co/api/v2"
}
