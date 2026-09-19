package com.confused.anikuta.core.notifications.di

import com.confused.anikuta.core.notifications.NotificationConfigStore
import com.confused.anikuta.core.notifications.NotificationManager
import com.confused.anikuta.core.preferences.UpdatePreferences
import org.koin.dsl.module

val notificationsModule = module {
    single { NotificationConfigStore(get()) }
    // D-477: the poster art provider (:app's EpisodeBannerComposer) rides the
    // nullable seam — when absent, notifications fall back to plain text.
    // D-503: the D-500 InAppBannerController wiring is GONE (the controller
    // file was deleted) — the user rejected the in-app overlay in favor of
    // the real Android heads-up popup, which the HIGH-importance channel
    // (NotificationManager.ensureChannel) now delivers.
    single {
        NotificationManager(
            get(), get(), get(), get(),
            get<UpdatePreferences>(),
            artProvider = getOrNull(),
        )
    }
}
