pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "jalanz"

// Module layout: spec/06-android-auto.md §2. :app and :car never depend on each other.
include(":app")
include(":car")
include(
    ":core:drive",
    ":core:location",
    ":core:guidance",
    ":core:routing-client",
    ":core:toll",
    ":core:share",
    ":core:map",
    ":core:search-client",
    ":core:data",
    ":core:privacy",
    ":core:account",
    ":core:designsystem",
)
include(
    ":feature:search",
    ":feature:route-preview",
    ":feature:navigation",
    ":feature:share",
    ":feature:settings",
)
