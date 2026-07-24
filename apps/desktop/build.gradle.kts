import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val naviampVersionName = rootProject.file("VERSION").readText().trim()
val naviampNativePackageVersion = nativeDistributionPackageVersion(naviampVersionName)
val naviampWindowsPackageVersion = windowsDistributionPackageVersion(naviampVersionName)
val naviampLinuxPackageVersion = linuxDistributionPackageVersion(naviampVersionName)
val desktopNativePlatform = providers.gradleProperty("naviamp.bass.platform")
    .orElse(providers.provider(::desktopNativePlatformId))
val generatedDesktopNativeAppResources =
    rootProject.layout.projectDirectory.dir("platforms/desktop/build/generated/desktopNativeAppResources")
val desktopPackagedAppName = desktopNativePlatform.map { platform ->
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
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":core:presentation"))
                implementation(project(":core:ui"))
                implementation(project(":platforms:desktop"))
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(compose.desktop.currentOs)
                implementation(libs.compose.material3)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(project(":core:testkit"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
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
            "-Xms64m",
            "-Xmx320m",
            "-XX:SoftMaxHeapSize=192m",
            "-XX:G1PeriodicGCInterval=30000",
        )
        when {
            desktopNativePlatform.get().startsWith("windows-") -> {
                val renderApi = providers.gradleProperty("naviamp.windows.skiko.renderApi")
                    .orElse("DEFAULT")
                    .get()
                    .trim()
                if (renderApi.isNotBlank() && !renderApi.equals("DEFAULT", ignoreCase = true)) {
                    jvmArgs += "-Dskiko.renderApi=$renderApi"
                }
                jvmArgs += "-Dnaviamp.visualizer.windowsOpenGl=true"
            }
            desktopNativePlatform.get().startsWith("macos-") -> {
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
            appResourcesRootDir.set(generatedDesktopNativeAppResources)
            modules("java.net.http", "java.sql")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)

            windows {
                iconFile.set(rootProject.file("platforms/desktop/src/desktopMain/resources/icons/naviamp.ico"))
                upgradeUuid = "ddece57e-59fc-4ff9-915d-875876779251"
                msiPackageVersion = naviampWindowsPackageVersion
                exePackageVersion = naviampWindowsPackageVersion
                menu = true
                menuGroup = "Naviamp"
            }
            macOS {
                iconFile.set(rootProject.file("platforms/desktop/src/desktopMain/resources/icons/naviamp.icns"))
            }
            linux {
                iconFile.set(rootProject.file("platforms/desktop/src/desktopMain/resources/icons/naviamp.png"))
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

tasks.matching {
    it.name in setOf(
        "run",
        "prepareAppResources",
        "createDistributable",
        "createReleaseDistributable",
        "packageDistributionForCurrentOS",
        "packageReleaseDistributionForCurrentOS",
    )
}.configureEach {
    dependsOn(":platforms:desktop:verifyDesktopNativeInputs")
    dependsOn(":platforms:desktop:prepareDesktopNativeAppResources")
}

tasks.matching {
    it.name in setOf(
        "createDistributable",
        "createReleaseDistributable",
        "packageDistributionForCurrentOS",
        "packageReleaseDistributionForCurrentOS",
    )
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
    archiveFileName.set(desktopNativePlatform.map { platform -> "Naviamp-$platform-$archiveNameSuffix.zip" })
    destinationDirectory.set(layout.buildDirectory.dir("compose/distributions"))
    from(desktopPackagedAppDir) {
        into(desktopPackagedAppName)
    }
}

tasks.register("verifyDesktopPackagingInputs") {
    group = "verification"
    description = "Verifies Desktop icons and platform-native packaging inputs."
    dependsOn(":platforms:desktop:verifyDesktopNativeInputs")

    doLast {
        val icons = listOf(
            rootProject.file("platforms/desktop/src/desktopMain/resources/icons/naviamp.icns"),
            rootProject.file("platforms/desktop/src/desktopMain/resources/icons/naviamp.ico"),
            rootProject.file("platforms/desktop/src/desktopMain/resources/icons/naviamp.png"),
        )
        check(icons.all { it.isFile && it.length() > 0L }) {
            "Desktop packaging icons are missing: ${icons.filterNot { it.isFile && it.length() > 0L }.joinToString()}"
        }
    }
}

tasks.register("verifyDesktopDistributable") {
    group = "verification"
    description = "Verifies that the Desktop app image contains every required native playback resource."
    dependsOn("verifyDesktopPackagingInputs")
    dependsOn("createDistributable")

    doLast {
        syncDesktopNativeAppResources()
        markDesktopVisualizerMetalExecutable()
        val platform = desktopNativePlatform.get()
        val bassResourcesDirectory = desktopPackagedResourcesDir(platform).resolve("playback/bass/$platform")
        val requiredLibraries = buildList {
            add(desktopLibraryName("bass", platform))
            add(desktopLibraryName("bassmix", platform))
            add(desktopLibraryName("bassflac", platform))
            add(desktopLibraryName("bassopus", platform))
            add(desktopLibraryName("naviamp_bass", platform))
            if (platform.startsWith("macos-")) add(desktopLibraryName("naviamp_visualizer_metal", platform))
            if (platform.startsWith("windows-")) add(desktopLibraryName("naviamp_visualizer_opengl", platform))
        }
        val missing = requiredLibraries.filterNot { bassResourcesDirectory.resolve(it).isFile }
        check(missing.isEmpty()) {
            "Desktop package is missing native playback resources in ${bassResourcesDirectory.absolutePath}: ${missing.joinToString()}"
        }
        if (platform.startsWith("macos-")) {
            val visualizer = bassResourcesDirectory.resolve(desktopLibraryName("naviamp_visualizer_metal", platform))
            check(visualizer.canExecute()) {
                "Desktop package Metal visualizer library is not executable: ${visualizer.absolutePath}"
            }
        }
    }
}

tasks.register<Zip>("packageLocalDistributable") {
    description = "Builds and zips the local packaged Desktop app image."
    packageDesktopApp("local")
}

tasks.register<Zip>("packageReleaseDistributable") {
    description = "Builds and zips the release Desktop app image."
    packageDesktopApp("release")
}

tasks.register<Sync>("stageLocalTestApp") {
    group = "distribution"
    description = "Builds and stages the local Desktop app under build/local-test."
    dependsOn("verifyDesktopDistributable")
    from(desktopPackagedAppDir)
    into(desktopLocalTestAppDir)
    doLast {
        refreshMacAppBundleModificationTime(desktopLocalTestAppDir.get().asFile)
    }
}

tasks.register<Sync>("stageReleaseApp") {
    group = "distribution"
    description = "Builds and stages the release Desktop app under build/release."
    dependsOn("verifyDesktopDistributable")
    from(desktopPackagedAppDir)
    into(desktopReleaseAppDir)
    doLast {
        refreshMacAppBundleModificationTime(desktopReleaseAppDir.get().asFile)
    }
}

fun syncDesktopNativeAppResources() {
    val platform = desktopNativePlatform.get()
    val sourceDirectory = generatedDesktopNativeAppResources.dir(platform).asFile
    if (!sourceDirectory.isDirectory) return
    val resourcesDirectory = desktopPackagedResourcesDir(platform)
    staleDesktopPackagedResourcesDirs(platform).forEach { staleDirectory ->
        delete(staleDirectory.resolve("playback/bass/$platform"))
    }
    copy {
        from(sourceDirectory)
        into(resourcesDirectory)
    }
}

fun desktopPackagedResourcesDir(platform: String) = when {
    platform.startsWith("macos-") -> desktopPackagedAppDir.get().dir("Contents/app/resources").asFile
    platform.startsWith("linux-") -> desktopPackagedAppDir.get().dir("lib/app/resources").asFile
    else -> desktopPackagedAppDir.get().dir("app/resources").asFile
}

fun staleDesktopPackagedResourcesDirs(platform: String) = when {
    platform.startsWith("linux-") -> listOf(desktopPackagedAppDir.get().dir("app/resources").asFile)
    else -> emptyList()
}

fun markDesktopVisualizerMetalExecutable() {
    val platform = desktopNativePlatform.get()
    if (!platform.startsWith("macos-")) return
    val visualizer = desktopPackagedAppDir.get()
        .dir("Contents/app/resources/playback/bass/$platform")
        .file(desktopLibraryName("naviamp_visualizer_metal", platform))
        .asFile
    if (visualizer.isFile) visualizer.setExecutable(true, false)
}

fun patchMacAppBundleVersion() {
    val platform = desktopNativePlatform.get()
    if (!platform.startsWith("macos-")) return
    val infoPlist = desktopPackagedAppDir.get().file("Contents/Info.plist").asFile
    if (!infoPlist.isFile) return
    infoPlist.writeText(
        infoPlist.readText()
            .replacePlistStringValue("CFBundleShortVersionString", naviampVersionName)
            .replacePlistStringValue("CFBundleVersion", naviampVersionName),
    )
    refreshMacAppBundleModificationTime(desktopPackagedAppDir.get().asFile)
}

fun refreshMacAppBundleModificationTime(appDirectory: File) {
    if (appDirectory.extension == "app" && appDirectory.isDirectory) {
        check(appDirectory.setLastModified(System.currentTimeMillis())) {
            "Could not refresh macOS app bundle modification time: ${appDirectory.absolutePath}"
        }
    }
}

fun String.replacePlistStringValue(key: String, value: String): String = replace(
    Regex("(<key>${Regex.escape(key)}</key>\\s*<string>)([^<]*)(</string>)"),
) { match -> "${match.groupValues[1]}$value${match.groupValues[3]}" }

fun desktopNativePlatformId(): String {
    val os = System.getProperty("os.name").lowercase().let { name ->
        when {
            name.contains("mac") || name.contains("darwin") -> "macos"
            name.contains("win") -> "windows"
            name.contains("linux") -> "linux"
            else -> name.filter(Char::isLetterOrDigit).ifBlank { "unknown" }
        }
    }
    val architecture = System.getProperty("os.arch").lowercase().let { name ->
        when (name) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "x64"
            else -> name.filter(Char::isLetterOrDigit).ifBlank { "unknown" }
        }
    }
    return "$os-$architecture"
}

fun desktopLibraryName(stem: String, platform: String): String = when {
    platform.startsWith("windows-") -> "$stem.dll"
    platform.startsWith("macos-") -> "lib$stem.dylib"
    else -> "lib$stem.so"
}

fun nativeDistributionPackageVersion(version: String): String {
    val parts = numericDistributionPackageVersion(version).substringBefore('-').substringBefore('+').split('.')
    require(parts.size == 3) { "VERSION must be major.minor.patch for Desktop packaging, got: $version" }
    val major = parts[0].toInt().takeIf { it > 0 } ?: 1
    return "$major.${parts[1].toInt()}.${parts[2].toInt()}"
}

fun windowsDistributionPackageVersion(version: String): String =
    numericDistributionPackageVersion(version).substringBefore('-').substringBefore('+').also { coreVersion ->
        val parts = coreVersion.split('.')
        require(parts.size == 3) { "VERSION must be major.minor.patch for Windows packaging, got: $version" }
        parts.forEach { it.toInt() }
    }

fun linuxDistributionPackageVersion(version: String): String =
    numericDistributionPackageVersion(version).substringBefore('+').replace('-', '~')

fun numericDistributionPackageVersion(version: String): String = version.removePrefix("v")
