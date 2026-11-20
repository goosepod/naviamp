import groovy.json.JsonSlurper
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val tvosBassFrameworks = listOf("bass", "bassflac", "bassopus")
val transportTests = providers.gradleProperty("naviamp.tvos.transportTests").orNull == "true"
check(!transportTests || providers.gradleProperty("naviamp.tvos.sceneHost").orNull == "true") {
    "Transport tests need the reviewed full shared scene graph"
}

kotlin {
    tvosArm64()
    tvosSimulatorArm64()
    applyDefaultHierarchyTemplate()

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        val slice = if (name == "tvosSimulatorArm64") "tvos-arm64_x86_64-simulator" else "tvos-arm64"
        binaries.configureEach {
            tvosBassFrameworks.forEach { framework ->
                linkerOpts(
                    "-F${project.file("vendor/bass/$framework.xcframework/$slice").absolutePath}",
                    "-framework", framework,
                )
            }
        }
    }
    sourceSets {
        named("commonMain") {
            dependencies { api(project(":platforms:apple")) }
        }
        named("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.core)
                if (transportTests) {
                    implementation(project(":core:testkit"))
                    implementation(project(":platforms:apple-media"))
                }
            }
        }
        if (transportTests) getByName("tvosTest").kotlin.srcDirs("src/tvosTransportTest/kotlin", "../apple-media/src/appleTest/kotlin")
    }
}

val stageTvosSimulatorTestBassFrameworks = tasks.register<Copy>("stageTvosSimulatorTestBassFrameworks") {
    tvosBassFrameworks.forEach { framework ->
        from(project.file("vendor/bass/$framework.xcframework/tvos-arm64_x86_64-simulator/$framework.framework")) {
            into("$framework.framework")
            exclude("_CodeSignature/**")
        }
    }
    into(layout.buildDirectory.dir("bin/tvosSimulatorArm64/debugTest/Frameworks"))
}

tasks.named<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>("tvosSimulatorArm64Test") {
    dependsOn(stageTvosSimulatorTestBassFrameworks)
    standalone.set(false)
    device.set(providers.gradleProperty("naviamp.tvos.testDevice").orElse("booted"))
}

val verifyTvosBassInventory = tasks.register("verifyTvosBassInventory") {
    group = "verification"
    description = "Checks the upstream tvOS BASS inventory and the bundled privacy manifests."
    doLast {
        val vendor = project.file("vendor/bass")
        val actual = vendor.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.endsWith(".xcframework") }
            .map { it.name.removeSuffix(".xcframework") }.toSet()
        check(actual == tvosBassFrameworks.toSet()) { "Unexpected tvOS BASS inventory: $actual" }
        val inventory = JsonSlurper().parse(vendor.resolve("upstream-inventory.json")) as Map<*, *>
        check(inventory["archiveSha256"] == "8f3bef0a67c20fdf1b65a7b6164b25bfb3a75e47e8bd5f1094eb0fad1ac3bbe8")
        val expectedFiles = (inventory["files"] as Map<*, *>).map { (path, hash) -> path as String to hash as String }.toMap()
        val actualFiles = vendor.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(vendor).invariantSeparatorsPath }
            .filter { it.substringBefore('/').endsWith(".xcframework") }.toSet()
        val privacyManifests = setOf(
            "bass.xcframework/tvos-arm64/bass.framework/PrivacyInfo.xcprivacy",
            "bass.xcframework/tvos-arm64_x86_64-simulator/bass.framework/PrivacyInfo.xcprivacy",
        )
        check(expectedFiles.size == 18 && actualFiles == expectedFiles.keys + privacyManifests) {
            "tvOS BASS file inventory differs from upstream files and declared privacy manifests"
        }
        expectedFiles.forEach { (path, expectedHash) ->
            val file = vendor.resolve(path).canonicalFile
            check(file.toPath().startsWith(vendor.canonicalFile.toPath())) { "Invalid inventory path: $path" }
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(hash == expectedHash) { "tvOS BASS hash mismatch: $path" }
        }
    }
}
tasks.named("check") { dependsOn(verifyTvosBassInventory) }

// The linked framework list is the only inventory; Kotlin receives it as generated source.
val generateTvosBassInventory by tasks.registering {
    val output = layout.buildDirectory.dir("generated/bassInventory/kotlin")
    val frameworks = tvosBassFrameworks
    inputs.property("frameworks", frameworks)
    outputs.dir(output)
    doLast {
        val file = output.get().file("app/naviamp/tvos/playback/TvosBassInventory.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package app.naviamp.tvos.playback\n\n" +
                "/** Generated from the linked framework list in this module's build script. */\n" +
                "internal val TvosBassFrameworks: List<String> = listOf(" +
                frameworks.joinToString { "\"$it\"" } + ")\n",
        )
    }
}
kotlin.sourceSets.named("tvosMain") { kotlin.srcDir(generateTvosBassInventory) }
