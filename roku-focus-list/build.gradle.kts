import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

group = "com.github.souravnoobcoder"

// JitPack builds a tag and expects the artifact to land in ~/.m2 under that exact version, so it
// passes the tag in VERSION. Without honouring it, tagging 2.0.1 would publish 2.0.0 and the
// coordinate consumers ask for would resolve to nothing.
version = (System.getenv("VERSION") ?: providers.gradleProperty("libraryVersion").get())
    .removePrefix("v")

kotlin {
    android {
        namespace = "com.rokufocus"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        optimization {
            consumerKeepRules.apply {
                publish = true
                file("consumer-rules.pro")
            }
        }

        // The Compose lint check misreads BoxWithConstraints scope usage in RokuLazyRow /
        // RokuLazyColumn, and @SuppressLint is not available from commonMain.
        lint {
            disable += "UnusedBoxWithConstraintsScope"
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    withSourcesJar(publish = true)

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.mp.runtime)
            api(libs.compose.mp.runtime.saveable)
            api(libs.compose.mp.foundation)
            api(libs.compose.mp.ui)
            api(libs.compose.mp.animation)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
