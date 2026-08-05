import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val desktopNativePlatform = providers.gradleProperty("naviamp.bass.platform")
    .orElse(providers.provider(::desktopNativePlatformId))
val desktopBassVendorDir = desktopNativePlatform.map { platform ->
    layout.projectDirectory.dir("vendor/bass/$platform")
}
val generatedDesktopNativeResources = layout.buildDirectory.dir("generated/desktopNativeResources")
val generatedDesktopNativeAppResources = layout.buildDirectory.dir("generated/desktopNativeAppResources")
val desktopCmakeExecutable = providers.environmentVariable("CMAKE_EXE")
    .orElse(providers.environmentVariable("LOCALAPPDATA").map { "$it/Android/Sdk/cmake/3.22.1/bin/cmake.exe" })
    .orElse(providers.provider { "cmake" })

val copyDesktopBassResources by tasks.registering(Copy::class) {
    from(desktopBassVendorDir)
    into(generatedDesktopNativeResources.zip(desktopNativePlatform) { resources, platform ->
        resources.dir("playback/bass/$platform")
    })
    onlyIf { desktopBassVendorDir.get().asFile.isDirectory }
}

val copyDesktopBassAppResources by tasks.registering(Copy::class) {
    from(desktopBassVendorDir)
    into(generatedDesktopNativeAppResources.zip(desktopNativePlatform) { resources, platform ->
        resources.dir("$platform/playback/bass/$platform")
    })
    onlyIf { desktopBassVendorDir.get().asFile.isDirectory }
}

fun desktopNativeBuild(
    name: String,
    sourceDirectory: String,
    libraryStem: String,
    enabled: (String) -> Boolean = { true },
): Triple<TaskProvider<Exec>, TaskProvider<Copy>, TaskProvider<Copy>> {
    val buildDirectory = desktopNativePlatform.map { platform ->
        layout.buildDirectory.dir("generated/${name}Build/$platform").get()
    }
    val outputFile = buildDirectory.zip(desktopNativePlatform) { directory, platform ->
        directory.file(desktopLibraryName(libraryStem, platform)).asFile
    }
    val configure = tasks.register<Exec>("configure$name") {
        val nativeProjectDir = rootProject.layout.projectDirectory.dir(sourceDirectory)
        onlyIf { enabled(desktopNativePlatform.get()) && desktopBassVendorDir.get().asFile.isDirectory }
        val cmakeArgs = mutableListOf(
            desktopCmakeExecutable.get(),
            "-S",
            nativeProjectDir.asFile.absolutePath,
            "-B",
            buildDirectory.get().asFile.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${buildDirectory.get().asFile.absolutePath}",
            "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${buildDirectory.get().asFile.absolutePath}",
            "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${buildDirectory.get().asFile.absolutePath}",
            "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=${buildDirectory.get().asFile.absolutePath}",
        )
        if (name == "DesktopBassJni") {
            cmakeArgs += "-DBASS_LIBRARY_DIR=${desktopBassVendorDir.get().asFile.absolutePath}"
        }
        if (desktopNativePlatform.get().startsWith("macos-")) {
            cmakeArgs += "-DCMAKE_OSX_ARCHITECTURES=${desktopCmakeArchitecture(desktopNativePlatform.get())}"
        }
        commandLine(cmakeArgs)
    }
    val build = tasks.register<Exec>("build$name") {
        dependsOn(configure)
        onlyIf { enabled(desktopNativePlatform.get()) && desktopBassVendorDir.get().asFile.isDirectory }
        commandLine(
            desktopCmakeExecutable.get(),
            "--build",
            buildDirectory.get().asFile.absolutePath,
            "--config",
            "Release",
        )
    }
    val copyResources = tasks.register<Copy>("copy${name}Resources") {
        dependsOn(build)
        from(outputFile)
        into(generatedDesktopNativeResources.zip(desktopNativePlatform) { resources, platform ->
            resources.dir("playback/bass/$platform")
        })
        if (libraryStem == "naviamp_visualizer_metal") {
            filePermissions { unix("755") }
        }
        onlyIf { outputFile.get().isFile }
    }
    val copyAppResources = tasks.register<Copy>("copy${name}AppResources") {
        dependsOn(build)
        from(outputFile)
        into(generatedDesktopNativeAppResources.zip(desktopNativePlatform) { resources, platform ->
            resources.dir("$platform/playback/bass/$platform")
        })
        if (libraryStem == "naviamp_visualizer_metal") {
            filePermissions { unix("755") }
        }
        onlyIf { outputFile.get().isFile }
    }
    return Triple(build, copyResources, copyAppResources)
}

