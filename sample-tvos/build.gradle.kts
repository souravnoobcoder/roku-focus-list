import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// Runnable Apple TV sample. Unlike `consumer-kmp`, which only has to compile, this one links a
// framework that the Xcode project in tvosApp/ hosts, so it is the module that proves the library
// actually behaves on a real Apple TV.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    // tvOS only. The library's other targets are covered by :consumer-kmp and :app; this module
    // exists purely to put something on an Apple TV.
    listOf(tvosArm64(), tvosSimulatorArm64()).forEach { target: KotlinNativeTarget ->
        target.binaries.framework {
            baseName = "RokuSample"
            // Static, so the .app carries no dynamic Kotlin framework to sign separately.
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":roku-focus-list"))
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.animation)
            implementation(libs.compose.mp.ui.tooling.preview)
        }
    }
}
