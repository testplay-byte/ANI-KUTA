package com.confused.anikuta.core.anilist.api

import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.anilist.model.AnimeTitle
import com.confused.anikuta.core.anilist.model.CoverImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * D-278: JSON codec for cached browse-section payloads (the `browse_cache`
 * table stores AniList TRENDING / POPULARITY / SCORE results as JSON strings).
 *
 * Lives in `:core:anilist` (next to [AniListAnime]) so BOTH the Browse and
 * Search features can reuse it. The Search screen now serves the cached
 * trending payload as its offline default (D-278: "search should show default
 * results without internet") — which is the EXACT same AniList TRENDING query
 * Browse caches. Extracting the codec here avoids duplicating the ~30-line
 * parser across two features (CORE_RULES §5 — reuse before you write).
 *
 * **Section keys** are also declared here so both consumers reference the same
 * storage keys (no drift between the writer [BrowseViewModel] and the reader
 * [SearchViewModel]).
 *
 * **Format (stable):** a JSON array of objects with fields
 * `id, title, cover, score, episodes, year, banner, genres (CSV), status`.
 * Missing fields default to `null` on decode, so cache rows written by older
 * versions still parse (forward-compatible).
 *
 * **Failure mode:** [decode] throws on malformed JSON — callers wrap it in a
 * try/catch and log, so a corrupt cache row never crashes the UI (it degrades
 * to an empty list, which the caller treats as "no cache").
 */
object BrowseCacheCodec {

    /** Storage keys for the three browse sections (see `browse_cache.sq`). */
    const val SECTION_TRENDING = "trending"
    const val SECTION_POPULAR = "popular"
    const val SECTION_TOP_RATED = "top_rated"

    /**
     * Serializes a list of anime into the stable browse-cache JSON format.
     * Omitting null fields keeps the payload compact + lets old decoders
     * default them to null on read.
     */
    fun encode(anime: List<AniListAnime>): String {
        val array = buildJsonArray {
            for (a in anime) {
                add(buildJsonObject {
                    put("id", a.id)
                    put("title", a.displayName)
                    a.coverUrl?.let { put("cover", it) }
                    a.averageScore?.let { put("score", it) }
                    a.episodes?.let { put("episodes", it) }
                    a.seasonYear?.let { put("year", it) }
                    a.bannerImage?.let { put("banner", it) }
                    a.genres?.takeIf { it.isNotEmpty() }?.let { put("genres", it.joinToString(",")) }
                    a.status?.let { put("status", it) }
                })
            }
        }
        return array.toString()
    }

    /**
     * Parses a browse-cache JSON payload. Throws on malformed JSON — callers
     * catch + log + treat as "no cache" (graceful degradation).
     *
     * The `takeIf { it != "null" }` guards handle the edge case where a null
     * field was serialized as the literal string `"null"` by an older encoder
     * (defensive — keeps old cache rows readable).
     */
    fun decode(json: String): List<AniListAnime> {
        val array = Json.parseToJsonElement(json).jsonArray
        return array.map { element ->
            val obj = element.jsonObject
            AniListAnime(
                id = obj["id"]!!.jsonPrimitive.int,
                title = AnimeTitle(
                    romaji = obj["title"]!!.jsonPrimitive.toString().trim('"'),
                    english = obj["title"]!!.jsonPrimitive.toString().trim('"'),
                ),
                coverImage = CoverImage(
                    large = obj["cover"]?.jsonPrimitive?.toString()?.trim('"'),
                    extraLarge = obj["cover"]?.jsonPrimitive?.toString()?.trim('"'),
                ),
                averageScore = obj["score"]?.jsonPrimitive?.intOrNull,
                episodes = obj["episodes"]?.jsonPrimitive?.intOrNull,
                seasonYear = obj["year"]?.jsonPrimitive?.intOrNull,
                bannerImage = obj["banner"]?.jsonPrimitive?.toString()?.trim('"')?.takeIf { it != "null" },
                genres = obj["genres"]?.jsonPrimitive?.toString()?.trim('"')
                    ?.takeIf { it != "null" && it.isNotBlank() }
                    ?.split(","),
                status = obj["status"]?.jsonPrimitive?.toString()?.trim('"')?.takeIf { it != "null" },
            )
        }
    }
}
