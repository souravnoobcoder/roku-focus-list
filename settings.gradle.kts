pluginManagement {
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
// Injects the tvOS variants of Compose Multiplatform — published by the
// sajidalidev/compose-multiplatform-core fork under dev.sajidali.* — onto the official
// org.jetbrains.* coordinates at resolution time. It only touches tvOS configurations; Android,
// desktop, iOS and wasmJs keep resolving JetBrains' own artifacts.
//
// strictMode is deliberately left off: it reports false positives on iOS-only platform leaves
// (*-uikitarm64, *-uikitsimarm64) and on conflict-resolution losers, failing builds whose linked
// graph is fine.
plugins {
    id("dev.sajidali.compose-tvos") version "1.4.2"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RokuFocus"
include(":app")
include(":roku-focus-list")
include(":consumer-kmp")
 