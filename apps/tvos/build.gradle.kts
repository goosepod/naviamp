plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    tvosArm64()
    tvosSimulatorArm64()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "NaviampTvShared"
            isStatic = true
            // Swift implements only these small native effects. Never export bootstrap/providers/UI.
            export(project(":core:host-api"))
            linkerOpts("-lsqlite3")
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:host-api"))
            implementation(project(":core:bootstrap"))
            implementation(project(":core:presentation"))
            implementation(project(":core:ui"))
            implementation(project(":core:storage"))
            implementation(project(":platforms:tvos"))
            implementation(project(":platforms:apple-media"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.ui)
        }
    }
}

composeCompiler {
    includeSourceInformation = false
    includeTraceMarkers = false
}
