package com.confused.anikuta.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * ROUND 121: the AniSkip client — the round's order replaced hand-marked
 * intro/outro stamps with a proper skip-time SERVICE ("utilize services like
 * AnySkip… properly provide the intro and outro data").
 *
 * AniSkip (https://aniskip.com) keys anime by **MAL id**, while this app
 * keys content by AniList id — so the client resolves AniList → MAL through
 * the public AniList GraphQL `Media.idMal` field (the same conversion the
 * ecosystem's AniSkip integrations use) and caches the mapping for the
 * process lifetime, negatives included (a mapping miss is a miss forever —
 * no point re-querying).
 *
 * The skip times themselves are fetched per episode with the KNOWN episode
 * length (AniSkip's results improve with it). One fetch per episode; the
 * CALLER caches the parsed result (the player persists it per episode in
 * PlayerPreferences) so replays never re-hit the network.
 *
 * Parsing uses org.json (an Android library module — no serialization
 * dependency needed) and is defensive throughout: a malformed payload is a
 * null, never a crash.
 */
class AniSkipClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {

    /** One skip interval, in whole seconds. */
    data class Interval(val startSec: Int, val endSec: Int) {
        val durationSec: Int get() = endSec - startSec
    }

    /**
     * The per-episode skip data. Either side may be null (AniSkip knows the
     * op but not the ed for plenty of episodes — and vice versa).
     */
    data class SkipTimes(
        val intro: Interval?,
        val outro: Interval?,
        /** True when the data came from AniSkip (vs. a null for "no data"). */
        val fromService: Boolean,
    )

    /**
     * ROUND 130 (the device report: "the seek bar does not show the proper
     * intro and outro bars"): the fetch result now DISTINGUISHES the three
     * outcomes, because only one of them may latch the per-episode negative
     * cache:
     *  - [Found] — the service answered with intervals.
     *  - [NotFound] — the service answered (HTTP 200, `found:false` or no
     *    usable intervals): a DEFINITIVE miss for this episode; the caller
     *    may latch it.
     *  - [Error] — network/transport/parse trouble (the old code collapsed
     *    this into the same null as NotFound, and the caller latched BOTH —
     *    one flaky fetch permanently silenced an episode's skip data).
     */
    sealed class FetchResult {
        data class Found(val skip: SkipTimes) : FetchResult()
        object NotFound : FetchResult()
        object Error : FetchResult()
    }

    /** AniList id → MAL id (absent = resolved-and-missing). */
    private val malIdCache = ConcurrentHashMap<Int, Optional<Int>>()

    /**
     * Fetches the op/ed skip times for one episode. The three-way
     * [FetchResult] says which outcome it was (see the type's doc) — the
     * caller falls back to any manual timestamps on every non-Found.
     *
     * ROUND 122 (R5): [episodeLengthSec] is SECONDS (the AniSkip v2 contract —
     * the round-121 code sent milliseconds; masked only because duration was
     * never known at fetch time).
     */
    suspend fun fetchSkipTimes(
        anilistId: Int,
        episodeNumber: Int,
        episodeLengthSec: Long,
    ): FetchResult {
        // ROUND 130 (the verifier's Defect 1): resolveMalId returns null BOTH
        // for a definitive "no MAL twin" (cached) and for a TRANSIENT failure
        // (an exception / non-200 from the AniList hop — deliberately not
        // cached). Only the cached negative may latch; a transient one is
        // Error so the next episode open retries instead of silencing the
        // episode forever.
        val malId = resolveMalId(anilistId)
            ?: return if (malIdCache.containsKey(anilistId)) {
                FetchResult.NotFound
            } else {
                FetchResult.Error
            }
        return fetchSkipTimesByMal(malId, episodeNumber, episodeLengthSec)
    }

    /**
     * The direct path when the caller already knows the MAL id (this app
     * stores it on content details when the source provided it) — skips the
     * AniList GraphQL resolution entirely.
     */
    suspend fun fetchSkipTimesByMal(
        malId: Int,
        episodeNumber: Int,
        episodeLengthSec: Long,
    ): FetchResult = withContext(Dispatchers.IO) {
        if (malId <= 0) return@withContext FetchResult.Error
        suspend fun buildUrl(episodeLengthSec: Long) = "https://api.aniskip.com/api/v2/skip-times/$malId/$episodeNumber"
            .toHttpUrl()
            .newBuilder()
            // ROUND 130 (the provider research): mixed-op/mixed-ed are real
            // submission types the old query dropped — mixed-eds are common
            // late-series outros, and every dropped type is a missing band.
            .addQueryParameter("types", "op")
            .addQueryParameter("types", "mixed-op")
            .addQueryParameter("types", "ed")
            .addQueryParameter("types", "mixed-ed")
            .apply {
                // The known length sharpens the results; omit when unknown.
                if (episodeLengthSec > 0) addQueryParameter("episodeLength", episodeLengthSec.toString())
            }
            .build()
        fun call(url: okhttp3.HttpUrl) = runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute()
        }.getOrNull()

        val first = call(buildUrl(episodeLengthSec))
            ?: return@withContext FetchResult.Error
        first.use { resp ->
            if (!resp.isSuccessful) return@withContext FetchResult.Error
            val body = runCatching { resp.body?.string() }.getOrNull()
                ?: return@withContext FetchResult.Error
            when (val parsed = parseSkipTimes(body)) {
                is Parsed.Found -> FetchResult.Found(parsed.skip)
                is Parsed.DefinitiveMiss -> {
                    // ROUND 130 (the provider research): AniSkip answers the
                    // SAME 404 for "no submission" AND "wrong episodeLength" —
                    // a stream that cuts differently from the listing would
                    // poison the episode as a miss. Retry ONCE without the
                    // length (the parameter is optional): the server then
                    // matches any submission for the episode.
                    if (episodeLengthSec > 0) {
                        val retry = call(buildUrl(0L))
                            ?: return@use FetchResult.Error
                        retry.use { resp2 ->
                            // ROUND 130 (the verifier's Defect 2): the RETRY's
                            // own failures are transient — Error, never
                            // NotFound (only the server's explicit "no data"
                            // for this episode may latch).
                            if (!resp2.isSuccessful) return@use FetchResult.Error
                            val body2 = runCatching { resp2.body?.string() }.getOrNull()
                                ?: return@use FetchResult.Error
                            when (val parsed2 = parseSkipTimes(body2)) {
                                is Parsed.Found -> FetchResult.Found(parsed2.skip)
                                is Parsed.DefinitiveMiss -> FetchResult.NotFound
                                is Parsed.Malformed -> FetchResult.Error
                            }
                        }
                    } else {
                        FetchResult.NotFound
                    }
                }
                is Parsed.Malformed -> FetchResult.Error
            }
        }
    }

    /** The parser's three-way outcome (ROUND 130 — see [FetchResult]). */
    private sealed class Parsed {
        data class Found(val skip: SkipTimes) : Parsed()
        object DefinitiveMiss : Parsed()
        object Malformed : Parsed()
    }

    /** org.json parsing — every field guarded; malformed → [Parsed.Malformed]. */
    private fun parseSkipTimes(body: String): Parsed = runCatching {
        val root = JSONObject(body)
        if (!root.optBoolean("found", false)) return@runCatching Parsed.DefinitiveMiss
        var intro: Interval? = null
        var outro: Interval? = null
        val results = root.optJSONArray("results") ?: return@runCatching Parsed.Malformed
        for (i in 0 until results.length()) {
            val entry = results.optJSONObject(i) ?: continue
            val interval = entry.optJSONObject("interval") ?: continue
            val start = interval.optDouble("startTime", -1.0)
            val end = interval.optDouble("endTime", -1.0)
            if (start < 0 || end <= start) continue
            val parsed = Interval(start.toInt(), end.toInt())
            when (entry.optString("skipType")) {
                "op", "mixed-op" -> if (intro == null) intro = parsed
                // ROUND 130: mixed-ed too (a late-series ED overlapped with
                // the episode's last scenes) — the old parser dropped it.
                "ed", "mixed-ed" -> if (outro == null) outro = parsed
                // "recap" skips exist too — deliberately ignored until the
                // user asks for them.
            }
        }
        if (intro == null && outro == null) return@runCatching Parsed.DefinitiveMiss
        Parsed.Found(SkipTimes(intro = intro, outro = outro, fromService = true))
    }.getOrElse { Parsed.Malformed }

    /**
     * AniList → MAL via the public GraphQL endpoint. Cached, definitive
     * negatives included.
     *
     * ROUND 122 (R5) — THE primary AniSkip break: the round-121 code sent the
     * query as GET query parameters, and AniList REJECTS GET on graphql.anilist.co
     * with HTTP 404 ("Use POST request to access graphql subdomain") — every
     * mapping failed, every popular anime got no skip data (verified live).
     * This is now a POST with the JSON body AniList requires. A network/HTTP
     * failure is NOT cached (the negative cache holds only definitive
     * "this anime has no MAL id" answers), so a transient failure retries on
     * the next episode open.
     */
    private fun resolveMalId(anilistId: Int): Int? {
        malIdCache[anilistId]?.let { return it.orElse(null) }
        var reachedAniList = false
        val resolved: Int? = runCatching {
            val body = "{\"query\":\"query { Media(id: $anilistId) { idMal } }\"}"
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { resp ->
                reachedAniList = resp.isSuccessful
                if (!resp.isSuccessful) return@runCatching null
                val text = resp.body?.string() ?: return@runCatching null
                JSONObject(text)
                    .optJSONObject("data")
                    ?.optJSONObject("Media")
                    ?.takeIf { !it.isNull("idMal") }
                    ?.optInt("idMal")
            }
        }.getOrNull()
        // Cache ONLY definitive answers: an HTTP-200 response with no Media row
        // (or a null idMal) means "no MAL mapping" — cache it. An exception or
        // a failed response must stay un-cached so the next open retries.
        if (reachedAniList) {
            malIdCache[anilistId] = Optional.ofNullable(resolved)
        }
        return resolved
    }

    private companion object {
        private const val USER_AGENT =
            "ANI-KUTA/1.0 (https://github.com/testplay-byte/ANI-KUTA)"
    }
}
