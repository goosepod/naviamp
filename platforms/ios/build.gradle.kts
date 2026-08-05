plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val iosBassFrameworks = listOf(
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
    "bass_mpc",
)

kotlin {
    iosArm64()
    iosSimulatorArm64()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
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
            iosBassFrameworks.forEach { framework ->
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
        iosTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val stageIosSimulatorTestBassFrameworks by tasks.registering(Copy::class) {
    val slice = "ios-arm64_i386_x86_64-simulator"
    iosBassFrameworks.forEach { framework ->
        from(project.file("vendor/bass/$framework.xcframework/$slice/$framework.framework")) {
            into("$framework.framework")
        }
    }
    into(layout.buildDirectory.dir("bin/iosSimulatorArm64/debugTest/Frameworks"))
}

tasks.named("iosSimulatorArm64Test") {
    dependsOn(stageIosSimulatorTestBassFrameworks)
}

val verifyIosBassInventory by tasks.registering {
    group = "verification"
    description = "Verifies that the vendored iOS BASS XCFrameworks exactly match the linked inventory."
    doLast {
        val vendorDirectory = project.file("vendor/bass")
        val actual = vendorDirectory.listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.endsWith(".xcframework") }
            .map { it.name.removeSuffix(".xcframework") }
            .toSet()
        val expected = iosBassFrameworks.toSet()
        check(actual == expected) {
            "iOS BASS inventory mismatch; missing=${expected - actual}, unexpected=${actual - expected}"
        }
    }
}

tasks.named("check") {
    dependsOn(verifyIosBassInventory)
}
