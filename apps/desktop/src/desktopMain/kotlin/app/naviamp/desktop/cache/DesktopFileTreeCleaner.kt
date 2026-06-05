package app.naviamp.desktop

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class DesktopFileTreeCleaner {
    fun clearDirectoryContents(directory: Path) {
        runCatching {
            if (!directory.exists()) return@runCatching
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    if (path != directory) {
                        Files.deleteIfExists(path)
                    }
                }
            }
        }
    }
}
