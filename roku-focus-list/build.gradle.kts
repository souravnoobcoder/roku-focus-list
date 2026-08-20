import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

// io.github.<user> is the namespace the Central Portal grants off a verified GitHub account.
// com.github.* was JitPack's synthetic namespace and does not exist on Maven Central.
group = "io.github.souravnoobcoder"

// VERSION is still honoured so a CI job can publish a tag without editing the file; everything
// else reads the single declared version.
version = (System.getenv("VERSION") ?: providers.gradleProperty("libraryVersion").get())
    .removePrefix("v")

kotlin {
    android {
        namespace = "com.rokufocus"
        compileSdk = 36
        minSdk = 23

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

mavenPublishing {
    // Left staging-only on purpose. CI releases by calling publishAndReleaseToMavenCentral
    // explicitly, so running publishToMavenCentral by hand can never publish irrevocably.
    publishToMavenCentral(automaticRelease = false)

    // Central rejects unsigned artifacts, but signing unconditionally would break
    // publishToMavenLocal for anyone without a key — which is every contributor, and the
    // verification/published-consumer check. The release workflow asserts the key is present
    // before it publishes, so an unsigned release cannot slip out this way.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates(group.toString(), "roku-focus-list", version.toString())

    // Central rejects a POM missing any of these, and the rejection happens after upload.
    pom {
        name.set("roku-focus-list")
        description.set(
            "Roku-style fixed-focus D-pad navigation for Compose Multiplatform: the highlight " +
                "stays at a fixed screen position while content scrolls behind it."
        )
        inceptionYear.set("2024")
        url.set("https://github.com/souravnoobcoder/roku-focus-list")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("souravnoobcoder")
                name.set("Sourav Rawat")
                url.set("https://github.com/souravnoobcoder")
            }
        }

        scm {
            url.set("https://github.com/souravnoobcoder/roku-focus-list")
            connection.set("scm:git:git://github.com/souravnoobcoder/roku-focus-list.git")
            developerConnection.set("scm:git:ssh://git@github.com/souravnoobcoder/roku-focus-list.git")
        }
    }
}
