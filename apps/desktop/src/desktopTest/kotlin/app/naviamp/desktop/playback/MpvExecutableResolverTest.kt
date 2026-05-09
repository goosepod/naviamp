package app.naviamp.desktop.playback

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MpvExecutableResolverTest {
    @Test
    fun resolvePrefersExplicitSystemPropertyPath() {
        val bundledRoot = createTempDirectory().toFile()
        val bundled = bundledRoot.executable("playback/mpv/macos-arm64/mpv")
        val override = createTempDirectory().toFile().executable("custom-mpv")

        val resolved = MpvExecutableResolver(
            platform = MpvPlatform(os = "macos", arch = "arm64"),
            systemPropertyPath = override.absolutePath,
            environmentPath = null,
            pathEnvironment = null,
            searchRoots = listOf(bundledRoot),
            installedExecutableCandidates = emptyList(),
        ).resolve()

        assertEquals(override.absoluteFile, resolved)
        assertEquals(true, bundled.exists())
    }

    @Test
    fun resolveFindsBundledPlatformExecutable() {
        val root = createTempDirectory().toFile()
        val bundled = root.executable("playback/mpv/linux-x64/mpv")

        val resolved = MpvExecutableResolver(
            platform = MpvPlatform(os = "linux", arch = "x64"),
            systemPropertyPath = null,
            environmentPath = null,
            pathEnvironment = null,
            searchRoots = listOf(root),
            installedExecutableCandidates = emptyList(),
        ).resolve()

        assertEquals(bundled.absoluteFile, resolved)
    }

    @Test
    fun resolveFallsBackToPath() {
        val pathRoot = createTempDirectory().toFile()
        val pathMpv = pathRoot.executable("mpv")

        val resolved = MpvExecutableResolver(
            platform = MpvPlatform(os = "macos", arch = "arm64"),
            systemPropertyPath = null,
            environmentPath = null,
            pathEnvironment = pathRoot.absolutePath,
            searchRoots = emptyList(),
            installedExecutableCandidates = emptyList(),
        ).resolve()

        assertEquals(pathMpv.absoluteFile, resolved)
    }

    @Test
    fun resolveFindsInstalledWindowsExecutableCandidate() {
        val installedMpv = createTempDirectory().toFile().executable("MPV Player/mpv.exe")

        val resolved = MpvExecutableResolver(
            platform = MpvPlatform(os = "windows", arch = "x64"),
            systemPropertyPath = null,
            environmentPath = null,
            pathEnvironment = null,
            searchRoots = emptyList(),
            installedExecutableCandidates = listOf(installedMpv),
        ).resolve()

        assertEquals(installedMpv.absoluteFile, resolved)
    }

    @Test
    fun resolveReturnsNullWhenNoExecutableExists() {
        val resolved = MpvExecutableResolver(
            platform = MpvPlatform(os = "windows", arch = "x64"),
            systemPropertyPath = null,
            environmentPath = null,
            pathEnvironment = null,
            searchRoots = listOf(createTempDirectory().toFile()),
            installedExecutableCandidates = emptyList(),
        ).resolve()

        assertNull(resolved)
    }

    private fun java.io.File.executable(relativePath: String): java.io.File {
        val file = resolve(relativePath)
        file.parentFile.mkdirs()
        file.writeText("")
        file.setExecutable(true)
        return file
    }
}
