package com.confused.anikuta.core.appupdate

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GitHub Releases-based update source.
 *
 * # How it works
 *
 * 1. Fetches the release list from the GitHub API:
 *    `GET https://api.github.com/repos/{owner}/{repo}/releases?per_page={n}`
 *    (D-251: previously used `/releases/latest`, which EXCLUDES releases flagged
 *    as prerelease — every release after v0.2.6 was flagged prerelease, so the
 *    in-app "Check for Updates" never saw them and users on old builds were told
 *    they were up to date. The list endpoint returns prereleases too.)
 * 2. Filters out drafts (defensively — the unauthenticated API omits them anyway).
 * 3. Picks the best release: the highest version; when two releases share a
 *    version, a stable (non-prerelease) one wins over a prerelease one.
 * 4. Parses the release for:
 *    - `tag_name` → version name (strips `v` prefix)
 *    - `name` → release name
 *    - `body` → changelog
 *    - `published_at` → release date (ISO 8601 → epoch ms)
 *    - `assets` → finds the first `.apk` asset → `browser_download_url` + `size`
 * 5. Compares the release's version with the installed version (tuple compare).
 *    If newer → returns [AppUpdateInfo].
 *
 * # Version comparison
 *
 * GitHub releases use semantic versioning (`vMAJOR.MINOR.PATCH`). D-251: the
 * old `major * 10000 + minor * 100 + patch` packing breaks once PATCH reaches
 * 100 (e.g. "0.2.100" would equal "0.3.0"). Versions are now compared as
 * integer tuples (major, minor, patch) lexicographically — correct for any
 * component values. The [AppUpdateInfo.versionCode] field is derived with
 * wide multipliers (major * 1_000_000 + minor * 10_000 + patch) and is
 * informational only.
 *
 * If the version name can't be parsed, the release is skipped.
 *
 * # Rate limiting
 *
 * The GitHub API has a rate limit of 60 requests/hour for unauthenticated
 * requests. This is sufficient for an app that checks once on startup. If
 * more frequent checks are needed, add a GitHub token via the `Authorization`
 * header (raises to 5000/hour).
 *
 * @param owner the GitHub repo owner (e.g., "testplay-byte").
 * @param repo the GitHub repo name (e.g., "ANI-KUTA").
 * @param client the OkHttp client (shared with the app for connection pooling).
 */
