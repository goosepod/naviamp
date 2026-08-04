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
            export(project(":core:storage"))
        }
    }

    sourceSets {
        iosMain.dependencies {
            api(project(":core:storage"))
            implementation(project(":core:presentation"))
            implementation(project(":core:ui"))
            implementation(project(":platforms:ios"))
            implementation(project(":providers:navidrome"))
            implementation(compose.runtime)
            implementation(compose.ui)
            implementation(libs.compose.material3)
        }
    }
}

composeCompiler {
    includeSourceInformation = false
    includeTraceMarkers = false
}
