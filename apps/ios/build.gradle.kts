plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "NaviampShared"
            isStatic = true
            // Swift implements only this credential effect; storage/query APIs stay internal.
            export(project(":core:storage-api"))
            if (providers.gradleProperty("naviamp.ios.profilePhases").orNull == "true") {
                freeCompilerArgs += listOf("-Xprofile-phases")
            }
        }
    }

    sourceSets {
        iosMain.dependencies {
            api(project(":core:storage-api"))
            implementation(project(":core:storage"))
            implementation(project(":core:presentation"))
            implementation(project(":core:ui"))
            implementation(project(":platforms:ios"))
            implementation(project(":providers:navidrome"))
            implementation(project(":providers:jellyfin"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.ktor.client.core)
        }
    }
}

composeCompiler {
    includeSourceInformation = false
    includeTraceMarkers = false
}
