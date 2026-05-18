package app.naviamp.desktop.playback.bass

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BassLibraryResolverTest {
    @Test
    fun resolvePrefersExplicitSystemPropertyDirectory() {
        val bundledRoot = createTempDirectory().toFile()
        bundledRoot.library("playback/bass/macos-arm64/libbass.dylib")
        val explicit = createTempDirectory().toFile()
        explicit.library("libbass.dylib")

        val resolved = BassLibraryResolver(
            platform = BassPlatform(os = "macos", arch = "arm64"),
            explicitDirectory = explicit.absolutePath,
            environmentDirectory = null,
            searchRoots = listOf(bundledRoot),
        ).resolve()

        assertEquals(explicit.absoluteFile, resolved)
    }

    @Test
    fun resolveFallsBackToEnvironmentDirectory() {
        val environment = createTempDirectory().toFile()
        environment.library("bass.dll")

        val resolved = BassLibraryResolver(
            platform = BassPlatform(os = "windows", arch = "x64"),
            explicitDirectory = null,
            environmentDirectory = environment.absolutePath,
            searchRoots = emptyList(),
        ).resolve()

        assertEquals(environment.absoluteFile, resolved)
    }

    @Test
    fun resolveFindsBundledResources() {
        val root = createTempDirectory().toFile()
        val bundled = root.library("playback/bass/linux-x64/libbass.so").parentFile

        val resolved = BassLibraryResolver(
            platform = BassPlatform(os = "linux", arch = "x64"),
            explicitDirectory = null,
            environmentDirectory = null,
            searchRoots = listOf(root),
        ).resolve()

        assertEquals(bundled.absoluteFile, resolved)
    }

    @Test
    fun resolveFindsPackagedComposeResources() {
        val root = createTempDirectory().toFile()
        val bundled = root.library("resources/playback/bass/macos-arm64/libbass.dylib").parentFile

        val resolved = BassLibraryResolver(
            platform = BassPlatform(os = "macos", arch = "arm64"),
            explicitDirectory = null,
            environmentDirectory = null,
            searchRoots = listOf(root),
        ).resolve()

        assertEquals(bundled.absoluteFile, resolved)
    }

    @Test
    fun resolveFindsMacAppBundleResources() {
        val root = createTempDirectory().toFile()
        val resources = root.library("Contents/Resources/playback/bass/macos-arm64/libbass.dylib").parentFile
        val macOs = root.resolve("Contents/MacOS").also { it.mkdirs() }

        val resolved = BassLibraryResolver(
            platform = BassPlatform(os = "macos", arch = "arm64"),
            explicitDirectory = null,
            environmentDirectory = null,
            searchRoots = listOf(macOs),
        ).resolve()

        assertEquals(resources.absoluteFile, resolved)
    }

    @Test
    fun resolveReturnsNullWhenBassIsMissing() {
        val resolved = BassLibraryResolver(
            platform = BassPlatform(os = "macos", arch = "arm64"),
            explicitDirectory = null,
            environmentDirectory = null,
            searchRoots = listOf(createTempDirectory().toFile()),
        ).resolve()

        assertNull(resolved)
    }

    private fun java.io.File.library(relativePath: String): java.io.File {
        val file = resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText("")
        return file
    }
}
