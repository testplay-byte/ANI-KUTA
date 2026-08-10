package com.confused.anikuta.core.content.genre

/**
 * Canonical genre list — AniList's complete genre vocabulary.
 *
 * Seeded into the `genre` DB table on first launch. Each entry has:
 * - anilistName: the exact string AniList uses in GraphQL responses
 * - displayName: what the UI shows
 * - category: 'genre' | 'theme' | 'demographic'
 * - isNsfw: whether this genre is NSFW
 *
 * This list is the SINGLE SOURCE OF TRUTH for what counts as a valid genre.
 * Free-text genres from extensions are normalized against this list (via
 * GenreNormalizer) — unrecognized strings are dropped, not stored.
 *
 * Source: https://anilist.gitbook.io/anilist-apiv2-docs/reference/enums#mediagenre
 */
object CanonicalGenres {

    data class GenreDef(
        val anilistName: String,
        val displayName: String,
        val category: String,  // "genre" | "theme" | "demographic"
        val isNsfw: Boolean = false,
    )

    /** The complete AniList genre vocabulary (40 items as of 2025). */
    val ALL: List<GenreDef> = listOf(
        // ── Core Genres ──
        GenreDef("Action", "Action", "genre"),
        GenreDef("Adventure", "Adventure", "genre"),
        GenreDef("Comedy", "Comedy", "genre"),
        GenreDef("Drama", "Drama", "genre"),
        GenreDef("Fantasy", "Fantasy", "genre"),
        GenreDef("Horror", "Horror", "genre"),
        GenreDef("Mystery", "Mystery", "genre"),
        GenreDef("Romance", "Romance", "genre"),
        GenreDef("Sci-Fi", "Sci-Fi", "genre"),
        GenreDef("Slice of Life", "Slice of Life", "genre"),
        GenreDef("Sports", "Sports", "genre"),
        GenreDef("Supernatural", "Supernatural", "genre"),
        GenreDef("Suspense", "Suspense", "genre"),
        GenreDef("Award Winning", "Award Winning", "genre"),
        GenreDef("Gourmet", "Gourmet", "genre"),
        GenreDef("Avant Garde", "Avant Garde", "genre"),
        GenreDef("Ecchi", "Ecchi", "genre", isNsfw = true),
        GenreDef("Erotica", "Erotica", "genre", isNsfw = true),
        GenreDef("Hentai", "Hentai", "genre", isNsfw = true),

        // ── Themes ──
        GenreDef("Adult Cast", "Adult Cast", "theme"),
        GenreDef("Anthropomorphic", "Anthropomorphic", "theme"),
        GenreDef("CGDCT", "CGDCT", "theme"),
        GenreDef("Childcare", "Childcare", "theme"),
        GenreDef("Combat Sports", "Combat Sports", "theme"),
        GenreDef("Crossdressing", "Crossdressing", "theme"),
        GenreDef("Delinquents", "Delinquents", "theme"),
        GenreDef("Detective", "Detective", "theme"),
        GenreDef("Educational", "Educational", "theme"),
        GenreDef("Gag Humor", "Gag Humor", "theme"),
        GenreDef("Gore", "Gore", "theme"),
        GenreDef("Harem", "Harem", "theme"),
        GenreDef("High Stakes Game", "High Stakes Game", "theme"),
        GenreDef("Historical", "Historical", "theme"),
        GenreDef("Idols (Female)", "Idols (Female)", "theme"),
        GenreDef("Idols (Male)", "Idols (Male)", "theme"),
        GenreDef("Isekai", "Isekai", "theme"),
        GenreDef("Iyashikei", "Iyashikei", "theme"),
        GenreDef("Love Polygon", "Love Polygon", "theme"),
        GenreDef("Magical Sex Shift", "Magical Sex Shift", "theme"),
        GenreDef("Mahou Shoujo", "Mahou Shoujo", "theme"),
        GenreDef("Martial Arts", "Martial Arts", "theme"),
        GenreDef("Mecha", "Mecha", "theme"),
        GenreDef("Medical", "Medical", "theme"),
        GenreDef("Military", "Military", "theme"),
        GenreDef("Music", "Music", "theme"),
        GenreDef("Mythology", "Mythology", "theme"),
        GenreDef("Organized Crime", "Organized Crime", "theme"),
        GenreDef("Otaku Culture", "Otaku Culture", "theme"),
        GenreDef("Parody", "Parody", "theme"),
        GenreDef("Performing Arts", "Performing Arts", "theme"),
        GenreDef("Pets", "Pets", "theme"),
        GenreDef("Psychological", "Psychological", "theme"),
        GenreDef("Racing", "Racing", "theme"),
        GenreDef("Reincarnation", "Reincarnation", "theme"),
        GenreDef("Reverse Harem", "Reverse Harem", "theme"),
        GenreDef("Romantic Subtext", "Romantic Subtext", "theme"),
        GenreDef("Samurai", "Samurai", "theme"),
        GenreDef("School", "School", "theme"),
        GenreDef("Showbiz", "Showbiz", "theme"),
        GenreDef("Space", "Space", "theme"),
        GenreDef("Strategy Game", "Strategy Game", "theme"),
        GenreDef("Super Power", "Super Power", "theme"),
        GenreDef("Survival", "Survival", "theme"),
        GenreDef("Team Sports", "Team Sports", "theme"),
        GenreDef("Time Travel", "Time Travel", "theme"),
        GenreDef("Vampire", "Vampire", "theme"),
        GenreDef("Video Game", "Video Game", "theme"),
        GenreDef("Visual Arts", "Visual Arts", "theme"),
        GenreDef("Workplace", "Workplace", "theme"),
        GenreDef("Boys Love", "Boys Love", "theme"),
        GenreDef("Girls Love", "Girls Love", "theme"),

        // ── Demographics ──
        GenreDef("Josei", "Josei", "demographic"),
        GenreDef("Kids", "Kids", "demographic"),
        GenreDef("Seinen", "Seinen", "demographic"),
        GenreDef("Shoujo", "Shoujo", "demographic"),
        GenreDef("Shounen", "Shounen", "demographic"),
    )

