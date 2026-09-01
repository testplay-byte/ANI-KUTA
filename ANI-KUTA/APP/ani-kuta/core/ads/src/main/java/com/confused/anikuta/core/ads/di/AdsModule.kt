package com.confused.anikuta.core.ads.di

import com.confused.anikuta.core.ads.AdPreferences
import com.confused.anikuta.core.ads.AdsCoordinator
import com.confused.anikuta.core.ads.AdsRepository
import com.confused.anikuta.core.ads.AdsRepositoryImpl
import com.confused.anikuta.core.ads.AppLifecycleObserver
import com.confused.anikuta.core.preferences.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module for the ad system.
 *
 * Registers:
 * - [AdPreferences] — SharedPreferences-backed cooldown state (lastAdShownTimestamp).
 * - [AdsRepository] → [AdsRepositoryImpl] — config holder + cooldown gate.
 *   Interface-bound so a future remote-config impl swaps in without touching
 *   the coordinator or UI (CORE_RULES §5 exception: future swap planned).
 * - [AppLifecycleObserver] — ProcessLifecycleOwner foreground/background tracker.
 * - [AdsCoordinator] — the state machine the AppRoot calls + the interstitial observes.
 *
 * Registered in `:app`'s [com.confused.anikuta.AnikutaApp] `modules(...)` block
 * alongside the other `:core:*` modules. The `:core:ads` module has no
 * Koin-Compose `viewModelOf` bindings — the ad system is UI-state-driven
 * (StateFlow + Compose), not ViewModel-driven.
 *
 * # Adding a new ad kind (future)
 *
 * 1. Add `data object NewKind : AdKind` in `AdsConfig.kt`.
 * 2. Add a settings data class for it (mirrors [com.confused.anikuta.core.ads.SmartLinkConfig]).
 * 3. Add a `when` branch in `SmartLinkAdInterstitial.kt`'s content switch.
 * 4. If it needs a different cooldown or state machine, extend [AdsCoordinator].
 * No DI changes needed here — the coordinator + repository are ad-kind-agnostic.
 */
val adsModule = module {
    single { AdPreferences(get<PreferenceStore>()) }
    single<AdsRepository> { AdsRepositoryImpl(get()) }
    single { AppLifecycleObserver() }
    // Task 61 (round 21): the application context rides the constructor for
    // the offline gate (ConnectivityManager) — see AdsCoordinator.isOnline.
    single { AdsCoordinator(get(), get(), androidContext()) }
}
