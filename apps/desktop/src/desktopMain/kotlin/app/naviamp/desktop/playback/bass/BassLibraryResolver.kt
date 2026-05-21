package app.naviamp.desktop.playback.bass

import java.io.File

class BassLibraryResolver(
    private val platform: BassPlatform = BassPlatform.current(),
    private val explicitDirectory: String? = System.getProperty("naviamp.bass.dir"),
    private val environmentDirectory: String? = System.getenv("NAVIAMP_BASS_DIR"),
    private val searchRoots: List<File> = defaultSearchRoots(),
) {
    fun resolve(): File? =
        candidateDirectories()
            .map { it.normalizedAbsoluteFile() }
            .distinctBy { it.absolutePath }
            .firstOrNull { File(it, platform.libraryName("bass")).isFile }

    fun candidateDirectories(): List<File> = buildList {
        explicitDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { add(File(it)) }
        environmentDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { add(File(it)) }
        searchRoots.forEach { root ->
            bundledRelativePaths().forEach { relativePath ->
                add(File(root, relativePath))
            }
        }
    }

    private fun bundledRelativePaths(): List<String> =
        listOf(
            "resources/playback/bass/${platform.id}",
            "playback/bass/${platform.id}",
            "bass/${platform.id}",
            "Contents/Resources/playback/bass/${platform.id}",
            "../Resources/playback/bass/${platform.id}",
            "../app/playback/bass/${platform.id}",
            "apps/desktop-slint/vendor/bass/${platform.id}",
            "vendor/bass/${platform.id}",
        )

    companion object {
        private fun defaultSearchRoots(): List<File> {
            val codeSource = BassLibraryResolver::class.java.protectionDomain.codeSource?.location
                ?.toURI()
                ?.let(::File)
            val codeSourceRoot = codeSource?.let {
                if (it.isFile) it.parentFile else it
            }

            return buildList {
                codeSourceRoot?.ancestors(limit = 8)?.let(::addAll)
                add(File(System.getProperty("user.dir")))
            }.distinctBy { it.absolutePath }
        }
    }
}

data class BassPlatform(
    val os: String,
    val arch: String,
) {
    val id: String = "$os-$arch"

    fun libraryName(stem: String): String =
        when (os) {
            "windows" -> "$stem.dll"
            "macos" -> "lib$stem.dylib"
            else -> "lib$stem.so"
        }

    companion object {
        fun current(): BassPlatform =
            BassPlatform(
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

private fun File.normalizedAbsoluteFile(): File =
    absoluteFile.toPath().normalize().toFile()