class GitHubUpdateSource(
    private val owner: String,
    private val repo: String,
    private val client: OkHttpClient,
) : UpdateSource {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override val id: String = "github"

    override suspend fun fetchLatestUpdate(
        currentVersionCode: Long,
        currentVersionName: String,
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases?per_page=$RELEASE_PAGE_SIZE"
        Logger.i(TAG) { "fetchLatestUpdate: GET $url (current=$currentVersionName/$currentVersionCode)" }

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "ANIKUTA-App-Update-Checker")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Logger.w(TAG) { "fetchLatestUpdate: HTTP ${response.code} ${response.message}" }
                response.close()
                return@withContext null
            }

            val body = response.body?.string() ?: run {
                Logger.w(TAG) { "fetchLatestUpdate: empty response body" }
                return@withContext null
            }

            val releases = try {
                json.decodeFromString<List<GitHubRelease>>(body)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "fetchLatestUpdate: failed to parse JSON" }
                return@withContext null
            }

            // D-251: pick the best release from the list. Drafts are excluded
            // (they're not visible to unauthenticated clients either). Among the
            // rest, highest version wins; a stable release beats a prerelease at
            // the same version.
            val best = releases
                .asSequence()
                .filter { it.draft != true }
                .mapNotNull { release ->
                    val versionName = release.tagName.removePrefix("v").removePrefix("V").trim()
                    if (versionName.isBlank()) return@mapNotNull null
                    val parsed = parseVersionTuple(versionName) ?: return@mapNotNull null
                    Triple(release, versionName, parsed)
                }
                .maxWithOrNull(
                    compareBy(
                        { it.third },
                        { if (it.first.prerelease == true) 0 else 1 },
                    )
                )

            if (best == null) {
                Logger.w(TAG) { "fetchLatestUpdate: no usable releases found (${releases.size} fetched)" }
                return@withContext null
            }

            val (release, versionName, parsed) = best
            if (release.prerelease == true) {
                Logger.i(TAG) { "fetchLatestUpdate: best release ${release.tagName} is a prerelease (using it — /releases/latest-style filtering hid these before D-251)" }
            }

            // Check if this is the same version or older than what the user has.
            val currentParsed = parseVersionTuple(currentVersionName)
            if (versionName == currentVersionName ||
                (currentParsed != null && parsed <= currentParsed)
            ) {
                Logger.i(TAG) {
                    "fetchLatestUpdate: no update available " +
                        "(latest=$versionName/$parsed, current=$currentVersionName/$currentParsed, " +
                        "sameVersion=${versionName == currentVersionName})"
                }
                return@withContext null
            }

            // Find the first .apk asset.
            val apkAsset = release.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            if (apkAsset == null) {
                Logger.w(TAG) { "fetchLatestUpdate: no APK asset in release ${release.tagName}" }
                return@withContext null
            }

            val releaseDate = parseIsoDate(release.publishedAt)

            Logger.i(TAG) {
                "fetchLatestUpdate: update available! " +
                    "$versionName/$parsed (apk=${apkAsset.name}, ${apkAsset.size} bytes, " +
                    "prerelease=${release.prerelease == true})"
            }

            AppUpdateInfo(
                versionName = versionName,
                versionCode = versionCodeFromTuple(parsed),
                downloadUrl = apkAsset.browserDownloadUrl,
                changelog = release.body ?: "No changelog provided.",
                releaseDate = releaseDate,
                source = id,
                apkSizeBytes = apkAsset.size,
                releaseName = release.name,
            )
        } catch (e: Exception) {
            Logger.e(TAG, e) { "fetchLatestUpdate: network error" }
            null
        }
    }

    /**
     * Parses a semantic version string ("MAJOR.MINOR.PATCH") into a comparable
     * [VersionTuple].
     *
     * Handles pre-release suffixes (e.g., "1.0.0-beta1" → (1, 0, 0), ignoring
     * the suffix). Returns null if parsing fails.
     */
    private fun parseVersionTuple(versionName: String): VersionTuple? {
        val cleanName = versionName.substringBefore("-").substringBefore("+").trim()
        val parts = cleanName.split(".")
        if (parts.isEmpty()) return null
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: return null
        return VersionTuple(major, minor, patch)
    }

    /** Packs a version tuple into a single informational Long (wide multipliers). */
    private fun versionCodeFromTuple(t: VersionTuple): Long =
        t.major.toLong() * 1_000_000L + t.minor.toLong() * 10_000L + t.patch.toLong()

    /** Parses an ISO 8601 date string (e.g., "2025-01-15T10:30:00Z") to epoch ms. */
    private fun parseIsoDate(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            // Use java.time (available on API 26+, which is our minSdk).
            java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (e2: Exception) {
                Logger.w(TAG, e2) { "parseIsoDate: failed to parse '$iso'" }
                0L
            }
        }
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val assets: List<GitHubAsset>? = null,
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
    )

    /**
     * A parsed (major, minor, patch) version — lexicographically comparable.
     * (Kotlin's Triple/Pair do NOT implement Comparable, hence this class.)
     */
    private data class VersionTuple(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Comparable<VersionTuple> {
        override fun compareTo(other: VersionTuple): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }
    }

    @Serializable
    private data class GitHubAsset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val browserDownloadUrl: String,
    )

    private companion object {
        private const val TAG = "Anikuta:Core:AppUpdate:GitHub"

        /** How many recent releases to scan for the best version. */
        private const val RELEASE_PAGE_SIZE = 30
    }
}
