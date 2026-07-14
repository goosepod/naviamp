import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val composeVersion = libs.versions.compose.get()
val naviampVersionName = rootProject.file("VERSION").readText().trim()
val naviampVersionCode = rootProject.file("VERSION_CODE").readText().trim()
val naviampNativePackageVersion = nativeDistributionPackageVersion(naviampVersionName)
val naviampWindowsPackageVersion = windowsDistributionPackageVersion(naviampVersionName)
val naviampLinuxPackageVersion = numericDistributionPackageVersion(naviampVersionName)
val desktopBassPlatform = providers.gradleProperty("naviamp.bass.platform")
    .orElse(desktopNativePlatform())
val desktopBassVendorDir = desktopBassPlatform.map { platform ->
    layout.projectDirectory.dir("vendor/bass/$platform")
}
val generatedDesktopBassResources = layout.buildDirectory.dir("generated/desktopBass")
val generatedDesktopBuildInfoResources = layout.buildDirectory.dir("generated/desktopBuildInfo")
val generateDesktopBuildInfo by tasks.registering {
    val outputFile = generatedDesktopBuildInfoResources.map { it.file("naviamp-build.properties").asFile }
    inputs.property("naviampVersionName", naviampVersionName)
    inputs.property("naviampVersionCode", naviampVersionCode)
    outputs.file(outputFile)
    doLast {
        outputFile.get().apply {
            parentFile.mkdirs()
            writeText(
                """
                version=$naviampVersionName
                buildNumber=$naviampVersionCode
                """.trimIndent() + "\n",
            )
        }
    }
}
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
val desktopCmakeExecutable = providers.environmentVariable("CMAKE_EXE")
    .orElse(providers.environmentVariable("LOCALAPPDATA").map { "$it/Android/Sdk/cmake/3.22.1/bin/cmake.exe" })
    .orElse(providers.provider { "cmake" })
