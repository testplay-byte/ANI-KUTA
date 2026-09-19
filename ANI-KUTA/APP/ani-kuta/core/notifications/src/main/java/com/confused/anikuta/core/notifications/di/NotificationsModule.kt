package com.confused.anikuta.core.notifications.di

import com.confused.anikuta.core.notifications.NotificationConfigStore
import com.confused.anikuta.core.notifications.NotificationManager
import com.confused.anikuta.core.preferences.NotificationPreferences
import com.confused.anikuta.core.preferences.UpdatePreferences
import org.koin.dsl.module

val notificationsModule = module {
    single { NotificationConfigStore(get()) }
    // D-477: the poster art provider (:app's EpisodeBannerComposer) rides the
    // nullable seam — when absent, notifications fall back to plain text.
    single { NotificationManager(get(), get(), get(), get(), get<UpdatePreferences>(), getOrNull()) }
}
