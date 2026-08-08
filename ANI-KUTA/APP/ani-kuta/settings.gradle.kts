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

// ── :data ──
include(":data:extension")

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