val configureDesktopBassJni by tasks.registering(Exec::class) {
    val nativeProjectDir = rootProject.layout.projectDirectory.dir("native/bass-jni")
    onlyIf { desktopBassVendorDir.get().asFile.isDirectory }
    val cmakeArgs = mutableListOf(
        desktopCmakeExecutable.get(),
        "-S",
        nativeProjectDir.asFile.absolutePath,
        "-B",
        desktopBassJniBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${desktopBassJniBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${desktopBassJniBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${desktopBassJniBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=${desktopBassJniBuildDir.get().asFile.absolutePath}",
        "-DBASS_LIBRARY_DIR=${desktopBassVendorDir.get().asFile.absolutePath}",
    )
    if (desktopBassPlatform.get().startsWith("macos-")) {
        cmakeArgs += "-DCMAKE_OSX_ARCHITECTURES=${desktopCmakeArchitecture(desktopBassPlatform.get())}"
    }
    commandLine(cmakeArgs)
}
val buildDesktopBassJni by tasks.registering(Exec::class) {
    dependsOn(configureDesktopBassJni)
    onlyIf { desktopBassVendorDir.get().asFile.isDirectory }
    commandLine(desktopCmakeExecutable.get(), "--build", desktopBassJniBuildDir.get().asFile.absolutePath, "--config", "Release")
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
val desktopVisualizerMetalBuildDir = desktopBassPlatform.map { platform ->
    layout.buildDirectory.dir("generated/visualizerMetalBuild/$platform").get()
}
val desktopVisualizerMetalOutputFile = desktopVisualizerMetalBuildDir.zip(desktopBassPlatform) { buildDir, platform ->
    buildDir.file(desktopLibraryName("naviamp_visualizer_metal", platform)).asFile
}
val configureDesktopVisualizerMetal by tasks.registering(Exec::class) {
    val nativeProjectDir = rootProject.layout.projectDirectory.dir("native/visualizer-metal")
    onlyIf { desktopBassPlatform.get().startsWith("macos-") }
    commandLine(
        desktopCmakeExecutable.get(),
        "-S",
        nativeProjectDir.asFile.absolutePath,
        "-B",
        desktopVisualizerMetalBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${desktopVisualizerMetalBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${desktopVisualizerMetalBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${desktopVisualizerMetalBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=${desktopVisualizerMetalBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_OSX_ARCHITECTURES=${desktopCmakeArchitecture(desktopBassPlatform.get())}",
    )
}
val buildDesktopVisualizerMetal by tasks.registering(Exec::class) {
    dependsOn(configureDesktopVisualizerMetal)
    onlyIf { desktopBassPlatform.get().startsWith("macos-") }
    commandLine(desktopCmakeExecutable.get(), "--build", desktopVisualizerMetalBuildDir.get().asFile.absolutePath, "--config", "Release")
}
val copyDesktopVisualizerMetal by tasks.registering(Copy::class) {
    dependsOn(buildDesktopVisualizerMetal)
    from(desktopVisualizerMetalOutputFile)
    into(generatedDesktopBassResources.zip(desktopBassPlatform) { resources, platform ->
        resources.dir("playback/bass/$platform")
    })
    filePermissions {
        unix("755")
    }
    onlyIf { desktopVisualizerMetalOutputFile.get().isFile }
}
val copyDesktopVisualizerMetalAppResources by tasks.registering(Copy::class) {
    dependsOn(buildDesktopVisualizerMetal)
    from(desktopVisualizerMetalOutputFile)
    into(generatedDesktopBassAppResources.zip(desktopBassPlatform) { resources, platform ->
        resources.dir("$platform/playback/bass/$platform")
    })
    filePermissions {
        unix("755")
    }
    onlyIf { desktopVisualizerMetalOutputFile.get().isFile }
}
val desktopVisualizerOpenGlBuildDir = desktopBassPlatform.map { platform ->
    layout.buildDirectory.dir("generated/visualizerOpenGlBuild/$platform").get()
}
val desktopVisualizerOpenGlOutputFile = desktopVisualizerOpenGlBuildDir.zip(desktopBassPlatform) { buildDir, platform ->
    buildDir.file(desktopLibraryName("naviamp_visualizer_opengl", platform)).asFile
}
val configureDesktopVisualizerOpenGl by tasks.registering(Exec::class) {
    val nativeProjectDir = rootProject.layout.projectDirectory.dir("native/visualizer-opengl")
    onlyIf { desktopBassPlatform.get().startsWith("windows-") }
    commandLine(
        desktopCmakeExecutable.get(),
        "-S",
        nativeProjectDir.asFile.absolutePath,
        "-B",
        desktopVisualizerOpenGlBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${desktopVisualizerOpenGlBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${desktopVisualizerOpenGlBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${desktopVisualizerOpenGlBuildDir.get().asFile.absolutePath}",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=${desktopVisualizerOpenGlBuildDir.get().asFile.absolutePath}",
    )
}
val buildDesktopVisualizerOpenGl by tasks.registering(Exec::class) {
    dependsOn(configureDesktopVisualizerOpenGl)
    onlyIf { desktopBassPlatform.get().startsWith("windows-") }
    commandLine(desktopCmakeExecutable.get(), "--build", desktopVisualizerOpenGlBuildDir.get().asFile.absolutePath, "--config", "Release")
}
val copyDesktopVisualizerOpenGl by tasks.registering(Copy::class) {
    dependsOn(buildDesktopVisualizerOpenGl)
    from(desktopVisualizerOpenGlOutputFile)
    into(generatedDesktopBassResources.zip(desktopBassPlatform) { resources, platform ->
        resources.dir("playback/bass/$platform")
    })
    onlyIf { desktopVisualizerOpenGlOutputFile.get().isFile }
}
val copyDesktopVisualizerOpenGlAppResources by tasks.registering(Copy::class) {
    dependsOn(buildDesktopVisualizerOpenGl)
    from(desktopVisualizerOpenGlOutputFile)
    into(generatedDesktopBassAppResources.zip(desktopBassPlatform) { resources, platform ->
        resources.dir("$platform/playback/bass/$platform")
    })
    onlyIf { desktopVisualizerOpenGlOutputFile.get().isFile }
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
            resources.srcDir(generatedDesktopBuildInfoResources)

            dependencies {
                implementation(project(":core:domain"))
                implementation(project(":core:storage"))
                implementation(project(":core:ui"))
                implementation(project(":providers:navidrome"))
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.material3)
                implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.cio)
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

compose.resources {
    generateResClass = always
    packageOfResClass = "app.naviamp.desktop.generated.resources"
}

composeCompiler {
    includeSourceInformation = false
    includeTraceMarkers = false
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
                val windowsSkikoRenderApi = providers.gradleProperty("naviamp.windows.skiko.renderApi")
                    .orElse("DEFAULT")
                    .get()
                    .trim()
                if (windowsSkikoRenderApi.isNotBlank() && !windowsSkikoRenderApi.equals("DEFAULT", ignoreCase = true)) {
                    jvmArgs += "-Dskiko.renderApi=$windowsSkikoRenderApi"
                }
                jvmArgs += "-Dnaviamp.visualizer.windowsOpenGl=true"
            }
            desktopBassPlatform.get().startsWith("macos-") -> {
                jvmArgs += "-Dskiko.renderApi=METAL"
                jvmArgs += "-Dnaviamp.visualizer.macosMetal=true"
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
            packageVersion = naviampNativePackageVersion
            description = "A native Navidrome music client with BASS-backed playback."
            vendor = "Naviamp"
            copyright = "Copyright 2026 Naviamp contributors"
            licenseFile.set(rootProject.file("LICENSE"))
            appResourcesRootDir.set(generatedDesktopBassAppResources)
            modules("java.net.http", "java.sql")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icons/naviamp.ico"))
                upgradeUuid = "ddece57e-59fc-4ff9-915d-875876779251"
                msiPackageVersion = naviampWindowsPackageVersion
                exePackageVersion = naviampWindowsPackageVersion
                menu = true
                menuGroup = "Naviamp"
            }
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/icons/naviamp.icns"))
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/icons/naviamp.png"))
                packageName = "naviamp"
                packageVersion = naviampLinuxPackageVersion
                debPackageVersion = naviampLinuxPackageVersion
                rpmPackageVersion = naviampLinuxPackageVersion
                appRelease = "1"
                shortcut = true
                debMaintainer = "Naviamp Maintainers"
                menuGroup = "AudioVideo"
                appCategory = "AudioVideo"
                rpmLicenseType = "MIT"
            }
        }
    }
}

tasks.matching { it.name == "desktopProcessResources" || it.name == "processDesktopMainResources" }
    .configureEach {
        dependsOn(generateDesktopBuildInfo)
        dependsOn(copyDesktopBass)
        dependsOn(copyDesktopBassJni)
        dependsOn(copyDesktopVisualizerMetal)
        dependsOn(copyDesktopVisualizerOpenGl)
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
    dependsOn(copyDesktopVisualizerMetalAppResources)
    dependsOn(copyDesktopVisualizerOpenGlAppResources)
}

tasks.matching {
    it.name == "createDistributable" ||
        it.name == "createReleaseDistributable" ||
        it.name == "packageDistributionForCurrentOS" ||
        it.name == "packageReleaseDistributionForCurrentOS"
}.configureEach {
    doLast {
        syncDesktopNativeAppResources()
        markDesktopVisualizerMetalExecutable()
        patchMacAppBundleVersion()
    }
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
        syncDesktopNativeAppResources()
        markDesktopVisualizerMetalExecutable()
        val platform = desktopBassPlatform.get()
        val bassResourcesDir = desktopPackagedResourcesDir(platform)
            .resolve("playback/bass/$platform")
        val requiredLibraries = buildList {
            add(desktopLibraryName("bass", platform))
            add(desktopLibraryName("bassmix", platform))
            add(desktopLibraryName("bassflac", platform))
            add(desktopLibraryName("bassopus", platform))
            if (platform.startsWith("macos-") || platform.startsWith("windows-") || platform.startsWith("linux-")) {
                add(desktopLibraryName("naviamp_bass", platform))
            }
            if (platform.startsWith("macos-")) {
                add(desktopLibraryName("naviamp_visualizer_metal", platform))
            }
            if (platform.startsWith("windows-")) {
                add(desktopLibraryName("naviamp_visualizer_opengl", platform))
            }
        }
        val missingLibraries = requiredLibraries.filterNot { bassResourcesDir.resolve(it).isFile }
        check(missingLibraries.isEmpty()) {
            "Desktop package is missing native playback resources in ${bassResourcesDir.absolutePath}: ${missingLibraries.joinToString()}"
        }
        if (platform.startsWith("macos-")) {
            val visualizerMetal = bassResourcesDir.resolve(desktopLibraryName("naviamp_visualizer_metal", platform))
            check(visualizerMetal.canExecute()) {
                "Desktop package Metal visualizer library is not executable: ${visualizerMetal.absolutePath}"
            }
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

fun syncDesktopNativeAppResources() {
    val platform = desktopBassPlatform.get()
    val sourceRoot = generatedDesktopBassAppResources.get()
        .dir(platform)
        .asFile
    if (!sourceRoot.isDirectory) return

    val resourcesRoot = desktopPackagedResourcesDir(platform)
    staleDesktopPackagedResourcesDirs(platform).forEach { staleResourcesRoot ->
        delete(staleResourcesRoot.resolve("playback/bass/$platform"))
    }
    copy {
        from(sourceRoot)
        into(resourcesRoot)
    }
}

fun desktopPackagedResourcesDir(platform: String) =
    when {
        platform.startsWith("macos-") -> desktopPackagedAppDir.get().dir("Contents/app/resources").asFile
        platform.startsWith("linux-") -> desktopPackagedAppDir.get().dir("lib/app/resources").asFile
        else -> desktopPackagedAppDir.get().dir("app/resources").asFile
    }

fun staleDesktopPackagedResourcesDirs(platform: String) =
    when {
        platform.startsWith("linux-") -> listOf(desktopPackagedAppDir.get().dir("app/resources").asFile)
        else -> emptyList()
    }

fun nativeDistributionPackageVersion(version: String): String {
    val coreVersion = numericDistributionPackageVersion(version).substringBefore('-').substringBefore('+')
    val parts = coreVersion.split(".")
    require(parts.size == 3) {
        "VERSION must be major.minor.patch for desktop packaging, got: $version"
    }
    val major = parts[0].toInt()
    val minor = parts[1].toInt()
    val patch = parts[2].toInt()
    val nativeMajor = major.takeIf { it > 0 } ?: 1
    return "$nativeMajor.$minor.$patch"
}

fun windowsDistributionPackageVersion(version: String): String {
    val coreVersion = numericDistributionPackageVersion(version).substringBefore('-').substringBefore('+')
    val parts = coreVersion.split(".")
    require(parts.size == 3) {
        "VERSION must be major.minor.patch for Windows packaging, got: $version"
    }
    parts.forEach { it.toInt() }
    return coreVersion
}

fun numericDistributionPackageVersion(version: String): String =
    version.removePrefix("v")

fun desktopCmakeArchitecture(platform: String): String =
    when {
        platform.endsWith("-arm64") -> "arm64"
        platform.endsWith("-x64") -> "x86_64"
        else -> System.getProperty("os.arch")
    }

fun markDesktopVisualizerMetalExecutable() {
    val platform = desktopBassPlatform.get()
    if (!platform.startsWith("macos-")) return
    val visualizerMetal = desktopPackagedAppDir.get()
        .dir("Contents/app/resources/playback/bass/$platform")
        .file(desktopLibraryName("naviamp_visualizer_metal", platform))
        .asFile
    if (visualizerMetal.isFile) {
        visualizerMetal.setExecutable(true, false)
    }
}

fun patchMacAppBundleVersion() {
    val platform = desktopBassPlatform.get()
    if (!platform.startsWith("macos-")) return
    val infoPlist = desktopPackagedAppDir.get()
        .file("Contents/Info.plist")
        .asFile
    if (!infoPlist.isFile) return

    val patched = infoPlist.readText()
        .replacePlistStringValue("CFBundleShortVersionString", naviampVersionName)
        .replacePlistStringValue("CFBundleVersion", naviampVersionName)
    infoPlist.writeText(patched)
}

fun String.replacePlistStringValue(key: String, value: String): String =
    replace(
        Regex("(<key>${Regex.escape(key)}</key>\\s*<string>)([^<]*)(</string>)"),
    ) { match -> "${match.groupValues[1]}$value${match.groupValues[3]}" }
