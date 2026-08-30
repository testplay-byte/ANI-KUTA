// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// Enum value NAMES + order are interop facts (plugins reference them by name in
// bytecode). The constructor ints are kept for source stability.
package com.lagradost.cloudstream3

/** Determines provider type: MetaProvider (3rd party data) / DirectProvider (all data from site). */
enum class ProviderType {
    MetaProvider,
    DirectProvider,
}

/** Determines VPN status (None, MightBeNeeded or Torrent). */
enum class VPNStatus {
    None,
    MightBeNeeded,
    Torrent,
}

/** Determines show status (Completed or Ongoing). */
enum class ShowStatus {
    Completed,
    Ongoing,
}

enum class DubStatus(val id: Int) {
    None(-1),
    Dubbed(1),
    Subbed(0),
}

@Suppress("UNUSED_PARAMETER")
enum class TvType(value: Int?) {
    Movie(1),
    AnimeMovie(2),
    TvSeries(3),
    Cartoon(4),
    Anime(5),
    OVA(6),
    Torrent(7),
    Documentary(8),
    AsianDrama(9),
    Live(10),
    NSFW(11),
    Others(12),
    Music(13),
    AudioBook(14),

    /** Won't load the built in player, make your own interaction. */
    CustomMedia(15),

    Audio(16),
    Podcast(17),
    Video(18),
}

enum class AutoDownloadMode(val value: Int) {
    Disable(0),
    FilterByLang(1),
    All(2),
    NsfwOnly(3);

    companion object {
        infix fun getEnum(value: Int): AutoDownloadMode? = entries.firstOrNull { it.value == value }
    }
}

/** Set of sync services simkl is compatible with. */
enum class SimklSyncServices(val originalName: String) {
    Simkl("simkl"),
    Imdb("imdb"),
    Tmdb("tmdb"),
    AniList("anilist"),
    Mal("mal"),
}

/** Used for the getTracker() method. */
enum class TrackerType {
    MOVIE,
    TV,
    TV_SHORT,
    ONA,
    OVA,
    SPECIAL,
    MUSIC;

    companion object {
        fun getTypes(type: TvType): Set<TrackerType> = when (type) {
            TvType.Movie, TvType.AnimeMovie, TvType.Torrent, TvType.Live,
            TvType.Documentary, TvType.Others, TvType.Video, TvType.CustomMedia,
            -> setOf(MOVIE)

            TvType.TvSeries, TvType.AsianDrama -> setOf(TV)
            TvType.Cartoon -> setOf(TV, TV_SHORT)
            TvType.Anime -> setOf(TV, TV_SHORT, ONA, OVA, SPECIAL, MOVIE)
            TvType.OVA -> setOf(OVA)
            TvType.Music, TvType.Audio, TvType.AudioBook, TvType.Podcast, TvType.NSFW -> setOf(MUSIC)
        }
    }
}
