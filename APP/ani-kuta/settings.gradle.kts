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

// ── :data ──
include(":data:extension")

// ── :feature (api/impl split per Nav3 Pattern B) ──
include(":feature:anime-browse:api")
include(":feature:anime-browse:impl")
include(":feature:anime-details:api")
include(":feature:anime-details:impl")
