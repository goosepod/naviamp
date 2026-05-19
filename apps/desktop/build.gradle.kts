import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
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
val desktopBassPlatform = providers.gradleProperty("naviamp.bass.platform")
    .orElse(desktopMpvPlatform())
val desktopBassVendorDir = desktopBassPlatform.map { platform ->
    layout.projectDirectory.dir("../desktop-slint/vendor/bass/$platform")
}
val generatedDesktopBassResources = layout.buildDirectory.dir("generated/desktopBass")
val copyDesktopBass by tasks.registering(Copy::class) {
    from(desktopBassVendorDir)
    into(generatedDesktopBassResources.zip(desktopBassPlatform) { resources, platform ->
        resources.dir("playback/bass/$platform")
    })
    onlyIf { desktopBassVendorDir.get().asFile.isDirectory }
}
val generatedDesktopBassAppResources = layout.buildDirectory.dir("generated/desktopBassApp")
val copyDesktopBassAppResources by tasks.registering(Copy::class) {
    from(desktopBassVendorDir)
    into(generatedDesktopBassAppResources.zip(desktopBassPlatform) { resources, platform ->
        resources.dir("$platform/playback/bass/$platform")
    })
    onlyIf { desktopBassVendorDir.get().asFile.isDirectory }
}
val desktopBassJniBuildDir = desktopBassPlatform.map { platform ->
    layout.buildDirectory.dir("generated/bassJniBuild/$platform").get()
}
val desktopBassJniOutputFile = desktopBassJniBuildDir.zip(desktopBassPlatform) { buildDir, platform ->
    buildDir.file(desktopLibraryName("naviamp_bass", platform)).asFile
}
val configureDesktopBassJni by tasks.registering(Exec::class) {
    val nativeProjectDir = rootProject.layout.projectDirectory.dir("native/bass-jni")
    onlyIf {
        desktopBassVendorDir.get().asFile.isDirectory &&
            desktopBassPlatform.get().startsWith("macos-")
    }
    commandLine(
        "cmake",
        "-S",
        nativeProjectDir.asFile.absolutePath,
        "-B",
        desktopBassJniBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_OSX_ARCHITECTURES=${desktopCmakeArchitecture(desktopBassPlatform.get())}",
        "-DBASS_LIBRARY_DIR=${desktopBassVendorDir.get().asFile.absolutePath}",
    )
}
val buildDesktopBassJni by tasks.registering(Exec::class) {
    dependsOn(configureDesktopBassJni)
    onlyIf {
        desktopBassVendorDir.get().asFile.isDirectory &&
            desktopBassPlatform.get().startsWith("macos-")
    }
    commandLine("cmake", "--build", desktopBassJniBuildDir.get().asFile.absolutePath, "--config", "Release")
}
val copyDesktopBassJni by tasks.registering(Copy::class) {
    dependsOn(buildDesktopBassJni)
    from(desktopBassJniOutputFile)
    into(generatedDesktopBassResources.zip(desktopBassPlatform) { resources, platform ->
        resources.dir("playback/bass/$platform")
    })
    onlyIf { desktopBassJniOutputFile.get().isFile }
}
val copyDesktopBassJniAppResources by tasks.registering(Copy::class) {
    dependsOn(buildDesktopBassJni)
    from(desktopBassJniOutputFile)
    into(generatedDesktopBassAppResources.zip(desktopBassPlatform) { resources, platform ->
        resources.dir("$platform/playback/bass/$platform")
    })
    onlyIf { desktopBassJniOutputFile.get().isFile }
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            resources.srcDir(generatedDesktopBassResources)

            dependencies {
                implementation(project(":core:domain"))
                implementation(project(":core:storage"))
                implementation(project(":core:ui"))
                implementation(project(":providers:navidrome"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.jlayer)
                implementation(libs.jna)
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

compose.desktop {
    application {
        mainClass = "app.naviamp.desktop.MainKt"
        jvmArgs += listOf(
            "-Dcompose.application.name=Naviamp",
            "-Dapple.awt.application.name=Naviamp",
            "-Dsun.awt.application.name=Naviamp",
        )

        buildTypes {
            release {
                proguard {
                    configurationFiles.from(project.file("proguard-rules.pro"))
                }
            }
        }

        nativeDistributions {
            packageName = "Naviamp"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(generatedDesktopBassAppResources)
            modules("java.net.http", "java.sql")

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/naviamp.ico"))
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icons/naviamp.icns"))
            }
        }
    }
}

tasks.matching { it.name == "desktopProcessResources" || it.name == "processDesktopMainResources" }
    .configureEach {
        dependsOn(copyDesktopBass)
        dependsOn(copyDesktopBassJni)
    }

tasks.matching {
    it.name == "prepareAppResources" ||
    it.name == "createDistributable" ||
        it.name == "createReleaseDistributable" ||
        it.name == "packageDistributionForCurrentOS" ||
        it.name == "packageReleaseDistributionForCurrentOS"
}.configureEach {
    dependsOn(copyDesktopBassAppResources)
    dependsOn(copyDesktopBassJniAppResources)
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

fun desktopLibraryName(stem: String, platform: String): String =
    when {
        platform.startsWith("windows-") -> "$stem.dll"
        platform.startsWith("macos-") -> "lib$stem.dylib"
        else -> "lib$stem.so"
    }

fun desktopCmakeArchitecture(platform: String): String =
    when {
        platform.endsWith("-arm64") -> "arm64"
        platform.endsWith("-x64") -> "x86_64"
        else -> System.getProperty("os.arch")
    }
