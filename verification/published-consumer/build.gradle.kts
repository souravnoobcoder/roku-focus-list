import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Versions are hardcoded because this is a separate build and cannot see the root version
// catalog. Keep them in sync with gradle/libs.versions.toml when bumping the toolchain.
plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.android.kotlin.multiplatform.library") version "9.0.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
    id("org.jetbrains.compose") version "1.10.3"
}

val rokuFocusList = "io.github.souravnoobcoder:roku-focus-list:2.0.0"

kotlin {
    android {
        namespace = "com.example.publishedconsumer"
        compileSdk = 36
        minSdk = 24
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Resolved by Maven coordinate, from commonMain — this is the whole point.
            implementation(rokuFocusList)
            implementation("org.jetbrains.compose.runtime:runtime:1.10.3")
            implementation("org.jetbrains.compose.foundation:foundation:1.10.3")
            implementation("org.jetbrains.compose.ui:ui:1.10.3")
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
        "compileKotlinIosX64",
        "compileKotlinIosSimulatorArm64",
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
