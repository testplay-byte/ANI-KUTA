package com.confused.anikuta.feature.animedetails

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/** NavKey for the Anime Details screen. Carries the AniList anime ID. */
@Serializable
data class AnimeDetailsKey(val animeId: Int) : NavKey
