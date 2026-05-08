package app.naviamp.desktop.playback

import java.io.File

class MpvExecutableResolver(
    private val platform: MpvPlatform = MpvPlatform.current(),
    private val systemPropertyPath: String? = System.getProperty("naviamp.mpv.path"),
    private val environmentPath: String? = System.getenv("NAVIAMP_MPV_PATH"),
    private val pathEnvironment: String? = System.getenv("PATH"),
    private val searchRoots: List<File> = defaultSearchRoots(),
) {
    fun resolve(): File? =
        sequence {
            systemPropertyPath?.let { yield(File(it)) }
            environmentPath?.let { yield(File(it)) }
            searchRoots.forEach { root ->
                bundledRelativePaths().forEach { relativePath ->
                    yield(File(root, relativePath))
                }
            }
            pathEnvironment
                ?.split(File.pathSeparator)
                ?.filter { it.isNotBlank() }
                ?.forEach { yield(File(it, platform.executableName)) }
        }
            .map { it.absoluteFile }
            .firstOrNull { it.isFile && it.canExecute() }

    private fun bundledRelativePaths(): List<String> =
        listOf(
            "playback/mpv/${platform.id}/${platform.executableName}",
            "mpv/${platform.id}/${platform.executableName}",
            "Contents/Resources/playback/mpv/${platform.id}/${platform.executableName}",
            "../Resources/playback/mpv/${platform.id}/${platform.executableName}",
            "../app/playback/mpv/${platform.id}/${platform.executableName}",
        )

    companion object {
        private fun defaultSearchRoots(): List<File> {
            val codeSource = MpvExecutableResolver::class.java.protectionDomain.codeSource?.location
                ?.toURI()
                ?.let(::File)
            val codeSourceRoot = codeSource?.let {
                if (it.isFile) it.parentFile else it
            }

            return buildList {
                codeSourceRoot?.ancestors(limit = 6)?.let(::addAll)
                add(File(System.getProperty("user.dir")))
            }.distinctBy { it.absolutePath }
        }
    }
}

data class MpvPlatform(
    val os: String,
    val arch: String,
) {
    val id: String = "$os-$arch"
    val executableName: String = if (os == "windows") "mpv.exe" else "mpv"

    companion object {
        fun current(): MpvPlatform =
            MpvPlatform(
                os = currentOs(),
                arch = currentArch(),
            )

        private fun currentOs(): String {
            val osName = System.getProperty("os.name").lowercase()
            return when {
                osName.contains("mac") || osName.contains("darwin") -> "macos"
                osName.contains("win") -> "windows"
                osName.contains("linux") -> "linux"
                else -> osName.filter { it.isLetterOrDigit() }.ifBlank { "unknown" }
            }
        }

        private fun currentArch(): String {
            val arch = System.getProperty("os.arch").lowercase()
            return when (arch) {
                "aarch64", "arm64" -> "arm64"
                "x86_64", "amd64" -> "x64"
                else -> arch.filter { it.isLetterOrDigit() }.ifBlank { "unknown" }
            }
        }
    }
}

private fun File.ancestors(limit: Int): List<File> =
    generateSequence(this) { it.parentFile }
        .take(limit)
        .toList()
