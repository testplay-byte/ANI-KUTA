package com.confused.anikuta.core.notifications.di

import com.confused.anikuta.core.notifications.InAppBannerController
import com.confused.anikuta.core.notifications.NotificationConfigStore
import com.confused.anikuta.core.notifications.NotificationManager
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.core.preferences.UpdatePreferences
import org.koin.dsl.module

val notificationsModule = module {
    single { NotificationConfigStore(get()) }
    // D-500: the in-app heads-up banner controller — the app's root overlay
    // collects its events and shows the composed banner on any screen.
    single { InAppBannerController() }
    // D-477: the poster art provider (:app's EpisodeBannerComposer) rides the
    // nullable seam — when absent, notifications fall back to plain text.
    single {
        NotificationManager(
            get(), get(), get(), get(),
            get<UpdatePreferences>(),
            artProvider = getOrNull(),
            inAppBanner = get(),
        )
    }
}
