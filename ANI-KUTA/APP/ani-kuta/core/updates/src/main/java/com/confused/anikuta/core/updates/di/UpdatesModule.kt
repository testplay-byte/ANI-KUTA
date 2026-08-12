package com.confused.anikuta.core.updates.di

import com.confused.anikuta.core.updates.UpdateEngine
import com.confused.anikuta.core.updates.UpdateScheduler
import com.confused.anikuta.core.updates.UpdateStore
import org.koin.dsl.module

val updatesModule = module {
    single { UpdateStore(get()) }
    // D-193 Phase 9: ActualReleaseUpdater + NotificationSender are nullable.
    // If :core:schedule / :core:notifications aren't available (they are, via :app),
    // the UpdateEngine skips those features. The bindings are in :app's appModule.
    single {
        UpdateEngine(
            updateStore = get(),
            extensionManager = get(),
            contentRepository = get(),
            watchProgressStore = get(),
            actualReleaseUpdater = getOrNull(),
            notificationSender = getOrNull(),
        )
    }
    // D-193 Phase 4: UpdateScheduler for configurable WorkManager interval.
    single { UpdateScheduler(get(), get()) }
    // D-193 Phase 5: SmartReleaseScheduler for 10-min polling.
    single { SmartReleaseScheduler(get(), get()) }
}
