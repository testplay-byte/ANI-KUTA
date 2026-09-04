package com.confused.anikuta.core.appupdate.di

import com.confused.anikuta.core.appupdate.AppUpdateManager
import com.confused.anikuta.core.appupdate.AppUpdatePreferences
import com.confused.anikuta.core.appupdate.GitHubUpdateSource
import com.confused.anikuta.core.appupdate.UpdateSource
import com.confused.anikuta.core.preferences.PreferenceStore
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Koin module for the app self-update system.
 *
 * Registers:
 * - [AppUpdatePreferences] — settings singleton.
 * - [GitHubUpdateSource] — the GitHub-based update source.
 * - `List<UpdateSource>` — all registered sources (priority order). Adding a
 *   new source = one `single<UpdateSource>` binding + add to the list.
 * - [AppUpdateManager] — the orchestrator singleton.
 *
 * # Adding a new update source
 *
 * 1. Implement [UpdateSource] (e.g., `CustomJsonUpdateSource`).
 * 2. Register it here:
 *    ```kotlin
 *    single<UpdateSource>(named("custom")) { CustomJsonUpdateSource(...) }
 *    ```
 * 3. Add it to the `List<UpdateSource>` binding.
 * The [AppUpdateManager] will automatically query it on the next check.
 */
val appUpdateModule = module {
    single { AppUpdatePreferences(get<PreferenceStore>()) }

    // GitHub update source — the PUBLISHED repo (D-411, round 33): the v1.1.1+
    // publishable line lives at Confused-Creature-180/ANI-KUTA (APK-only —
    // releases carry the APK; no source code is published there). The v0.4.x
    // dev line (testplay-byte) is closed — its installs query the old repo
    // until the user sideloads v1.1.1 once, after which updates flow from here.
    single<UpdateSource>(named("github")) {
        GitHubUpdateSource(
            owner = "Confused-Creature-180",
            repo = "ANI-KUTA",
            client = get(named("appUpdate")),
        )
    }

    // Dedicated OkHttp client for update checks/downloads (separate from extension network).
    single(named("appUpdate")) {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // All registered update sources (priority order — first non-null wins).
    single<List<UpdateSource>> {
        listOf(get<UpdateSource>(named("github")))
    }

    single {
        AppUpdateManager(
            context = get(),
            preferences = get(),
            sources = get<List<UpdateSource>>(),
        )
    }
}
