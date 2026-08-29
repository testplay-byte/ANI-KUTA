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
include(":core:cloudstream-api")  // Clean-room CS3 plugin binary-compat surface (doc 23)

// ── :data ──
include(":data:extension")
include(":data:cloudstream")  // CloudStream extension system runtime (doc 23)

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
include(":feature:watch:api")
include(":feature:watch:impl")
include(":feature:anime-history:api")
include(":feature:anime-history:impl")
include(":feature:updates:api")
include(":feature:updates:impl")
include(":feature:debug-bubble")
