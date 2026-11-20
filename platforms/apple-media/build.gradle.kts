plugins { alias(libs.plugins.kotlin.multiplatform) }

kotlin {
    iosArm64()
    iosSimulatorArm64()
    if (providers.gradleProperty("naviamp.tvos.sceneHost").orNull == "true") {
        tvosArm64()
        tvosSimulatorArm64()
    }
    applyDefaultHierarchyTemplate()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        compilations.getByName("main").cinterops.create("AudioLifecycle") {
            definitionFile.set(project.file("src/nativeInterop/cinterop/AudioLifecycle.def"))
            includeDirs(project.file("src/nativeInterop/cinterop/include"))
        }
    }
    sourceSets {
        commonMain.dependencies { api(project(":core:presentation")) }
        commonTest.dependencies { implementation(libs.kotlin.test) }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    val host = if (name.startsWith("tvos")) "tvos" else "ios"
    device.set(providers.gradleProperty("naviamp.$host.testDevice").orElse("booted"))
}
