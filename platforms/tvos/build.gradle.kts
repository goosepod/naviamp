import groovy.json.JsonSlurper
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val tvosBassFrameworks = listOf("bass", "bassflac", "bassopus")

kotlin {
    tvosArm64()
    tvosSimulatorArm64()

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
            }
        }
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
    description = "Checks that the direct tvOS backend bundles exactly the public BASS inventory."
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
        check(expectedFiles.size == 18 && actualFiles == expectedFiles.keys) { "tvOS BASS file inventory differs from upstream" }
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
