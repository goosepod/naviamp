plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.withType<org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable>().configureEach {
            linkerOpts("-lsqlite3")
            val slice = if (target.name == "iosArm64") "ios-arm64" else "ios-arm64_i386_x86_64-simulator"
            listOf("bass", "bassmix").forEach { framework ->
                linkerOpts(
                    "-F${rootProject.file("platforms/ios/vendor/bass/$framework.xcframework/$slice").absolutePath}",
                    "-framework", framework,
                )
            }
        }
        binaries.framework {
            baseName = "NaviampShared"
            isStatic = true
            export(project(":core:storage"))
            if (providers.gradleProperty("naviamp.ios.profilePhases").orNull == "true") {
                freeCompilerArgs += listOf("-Xprofile-phases")
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            implementation(project(":core:app"))
            api(project(":core:storage"))
            implementation(project(":core:bootstrap"))
            implementation(project(":core:presentation"))
            implementation(project(":core:ui"))
            implementation(project(":platforms:ios"))
            implementation(project(":platforms:apple-media"))
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

val stageIosApplicationTestBassFrameworks by tasks.registering(Copy::class) {
    dependsOn(":platforms:ios:stageIosSimulatorTestBassFrameworks")
    from(project(":platforms:ios").layout.buildDirectory.dir("bin/iosSimulatorArm64/debugTest/Frameworks"))
    into(layout.buildDirectory.dir("bin/iosSimulatorArm64/debugTest/Frameworks"))
}

tasks.named<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>("iosSimulatorArm64Test") {
    dependsOn(stageIosApplicationTestBassFrameworks)
    standalone.set(false)
    device.set(providers.gradleProperty("naviamp.ios.testDevice").orElse("booted"))
}
