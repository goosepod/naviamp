import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

val desktopMpvPlatform = providers.gradleProperty("naviamp.mpv.platform")
    .orElse(desktopMpvPlatform())
val desktopMpvExecutableName = desktopMpvPlatform.map { platform ->
    if (platform.startsWith("windows-")) "mpv.exe" else "mpv"
}
val desktopMpvVendorFile = desktopMpvPlatform.zip(desktopMpvExecutableName) { platform, executableName ->
    layout.projectDirectory.file("vendor/mpv/$platform/$executableName")
}
val generatedDesktopMpvResources = layout.buildDirectory.dir("generated/desktopMpv")
val copyDesktopMpv by tasks.registering(Copy::class) {
    from(desktopMpvVendorFile)
    into(generatedDesktopMpvResources.zip(desktopMpvPlatform) { resources, platform ->
        resources.dir("playback/mpv/$platform")
    })
    onlyIf { desktopMpvVendorFile.get().asFile.isFile }
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            resources.srcDir(generatedDesktopMpvResources)

            dependencies {
                implementation(project(":core:domain"))
                implementation(project(":core:ui"))
                implementation(project(":providers:navidrome"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.jlayer)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

sqldelight {
    databases {
        create("NaviampCacheDatabase") {
            packageName.set("app.naviamp.desktop.cache")
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.naviamp.desktop.MainKt"
        jvmArgs += listOf(
            "-Dcompose.application.name=Naviamp",
            "-Dapple.awt.application.name=Naviamp",
            "-Dsun.awt.application.name=Naviamp",
        )

        nativeDistributions {
            packageName = "Naviamp"
            packageVersion = "0.9.0"
            modules("java.net.http", "java.sql")

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/naviamp.ico"))
            }
        }
    }
}

tasks.matching { it.name == "desktopProcessResources" || it.name == "processDesktopMainResources" }
    .configureEach {
        dependsOn(copyDesktopMpv)
    }

fun desktopMpvPlatform(): String {
    val os = System.getProperty("os.name").lowercase().let { osName ->
        when {
            osName.contains("mac") || osName.contains("darwin") -> "macos"
            osName.contains("win") -> "windows"
            osName.contains("linux") -> "linux"
            else -> osName.filter { it.isLetterOrDigit() }.ifBlank { "unknown" }
        }
    }
    val arch = System.getProperty("os.arch").lowercase().let { archName ->
        when (archName) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "x64"
            else -> archName.filter { it.isLetterOrDigit() }.ifBlank { "unknown" }
        }
    }
    return "$os-$arch"
}
