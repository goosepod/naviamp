plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val tvosEnabled = providers.gradleProperty("naviamp.tvos.enabled").orNull == "true"

kotlin {
    iosArm64()
    iosSimulatorArm64()
    if (tvosEnabled) {
        tvosArm64()
        tvosSimulatorArm64()
    }
    applyDefaultHierarchyTemplate()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("bass") {
            defFile(project.file("src/nativeInterop/cinterop/bass.def"))
            includeDirs(project.file("src/nativeInterop/cinterop/include"))
        }
        val television = name.startsWith("tvos")
        val slice = when (name) {
            "tvosArm64" -> "tvos-arm64"
            "tvosSimulatorArm64" -> "tvos-arm64_x86_64-simulator"
            "iosSimulatorArm64" -> "ios-arm64_i386_x86_64-simulator"
            else -> "ios-arm64_armv7_armv7s"
        }
        val host = if (television) "tvos" else "ios"
        binaries.configureEach {
            linkerOpts(
                "-F${rootProject.file("platforms/$host/vendor/bass/bass.xcframework/$slice").absolutePath}",
                "-framework", "bass",
            )
        }
    }

    sourceSets {
        named("appleMain") {
            dependencies { api(project(":core:domain")) }
        }
    }
}
