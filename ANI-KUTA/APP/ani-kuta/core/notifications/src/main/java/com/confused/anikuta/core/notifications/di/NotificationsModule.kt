package com.confused.anikuta.core.notifications.di

import com.confused.anikuta.core.notifications.NotificationConfigStore
import com.confused.anikuta.core.notifications.NotificationManager
import org.koin.dsl.module

val notificationsModule = module {
    single { NotificationConfigStore(get()) }
    single { NotificationManager(get(), get(), get()) }
}
