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
 * # D-440 (round 39): the update repo follows the BUILD TYPE
 *
 * - **Debug builds** (`com.confused.anikuta.debug`) check the DEV repo
 *   `testplay-byte/ANI-KUTA` — the repository the app is developed in, whose
 *   releases + CI artifacts are the debug line's source of truth.
 * - **Release builds** (`com.confused.anikuta`) check the PUBLISHED repo
 *   `Confused-Creature-180/ANI-KUTA` — where official releases are re-hosted
 *   for end users (the same repo the App Icon catalog lives in).
 *
 * The split is resolved from the merged manifest's `android:debuggable` flag
 * (ApplicationInfo.FLAG_DEBUGGABLE) — the RUNTIME truth of the installed
 * build type — so this single wiring is correct on every branch and every
 * build path (supersedes the release-line-only D-411 hardcode: debug no
 * longer "accidentally" points at the published repo, and the debug app on
 * the user's device was observed checking the new repository).
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

    // GitHub update source — D-440: the owner follows the BUILD TYPE (see the
    // module KDoc). Resolved INSIDE the definition lambda (the Scope receiver
    // provides `get()`; the module-level receiver does not) so the Context
    // resolves against startKoin's androidContext at first use — the standard
    // Koin pattern, no eager module-load-time resolution.
    single<UpdateSource>(named("github")) {
        val appContext = get<android.content.Context>()
        val isDebugBuild = try {
            appContext.packageManager
                .getApplicationInfo(appContext.packageName, 0)
                .flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        } catch (_: Exception) {
            // Unreadable application info (should never happen) — fall back to
            // the DEV repo: the safe default is the repo we control.
            true
        }
        GitHubUpdateSource(
            owner = if (isDebugBuild) DEV_UPDATE_REPO_OWNER else PUBLISHED_UPDATE_REPO_OWNER,
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

/** D-440: the dev repository — debug builds' update source. */
private const val DEV_UPDATE_REPO_OWNER = "testplay-byte"

/** D-440: the published repository — release builds' update source. */
private const val PUBLISHED_UPDATE_REPO_OWNER = "Confused-Creature-180"