val (_, copyDesktopBassJniResources, copyDesktopBassJniAppResources) = desktopNativeBuild(
    name = "DesktopBassJni",
    sourceDirectory = "native/bass-jni",
    libraryStem = "naviamp_bass",
)
val (_, copyDesktopVisualizerMetalResources, copyDesktopVisualizerMetalAppResources) = desktopNativeBuild(
    name = "DesktopVisualizerMetal",
    sourceDirectory = "native/visualizer-metal",
    libraryStem = "naviamp_visualizer_metal",
    enabled = { platform -> platform.startsWith("macos-") },
)
val (_, copyDesktopVisualizerOpenGlResources, copyDesktopVisualizerOpenGlAppResources) = desktopNativeBuild(
    name = "DesktopVisualizerOpenGl",
    sourceDirectory = "native/visualizer-opengl",
    libraryStem = "naviamp_visualizer_opengl",
    enabled = { platform -> platform.startsWith("windows-") },
)

val prepareDesktopNativeResources by tasks.registering {
    group = "build"
    description = "Builds and stages Desktop native playback resources for the runtime classpath."
    dependsOn(
        copyDesktopBassResources,
        copyDesktopBassJniResources,
        copyDesktopVisualizerMetalResources,
        copyDesktopVisualizerOpenGlResources,
    )
}

val prepareDesktopNativeAppResources by tasks.registering {
    group = "build"
    description = "Builds and stages Desktop native playback resources for Compose app packaging."
    dependsOn(
        copyDesktopBassAppResources,
        copyDesktopBassJniAppResources,
        copyDesktopVisualizerMetalAppResources,
        copyDesktopVisualizerOpenGlAppResources,
    )
}

tasks.register("verifyDesktopNativeInputs") {
    group = "verification"
    description = "Verifies vendored BASS libraries and native source inputs for every supported Desktop target."

    doLast {
        val expectedStemsByPlatform = mapOf(
            "macos-arm64" to setOf(
                "bass", "bass_mpc", "bassape", "bassdsd", "bassflac", "basshls",
                "bassmidi", "bassmix", "bassopus", "basswebm", "basswv",
            ),
            "windows-x64" to setOf(
                "bass", "bass_aac", "bass_mpc", "bass_ssl", "bassalac", "bassape",
                "bassdsd", "bassflac", "basshls", "bassmidi", "bassmix", "bassopus",
                "basswebm", "basswma",
            ),
            "linux-x64" to setOf(
                "bass", "bass_aac", "bass_ac3", "bass_mpc", "bass_spx", "bass_tta",
                "bassalac", "bassape", "bassdsd", "bassflac", "basshls", "bassmidi",
                "bassmix", "bassopus", "basswebm", "basswv",
            ),
        )
        expectedStemsByPlatform.forEach { (platform, expectedStems) ->
            val vendorDirectory = layout.projectDirectory.dir("vendor/bass/$platform").asFile
            val requiredLibraries = expectedStems
                .map { desktopLibraryName(it, platform) }
            val missing = requiredLibraries.filterNot { vendorDirectory.resolve(it).isFile }
            check(missing.isEmpty()) {
                "$platform is missing required vendored BASS libraries: ${missing.joinToString()}"
            }
            val actualLibraries = vendorDirectory.listFiles()
                .orEmpty()
                .filter(File::isFile)
                .map(File::getName)
                .toSet()
            val unexpected = actualLibraries - requiredLibraries.toSet()
            check(unexpected.isEmpty()) {
                "$platform contains unexpected vendored BASS libraries: ${unexpected.joinToString()}"
            }
        }

        val nativeInputs = listOf(
            rootProject.file("native/bass-jni/CMakeLists.txt"),
            rootProject.file("native/bass-jni/src/naviamp_bass_jni.cpp"),
            rootProject.file("native/visualizer-metal/CMakeLists.txt"),
            rootProject.file("native/visualizer-metal/src/naviamp_visualizer_metal.mm"),
            rootProject.file("native/visualizer-opengl/CMakeLists.txt"),
            rootProject.file("native/visualizer-opengl/src/naviamp_visualizer_opengl.cpp"),
        )
        check(nativeInputs.all { it.isFile && it.length() > 0L }) {
            "Desktop native build inputs are missing: ${nativeInputs.filterNot { it.isFile && it.length() > 0L }.joinToString()}"
        }
    }
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        val desktopMain by getting {
            resources.srcDir(generatedDesktopNativeResources)
            dependencies {
                api(project(":core:app"))
                api(project(":core:domain"))
                api(project(":core:presentation"))
                api(project(":core:storage"))
                api(project(":providers:navidrome"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

tasks.matching { it.name == "desktopProcessResources" || it.name == "processDesktopMainResources" }
    .configureEach { dependsOn(prepareDesktopNativeResources) }

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

fun desktopCmakeArchitecture(platform: String): String = when {
    platform.endsWith("-arm64") -> "arm64"
    platform.endsWith("-x64") -> "x86_64"
    else -> System.getProperty("os.arch")
}
