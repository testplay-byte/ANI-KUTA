package com.confused.anikuta.core.torrent.di

import com.confused.anikuta.core.torrent.TorrServerLoader
import com.confused.anikuta.core.torrent.TorrentGateway
import com.confused.anikuta.core.torrent.TorrentPreferences
import com.confused.anikuta.core.torrent.TorrentServerApi
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * The torrent module's Koin wiring. Removing torrent support = removing
 * this module from AnikutaApp's module list + the seams listed in
 * TORRENT-REMOVAL.md.
 */
val torrentModule = module {
    single { TorrServerLoader(androidContext()) }
    single { TorrentPreferences(get()) }
    single { TorrentServerApi(get()) }
    single { TorrentGateway(get(), get(), get()) }
}
