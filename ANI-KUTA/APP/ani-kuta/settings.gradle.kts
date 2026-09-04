pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ANIKUTA"

// ── :app ──
include(":app")

// ── :core ──
include(":core:common")
include(":core:seasons")  // D-312: dedicated season-management engine (pattern registry + provider-hint fusion)
include(":core:designsystem")
include(":core:database")
include(":core:preferences")
include(":core:navigation-api")
include(":core:network")
include(":core:anilist")
include(":core:watch-progress")
include(":core:activity-tracker")
include(":core:provider-api")
include(":core:source-api")
include(":core:player-mpv-lib")
include(":core:player")
include(":core:video-resolver")
include(":core:download")
include(":core:metadata")
include(":core:tracker-api")
include(":core:tracker-anilist")
include(":core:smart-matcher")
include(":core:content")
include(":core:data-cache")
include(":core:updates")
include(":core:schedule")
include(":core:ratings")
include(":core:notifications")
include(":core:app-update")
include(":core:ads")  // Ad system — smart-link interstitial (D-272)
include(":core:playback-cache")  // Video caching (test-feature/video-cache-new-download)
include(":core:debug-api")
include(":core:cloudstream-api")  // CloudStream V2: clean-room CS3 plugin binary-compat surface
include(":core:cs-player")  // CloudStream V2 (task 52): Media3 ExoPlayer playback engine host — CS links NEVER touch MPV

// ── :data ──
include(":data:extension")
include(":data:cloudstream")  // CloudStream V2: extension system runtime (loader/manager/repos/bridge)

// ── :feature (api/impl split per Nav3 Pattern B) ──
include(":feature:anime-browse:api")
include(":feature:anime-browse:impl")
include(":feature:anime-details:api")
include(":feature:anime-details:impl")
include(":feature:anime-library:api")
include(":feature:anime-library:impl")
include(":feature:anime-search:api")
include(":feature:anime-search:impl")
include(":feature:extensions-settings:api")
include(":feature:extensions-settings:impl")
include(":feature:download")
include(":feature:onboarding")  // D-403 (round 28): the first-run setup wizard (single module — the feature:download precedent)
include(":feature:watch:api")
include(":feature:watch:impl")
include(":feature:cs-watch:api")  // CloudStream V2 (task 52): CS watch nav key — the aniyomi :feature:watch stays untouched
include(":feature:cs-watch:impl")  // CloudStream V2 (task 52): the dedicated CS watch screen (ExoPlayer) — mirroring the watch UX, zero aniyomi code shared
include(":feature:anime-history:api")
include(":feature:anime-history:impl")
include(":feature:updates:api")
include(":feature:updates:impl")
include(":feature:debug-bubble")
