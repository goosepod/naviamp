package app.naviamp.desktop

import java.nio.file.Files
import java.nio.file.Path

/** Deletes only an exact path supplied by a shared ownership record; it never enumerates directories. */
class DesktopKnownFileDeleter {
    fun deleteFile(path: Path): Boolean {
        if (!Files.exists(path)) return true
        return Files.isRegularFile(path) && Files.deleteIfExists(path)
    }
}
