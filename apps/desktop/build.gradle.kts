import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val desktopBassPlatform = providers.gradleProperty("naviamp.bass.platform")
    .orElse(desktopNativePlatform())
val desktopBassVendorDir = desktopBassPlatform.map { platform ->
    layout.projectDirectory.dir("vendor/bass/$platform")
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
val desktopPackagedAppName = desktopBassPlatform.map { platform ->
    if (platform.startsWith("macos-")) "Naviamp.app" else "Naviamp"
}
val desktopPackagedAppDir = desktopPackagedAppName.flatMap { appName ->
    layout.buildDirectory.dir("compose/binaries/main/app/$appName")
}
val desktopLocalTestAppDir = desktopPackagedAppName.flatMap { appName ->
    rootProject.layout.buildDirectory.dir("local-test/$appName")
}
val desktopReleaseAppDir = desktopPackagedAppName.flatMap { appName ->
    rootProject.layout.buildDirectory.dir("release/$appName")
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
        when {
            desktopBassPlatform.get().startsWith("windows-") -> {
                jvmArgs += "-Dskiko.renderApi=OPENGL"
            }
            desktopBassPlatform.get().startsWith("macos-") -> {
                jvmArgs += "-Dskiko.renderApi=METAL"
            }
        }

        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
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

fun Zip.packageDesktopApp(archiveNameSuffix: String) {
    group = "distribution"
    dependsOn("verifyDesktopDistributable")
    archiveFileName.set(desktopBassPlatform.map { platform -> "Naviamp-$platform-$archiveNameSuffix.zip" })
    destinationDirectory.set(layout.buildDirectory.dir("compose/distributions"))
    from(desktopPackagedAppDir) {
        into(desktopPackagedAppName)
    }
}

tasks.register("verifyDesktopDistributable") {
    group = "verification"
    description = "Verifies that the desktop app image contains the native playback resources needed at runtime."
    dependsOn("createDistributable")

    doLast {
        val platform = desktopBassPlatform.get()
        val bassResourcesDir = desktopPackagedAppDir.get()
            .dir("Contents/app/resources/playback/bass/$platform")
            .asFile
            .takeIf { platform.startsWith("macos-") }
            ?: desktopPackagedAppDir.get()
                .dir("app/resources/playback/bass/$platform")
                .asFile
        val requiredLibraries = buildList {
            add(desktopLibraryName("bass", platform))
            add(desktopLibraryName("bassmix", platform))
            add(desktopLibraryName("bassflac", platform))
            add(desktopLibraryName("bassopus", platform))
            if (platform.startsWith("macos-")) {
                add(desktopLibraryName("naviamp_bass", platform))
            }
        }
        val missingLibraries = requiredLibraries.filterNot { bassResourcesDir.resolve(it).isFile }
        check(missingLibraries.isEmpty()) {
            "Desktop package is missing native playback resources in ${bassResourcesDir.absolutePath}: ${missingLibraries.joinToString()}"
        }
    }
}

tasks.register<Zip>("packageLocalDistributable") {
    description = "Builds and zips the local packaged desktop app from the working non-ProGuard app image."
    packageDesktopApp("local")
}

tasks.register<Zip>("packageReleaseDistributable") {
    description = "Builds and zips the release desktop app from the working non-ProGuard app image."
    packageDesktopApp("release")
}

tasks.register<Sync>("stageLocalTestApp") {
    group = "distribution"
    description = "Builds the local packaged desktop app and stages it under build/local-test."
    dependsOn("verifyDesktopDistributable")
    from(desktopPackagedAppDir)
    into(desktopLocalTestAppDir)
}

tasks.register<Sync>("stageReleaseApp") {
    group = "distribution"
    description = "Builds the release desktop app from the working non-ProGuard app image and stages it under build/release."
    dependsOn("verifyDesktopDistributable")
    from(desktopPackagedAppDir)
    into(desktopReleaseAppDir)
}

fun desktopNativePlatform(): String {
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
