import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kover)
}

val naviampVersionName = rootProject.file("VERSION").readText().trim()
val naviampVersionCode = rootProject.file("VERSION_CODE").readText().trim().toInt()
val naviampNativePackageVersion = nativeDistributionPackageVersion(naviampVersionName)
val naviampWindowsPackageVersion = windowsDistributionPackageVersion(naviampVersionName, naviampVersionCode)
val naviampLinuxPackageVersion = linuxDistributionPackageVersion(naviampVersionName)
val desktopExecutableDescription = "Naviamp"
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
val desktopReleasePackagedAppDir = desktopPackagedAppName.flatMap { appName ->
    layout.buildDirectory.dir("compose/binaries/main-release/app/$appName")
}
val desktopLocalTestAppDir = desktopPackagedAppName.flatMap { appName ->
    rootProject.layout.buildDirectory.dir("local-test/$appName")
}
val desktopReleaseAppDir = desktopPackagedAppName.flatMap { appName ->
    rootProject.layout.buildDirectory.dir("release/$appName")
}
val desktopReleaseArtifactsDir = layout.buildDirectory.dir("release-artifacts")
// Per-user packages use a distinct upgrade family so Windows never tries to remove a legacy
// machine-wide installation (and request elevation) while installing the current-user package.
val windowsInstallerUpgradeUuid = "75051da2-0b4b-4f67-81d9-56d2e36a6437"
val windowsInstallerResourcesDir = layout.buildDirectory.dir("windows-installer-resources")
val windowsInstallerIcon = rootProject.file("platforms/desktop/src/desktopMain/resources/icons/naviamp.ico")

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
            // Track transitions briefly overlap streaming/prefetch TLS buffers, artwork decoding,
            // and sidecar work. The retained heap remains small, but the old 320 MB ceiling could
            // trap G1 in allocation-failure collections before those temporary buffers unwound.
            "-Xmx512m",
            "-XX:SoftMaxHeapSize=320m",
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
                jvmArgs += "-Xdock:icon=\$APPDIR/resources/icons/naviamp.png"
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
            // jpackage maps this value to the Windows executable's FileDescription, which is
            // what Task Manager displays as the application name.
            description = desktopExecutableDescription
            vendor = "Naviamp"
            copyright = "Copyright 2026 Naviamp contributors"
            licenseFile.set(rootProject.file("LICENSE"))
            appResourcesRootDir.set(generatedDesktopNativeAppResources)
            modules("java.net.http", "java.sql")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)

            windows {
                iconFile.set(windowsInstallerIcon)
                upgradeUuid = windowsInstallerUpgradeUuid
                msiPackageVersion = naviampWindowsPackageVersion
                exePackageVersion = naviampWindowsPackageVersion
                perUserInstall = true
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

tasks.withType<AbstractJPackageTask>().configureEach {
    when (targetFormat) {
        TargetFormat.Deb -> freeArgs.addAll(
            "--linux-package-deps",
            "libsecret-tools,gnome-keyring",
        )
        TargetFormat.Rpm -> freeArgs.addAll(
            "--linux-package-deps",
            "libsecret,gnome-keyring",
        )
        else -> Unit
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
        val appDirectory = if (name.contains("Release")) {
            desktopReleasePackagedAppDir.get().asFile
        } else {
            desktopPackagedAppDir.get().asFile
        }
        syncDesktopNativeAppResources(appDirectory)
        markDesktopVisualizerMetalExecutable(appDirectory)
        patchMacAppBundleVersion(appDirectory)
        sealMacAppBundle(appDirectory)
    }
}

fun Zip.packageDesktopApp(
    archiveNameSuffix: String,
    release: Boolean = false,
) {
    group = "distribution"
    dependsOn(if (release) "createReleaseDistributable" else "verifyDesktopDistributable")
    archiveFileName.set(
        desktopNativePlatform.map { platform -> "Naviamp-$naviampVersionName-$platform-$archiveNameSuffix.zip" },
    )
    destinationDirectory.set(layout.buildDirectory.dir("compose/distributions"))
    from(if (release) desktopReleasePackagedAppDir else desktopPackagedAppDir) {
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

tasks.register("verifyReleaseVersionMetadata") {
    group = "verification"
    description = "Verifies prerelease package versions remain installable and release filenames remain descriptive."
    doLast {
        check(windowsDistributionPackageVersion("v2.0.0-alpha.3", 38) == "2.0.38")
        check(windowsDistributionPackageVersion("v2.0.0-beta.1", 40) == "2.0.40")
        check(windowsDistributionPackageVersion("v2.0.0-beta.2", 41) == "2.0.41")
        check(windowsDistributionPackageVersion("v2.0.0", 42) == "2.0.42")
        check(linuxDistributionPackageVersion("v2.0.0-beta.1") == "2.0.0~beta.1")
        check(linuxDistributionPackageVersion("v2.0.0-beta.2") == "2.0.0~beta.2")
        check(nativeDistributionPackageVersion("v2.0.0-beta.1") == "2.0.0")
        check(desktopExecutableDescription == "Naviamp")
        val launchEnabledWindowsTemplate = windowsMainWxsWithOptionalLaunch("<UIRef Id=\"JpUI\"/>")
        check(launchEnabledWindowsTemplate.contains("WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT"))
        check(launchEnabledWindowsTemplate.contains("Value=\"LaunchNaviamp\""))
        check(launchEnabledWindowsTemplate.contains("[INSTALLDIR]Naviamp.exe"))
        check("Naviamp-$naviampVersionName-${desktopNativePlatform.get()}.msi".contains(naviampVersionName))
        if (desktopNativePlatform.get().startsWith("linux-")) {
            val packageDependencies = tasks.withType<AbstractJPackageTask>()
                .associate { it.targetFormat to it.freeArgs.get() }
            check(packageDependencies[TargetFormat.Deb].orEmpty().contains("libsecret-tools,gnome-keyring"))
            check(packageDependencies[TargetFormat.Rpm].orEmpty().contains("libsecret,gnome-keyring"))
        }
        logger.lifecycle(
            "Release version metadata: semantic=$naviampVersionName windows=$naviampWindowsPackageVersion " +
                "native=$naviampNativePackageVersion linux=$naviampLinuxPackageVersion",
        )
    }
}

tasks.register("verifyDesktopDistributable") {
    group = "verification"
    description = "Verifies that the Desktop app image contains every required native playback resource."
    dependsOn("verifyDesktopPackagingInputs")
    dependsOn("createDistributable")

    doLast {
        val appDirectory = desktopPackagedAppDir.get().asFile
        syncDesktopNativeAppResources(appDirectory)
        markDesktopVisualizerMetalExecutable(appDirectory)
        val platform = desktopNativePlatform.get()
        val bassResourcesDirectory = desktopPackagedResourcesDir(platform, appDirectory).resolve("playback/bass/$platform")
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
            val applicationIcon = desktopPackagedResourcesDir(platform, appDirectory)
                .resolve("icons/naviamp.png")
            check(applicationIcon.isFile && applicationIcon.length() > 0L) {
                "Desktop package is missing its macOS JVM application icon: ${applicationIcon.absolutePath}"
            }
            val launcherConfig = appDirectory.resolve("Contents/app/Naviamp.cfg")
            check(launcherConfig.readText().contains("-Xdock:icon=\$APPDIR/resources/icons/naviamp.png")) {
                "Desktop package does not configure the macOS JVM application icon."
            }
            val visualizer = bassResourcesDirectory.resolve(desktopLibraryName("naviamp_visualizer_metal", platform))
            check(visualizer.canExecute()) {
                "Desktop package Metal visualizer library is not executable: ${visualizer.absolutePath}"
            }
            sealMacAppBundle(appDirectory)
            verifyMacAppBundleSeal(appDirectory)
        }
    }
}

tasks.register<Zip>("packageLocalDistributable") {
    description = "Builds and zips the local packaged Desktop app image."
    packageDesktopApp("local")
}

tasks.register<Zip>("packageReleaseDistributable") {
    description = "Builds and zips the release Desktop app image."
    packageDesktopApp("release", release = true)
}

fun registerWindowsInstaller(format: String) = tasks.register("packageReleaseWindows${format.uppercase()}") {
    group = "distribution"
    description = "Builds the per-user Windows ${format.uppercase()} installer with an optional post-install launch."
    dependsOn("createReleaseDistributable")

    val outputDirectory = layout.buildDirectory.dir("compose/binaries/main-release/$format")
    val outputFile = outputDirectory.map { it.file("Naviamp-$naviampWindowsPackageVersion.$format") }
    inputs.dir(desktopReleasePackagedAppDir)
    inputs.file(rootProject.file("LICENSE"))
    inputs.file(windowsInstallerIcon)
    outputs.file(outputFile)

    doLast {
        check(System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "Windows installers must be built on Windows."
        }

        val javaHome = File(System.getProperty("java.home"))
        val jmodTool = javaHome.resolve("bin/jmod.exe")
        val jpackageTool = javaHome.resolve("bin/jpackage.exe")
        val jpackageModule = javaHome.resolve("jmods/jdk.jpackage.jmod")
        check(jmodTool.isFile && jpackageTool.isFile && jpackageModule.isFile) {
            "The active JDK must include jmod, jpackage, and jdk.jpackage.jmod."
        }

        val resourcesDirectory = windowsInstallerResourcesDir.get().asFile.resolve(format)
        val extractedJmodDirectory = resourcesDirectory.resolve("jmod")
        delete(resourcesDirectory)
        resourcesDirectory.mkdirs()
        project.exec {
            commandLine(
                jmodTool.absolutePath,
                "extract",
                "--dir",
                extractedJmodDirectory.absolutePath,
                jpackageModule.absolutePath,
            )
        }.assertNormalExitValue()

        val defaultMainWxs = extractedJmodDirectory
            .resolve("classes/jdk/jpackage/internal/resources/main.wxs")
        check(defaultMainWxs.isFile) {
            "The active JDK does not contain the expected Windows jpackage main.wxs template."
        }
        resourcesDirectory.resolve("main.wxs").writeText(
            windowsMainWxsWithOptionalLaunch(defaultMainWxs.readText()),
        )

        val destination = outputDirectory.get().asFile
        delete(destination)
        destination.mkdirs()
        project.exec {
            commandLine(
                jpackageTool.absolutePath,
                "--type", format,
                "--name", "Naviamp",
                "--app-version", naviampWindowsPackageVersion,
                "--app-image", desktopReleasePackagedAppDir.get().asFile.absolutePath,
                "--dest", destination.absolutePath,
                "--description", desktopExecutableDescription,
                "--vendor", "Naviamp",
                "--copyright", "Copyright 2026 Naviamp contributors",
                "--license-file", rootProject.file("LICENSE").absolutePath,
                "--icon", windowsInstallerIcon.absolutePath,
                "--resource-dir", resourcesDirectory.absolutePath,
                "--win-per-user-install",
                "--win-menu",
                "--win-menu-group", "Naviamp",
                "--win-upgrade-uuid", windowsInstallerUpgradeUuid,
            )
        }.assertNormalExitValue()
        check(outputFile.get().asFile.isFile) {
            "Windows ${format.uppercase()} installer was not produced: ${outputFile.get().asFile.absolutePath}"
        }
    }
}

val packageReleaseWindowsMsi = registerWindowsInstaller("msi")
val packageReleaseWindowsExe = registerWindowsInstaller("exe")

tasks.register("packageReleaseWindowsInstallers") {
    group = "distribution"
    description = "Builds the per-user Windows MSI and EXE installers."
    dependsOn(packageReleaseWindowsMsi, packageReleaseWindowsExe)
}

tasks.register("stageReleaseArtifacts") {
    group = "distribution"
    description = "Builds and stages release packages with the complete semantic version in every filename."
    dependsOn("packageReleaseDistributable")
    dependsOn(
        if (desktopNativePlatform.get().startsWith("windows-")) {
            "packageReleaseWindowsInstallers"
        } else {
            "packageReleaseDistributionForCurrentOS"
        },
    )
    outputs.dir(desktopReleaseArtifactsDir)

    doLast {
        val platform = desktopNativePlatform.get()
        val destination = desktopReleaseArtifactsDir.get().asFile
        delete(destination)
        destination.mkdirs()

        val zip = layout.buildDirectory
            .file("compose/distributions/Naviamp-$naviampVersionName-$platform-release.zip")
            .get()
            .asFile
        check(zip.isFile) { "Versioned Desktop release zip was not produced: ${zip.absolutePath}" }
        copy { from(zip); into(destination) }

        val extensions = when {
            platform.startsWith("windows-") -> listOf("msi", "exe")
            platform.startsWith("macos-") -> listOf("dmg")
            platform.startsWith("linux-") -> listOf("deb", "rpm")
            else -> error("Unsupported Desktop release platform: $platform")
        }
        val binaries = layout.buildDirectory.dir("compose/binaries").get().asFile
        extensions.forEach { extension ->
            val source = binaries.walkTopDown()
                .filter { it.isFile && it.extension.equals(extension, ignoreCase = true) }
                .maxByOrNull(File::lastModified)
                ?: error("Desktop .$extension release package was not produced under ${binaries.absolutePath}")
            copy {
                from(source)
                into(destination)
                rename { "Naviamp-$naviampVersionName-$platform.$extension" }
            }
        }
    }
}

tasks.register<Sync>("stageLocalTestApp") {
    group = "distribution"
    description = "Builds and stages the local Desktop app under build/local-test."
    dependsOn("verifyDesktopDistributable")
    from(desktopPackagedAppDir)
    into(desktopLocalTestAppDir)
    doFirst {
        delete(desktopLocalTestAppDir)
    }
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
    doFirst {
        delete(desktopReleaseAppDir)
    }
    doLast {
        refreshMacAppBundleModificationTime(desktopReleaseAppDir.get().asFile)
    }
}

fun syncDesktopNativeAppResources(appDirectory: File) {
    val platform = desktopNativePlatform.get()
    val sourceDirectory = generatedDesktopNativeAppResources.dir(platform).asFile
    if (!sourceDirectory.isDirectory) return
    val resourcesDirectory = desktopPackagedResourcesDir(platform, appDirectory)
    staleDesktopPackagedResourcesDirs(platform, appDirectory).forEach { staleDirectory ->
        delete(staleDirectory.resolve("playback/bass/$platform"))
    }
    copy {
        from(sourceDirectory)
        into(resourcesDirectory)
    }
}

fun desktopPackagedResourcesDir(platform: String, appDirectory: File) = when {
    platform.startsWith("macos-") -> appDirectory.resolve("Contents/app/resources")
    platform.startsWith("linux-") -> appDirectory.resolve("lib/app/resources")
    else -> appDirectory.resolve("app/resources")
}

fun staleDesktopPackagedResourcesDirs(platform: String, appDirectory: File) = when {
    platform.startsWith("linux-") -> listOf(appDirectory.resolve("app/resources"))
    else -> emptyList()
}

fun markDesktopVisualizerMetalExecutable(appDirectory: File) {
    val platform = desktopNativePlatform.get()
    if (!platform.startsWith("macos-")) return
    val visualizer = appDirectory
        .resolve("Contents/app/resources/playback/bass/$platform")
        .resolve(desktopLibraryName("naviamp_visualizer_metal", platform))
    if (visualizer.isFile) visualizer.setExecutable(true, false)
}

fun patchMacAppBundleVersion(appDirectory: File) {
    val platform = desktopNativePlatform.get()
    if (!platform.startsWith("macos-")) return
    val infoPlist = appDirectory.resolve("Contents/Info.plist")
    if (!infoPlist.isFile) return
    infoPlist.writeText(
        infoPlist.readText()
            .replacePlistStringValue("CFBundleShortVersionString", naviampNativePackageVersion)
            .replacePlistStringValue("CFBundleVersion", naviampVersionCode.toString()),
    )
    refreshMacAppBundleModificationTime(appDirectory)
}

fun sealMacAppBundle(appDirectory: File) {
    val platform = desktopNativePlatform.get()
    if (!platform.startsWith("macos-")) return
    project.exec {
        commandLine(
            "/usr/bin/codesign",
            "--force",
            "--sign",
            "-",
            "--options",
            "runtime",
            "--entitlements",
            rootProject.file("apps/desktop/packaging/macos-entitlements.plist").absolutePath,
            appDirectory.absolutePath,
        )
    }.assertNormalExitValue()
}

fun verifyMacAppBundleSeal(appDirectory: File) {
    project.exec {
        commandLine(
            "/usr/bin/codesign",
            "--verify",
            "--deep",
            "--strict",
            "--verbose=2",
            appDirectory.absolutePath,
        )
    }.assertNormalExitValue()
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

fun windowsMainWxsWithOptionalLaunch(defaultMainWxs: String): String {
    val uiReference = "<UIRef Id=\"JpUI\"/>"
    check(defaultMainWxs.contains(uiReference)) {
        "The active JDK's Windows installer template no longer contains the expected JpUI reference."
    }
    return defaultMainWxs.replace(
        uiReference,
        """
        <Property Id="WIXUI_EXITDIALOGOPTIONALCHECKBOXTEXT" Value="Launch Naviamp" />
        <Property Id="WIXUI_EXITDIALOGOPTIONALCHECKBOX" Value="1" />
        <Property Id="WixShellExecTarget" Value="[INSTALLDIR]Naviamp.exe" />
        <CustomAction
          Id="LaunchNaviamp"
          BinaryKey="WixCA"
          DllEntry="WixShellExec"
          Execute="immediate"
          Impersonate="yes" />

        <UIRef Id="JpUI"/>
        <UI>
          <Publish
            Dialog="ExitDialog"
            Control="Finish"
            Event="DoAction"
            Value="LaunchNaviamp">WIXUI_EXITDIALOGOPTIONALCHECKBOX = 1 AND NOT Installed</Publish>
        </UI>
        """.trimIndent(),
    )
}

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

fun windowsDistributionPackageVersion(version: String, versionCode: Int): String {
    val parts = numericDistributionPackageVersion(version).substringBefore('-').substringBefore('+').split('.')
    require(parts.size == 3) { "VERSION must be major.minor.patch for Windows packaging, got: $version" }
    val major = parts[0].toInt()
    val minor = parts[1].toInt()
    parts[2].toInt()
    require(major in 0..255 && minor in 0..255) {
        "Windows installer major/minor must be between 0 and 255, got: $version"
    }
    require(versionCode in 1..65_535) {
        "VERSION_CODE must be between 1 and 65535 for Windows installers, got: $versionCode"
    }
    return "$major.$minor.$versionCode"
}

fun linuxDistributionPackageVersion(version: String): String =
    numericDistributionPackageVersion(version).substringBefore('+').replace('-', '~')

fun numericDistributionPackageVersion(version: String): String = version.removePrefix("v")