    /** Quick lookup: lowercase anilist name → GenreDef. */
    private val lookup: Map<String, GenreDef> = ALL.associateBy { it.anilistName.lowercase() }

    /** Aliases: extension strings that map to a canonical genre. */
    private val aliases: Map<String, String> = mapOf(
        "science fiction" to "Sci-Fi",
        "scifi" to "Sci-Fi",
        "sci fi" to "Sci-Fi",
        "slice-of-life" to "Slice of Life",
        "slice of life" to "Slice of Life",
        "mahou shoujo" to "Mahou Shoujo",
        "magical girl" to "Mahou Shoujo",
        "mecha" to "Mecha",
        "psychological" to "Psychological",
        "martial arts" to "Martial Arts",
        "martialarts" to "Martial Arts",
        "school life" to "School",
        "school" to "School",
        "shounen" to "Shounen",
        "shonen" to "Shounen",
        "shoujo" to "Shoujo",
        "seinen" to "Seinen",
        "josei" to "Josei",
        "isekai" to "Isekai",
        "harem" to "Harem",
        "reverse harem" to "Reverse Harem",
        "vampire" to "Vampire",
        "zombies" to "Supernatural",
        "zombie" to "Supernatural",
        "demons" to "Supernatural",
        "demon" to "Supernatural",
        "angels" to "Supernatural",
        "ghost" to "Supernatural",
        "ghosts" to "Supernatural",
        "magic" to "Fantasy",
        "magical" to "Fantasy",
        "super power" to "Super Power",
        "superpower" to "Super Power",
        "super powers" to "Super Power",
        "time travel" to "Time Travel",
        "space" to "Space",
        "military" to "Military",
        "police" to "Detective",
        "ninja" to "Action",
        "samurai" to "Samurai",
        "historical" to "Historical",
        "music" to "Music",
        "idol" to "Idols (Female)",
        "idols" to "Idols (Female)",
        "cooking" to "Gourmet",
        "food" to "Gourmet",
        "survival" to "Survival",
        "survival game" to "High Stakes Game",
        "death game" to "High Stakes Game",
        "video game" to "Video Game",
        "racing" to "Racing",
        "cars" to "Racing",
        "medical" to "Medical",
        "reincarnation" to "Reincarnation",
        "parody" to "Parody",
        "comedy" to "Comedy",
        "action" to "Action",
        "adventure" to "Adventure",
        "drama" to "Drama",
        "fantasy" to "Fantasy",
        "horror" to "Horror",
        "mystery" to "Mystery",
        "romance" to "Romance",
        "sports" to "Sports",
        "supernatural" to "Supernatural",
        "suspense" to "Suspense",
        "ecchi" to "Ecchi",
        "gore" to "Gore",
        "harem" to "Harem",
        "psychological" to "Psychological",
        "thriller" to "Suspense",
    )

    /**
     * Normalize a raw genre string (from an extension or AniList) to the
     * canonical AniList name. Returns null if the string doesn't match any
     * known genre (it will be dropped, not stored).
     *
     * Steps:
     * 1. Trim whitespace.
     * 2. Check direct match (case-insensitive) against anilist_name.
     * 3. Check alias map (case-insensitive).
     * 4. If no match, return null (unrecognized — drop it).
     */
    fun normalize(rawGenre: String): String? {
        val trimmed = rawGenre.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()

        // Direct match.
        lookup[lower]?.let { return it.anilistName }

        // Alias match.
        aliases[lower]?.let { return it }

        // Try without hyphens.
        val noHyphen = lower.replace("-", " ")
        lookup[noHyphen]?.let { return it.anilistName }
        aliases[noHyphen]?.let { return it }

        // Unrecognized genre — drop it.
        return null
    }

    /** Normalize a list of raw genre strings → canonical names (unrecognized dropped). */
    fun normalizeAll(rawGenres: List<String>): List<String> {
        return rawGenres.mapNotNull { normalize(it) }.distinct()
    }

    /** Normalize a comma-separated genre string → canonical names. */
    fun normalizeCsv(csvGenres: String?): List<String> {
        if (csvGenres.isNullOrBlank()) return emptyList()
        // Split on comma (with or without space), semicolon, or pipe.
        val parts = csvGenres.split(Regex("[,;|]")).map { it.trim() }.filter { it.isNotEmpty() }
        return normalizeAll(parts)
    }
}
