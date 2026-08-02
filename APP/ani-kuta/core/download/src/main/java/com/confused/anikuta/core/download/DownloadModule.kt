package com.confused.anikuta.core.download

import com.confused.anikuta.core.database.AnikutaDatabase
import okhttp3.OkHttpClient
import org.koin.dsl.module
import java.io.File

val downloadModule = module {
    single {
        val context = org.koin.android.ext.koin.androidContext()
        val downloadDir = File(context.filesDir, "downloads")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        DownloadManager(
            database = get<AnikutaDatabase>(),
            httpClient = get<OkHttpClient>(),
            downloadDir = downloadDir,
        )
    }
}
