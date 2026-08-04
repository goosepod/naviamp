package app.naviamp.desktop

import app.naviamp.domain.cache.isNaviampOwnedAudioFileName
import java.nio.file.Files
import java.nio.file.Path

/** Deletes only an exact path supplied by a shared ownership record; it never enumerates directories. */
class DesktopKnownFileDeleter {
    fun deleteOwnedAudioFile(directory: Path, path: Path): Boolean {
        val root = directory.toAbsolutePath().normalize()
        val target = path.toAbsolutePath().normalize()
        if (target.parent != root || !isNaviampOwnedAudioFileName(target.fileName.toString())) return false
        if (!Files.exists(target)) return true
        return Files.isRegularFile(target) && Files.deleteIfExists(target)
    }

    fun deleteFile(path: Path): Boolean {
        if (!Files.exists(path)) return true
        return Files.isRegularFile(path) && Files.deleteIfExists(path)
    }
}
