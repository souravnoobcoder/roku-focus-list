// Standalone build (NOT part of the root Gradle build) that resolves roku-focus-list by
// Maven coordinate from mavenLocal, to prove the published Gradle module metadata lets a
// Kotlin Multiplatform project depend on the library from commonMain.
//
// Run:  ./gradlew -p verification/published-consumer verifyCommonMainConsumption
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
// A consumer building for Apple TV applies this themselves; the library's own published metadata
// stays on the official org.jetbrains.compose coordinates.
plugins {
    id("dev.sajidali.compose-tvos") version "1.4.2"
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "published-consumer"
