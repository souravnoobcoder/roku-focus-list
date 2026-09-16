import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Versions are hardcoded because this is a separate build and cannot see the root version
// catalog. Keep them in sync with gradle/libs.versions.toml when bumping the toolchain.
plugins {
    kotlin("multiplatform") version "2.4.10"
    id("com.android.kotlin.multiplatform.library") version "9.2.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.12.0"
}

val rokuFocusList = "io.github.souravnoobcoder:roku-focus-list:2.3.0"
val composeVersion = "1.12.0"

kotlin {
    android {
        namespace = "com.example.publishedconsumer"
        compileSdk = 37
        minSdk = 24
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    iosArm64()
    iosSimulatorArm64()

    // The tvOS leg is the point of the compose-tvos plugin applied in settings.gradle.kts: it
    // proves a consumer can resolve the published coordinate for Apple TV using only the official
    // org.jetbrains.compose artifacts this library declares.
    tvosArm64()
    tvosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Resolved by Maven coordinate, from commonMain — this is the whole point.
            implementation(rokuFocusList)
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
        }
    }
}

tasks.register("verifyCommonMainConsumption") {
    group = "verification"
    description = "Compiles commonMain + every target against the published roku-focus-list artifact."
    dependsOn(
        "compileCommonMainKotlinMetadata",
        "compileAndroidMain",
        "compileKotlinDesktop",
        "compileKotlinIosArm64",
        "compileKotlinIosSimulatorArm64",
        "compileKotlinTvosArm64",
        "compileKotlinTvosSimulatorArm64",
    )
}

tasks.register("printRokuFocusResolution") {
    group = "verification"
    description = "Prints how the published roku-focus-list coordinate resolves per source set."
    val metadata = configurations.named("commonMainResolvableDependenciesMetadata")
    val desktop = configurations.named("desktopCompileClasspath")
    doLast {
        println("--- commonMain (metadata variant) ---")
        metadata.get().resolvedConfiguration.resolvedArtifacts
            .filter { "roku-focus-list" in it.moduleVersion.id.name }
            .forEach { println("  ${it.moduleVersion.id}  ->  ${it.file.name}") }
        println("--- desktop (jvm variant) ---")
        desktop.get().resolvedConfiguration.resolvedArtifacts
            .filter { "roku-focus-list" in it.moduleVersion.id.name }
            .forEach { println("  ${it.moduleVersion.id}  ->  ${it.file.name}") }
    }
}
