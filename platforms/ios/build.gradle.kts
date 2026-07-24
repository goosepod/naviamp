plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        val bassFrameworks = listOf(
            "bass",
            "bassmix",
            "bassflac",
            "bassopus",
            "bassmidi",
            "basswv",
            "bassdsd",
            "basswebm",
            "basshls",
            "bassape",
            "bassloud",
            "bass_fx",
            "bass_mpc",
            "bass_tta",
        )
        val bassSlice = if (name == "iosSimulatorArm64") {
            "ios-arm64_i386_x86_64-simulator"
        } else {
            "ios-arm64_armv7_armv7s"
        }

        compilations.getByName("main").cinterops.create("bass") {
            defFile(project.file("src/nativeInterop/cinterop/bass.def"))
            includeDirs(project.file("vendor/bass/include"))
        }
        binaries.configureEach {
            bassFrameworks.forEach { framework ->
                linkerOpts(
                    "-F${project.file("vendor/bass/$framework.xcframework/$bassSlice").absolutePath}",
                    "-framework",
                    framework,
                )
            }
        }
    }

    sourceSets {
        iosMain.dependencies {
            api(project(":core:app"))
            api(project(":core:domain"))
            api(project(":core:presentation"))
            api(project(":core:storage"))
            api(project(":core:ui"))
            api(project(":providers:navidrome"))
        }
    }
}
