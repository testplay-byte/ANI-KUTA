package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.common.Logger

/**
 * AniList OAuth2 configuration.
 *
 * AniList uses OAuth2 with a simple client ID + redirect URI flow.
 * The user opens a browser → authorizes → redirected back to the app.
 *
 * Register your app at https://anilist.co/settings/developer to get a client ID.
 * The redirect URI must match what's registered.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Tracker:AniList:OAuth".
 */
object AniListOAuth {

    private const val TAG = "Anikuta:Core:Tracker:AniList:OAuth"

    // ── Configuration ──
    // TODO: Move to BuildConfig or a config file (not hardcoded).
    // For now, these are placeholders — the user must register an AniList app
    // and replace these values.
    private const val CLIENT_ID = "YOUR_CLIENT_ID"
    private const val REDIRECT_URI = "anikuta://anilist-auth"
    private const val AUTH_URL = "https://anilist.co/api/v2/oauth/authorize"
    private const val TOKEN_URL = "https://anilist.co/api/v2/oauth/token"

    /**
     * Get the OAuth authorization URL.
     * The user opens this URL in a browser to authorize the app.
     */
    fun getAuthUrl(): String {
        val url = "$AUTH_URL?client_id=$CLIENT_ID&redirect_uri=$REDIRECT_URI&response_type=code"
        Logger.d(TAG) { "Auth URL generated" }
        return url
    }

    /**
     * Check if the client ID is configured (not the placeholder).
     */
    fun isConfigured(): Boolean {
        return CLIENT_ID != "YOUR_CLIENT_ID"
    }

    /**
     * Get the redirect URI (for intent filtering in AndroidManifest).
     */
    fun getRedirectUri(): String = REDIRECT_URI
}

/**
 * AniList API constants.
 */
object AniListApiConstants {
    const val GRAPHQL_URL = "https://graphql.anilist.co"
    const val API_URL = "https://anilist.co/api/v2"
}
