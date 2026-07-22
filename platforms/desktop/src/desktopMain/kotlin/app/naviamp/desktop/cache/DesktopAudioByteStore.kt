package app.naviamp.desktop

import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.StoredAudioBytes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Atomic Desktop filesystem implementation of Core's audio-byte effect. */
class DesktopAudioByteStore(
    private val directory: Path,
) : AudioByteStore {
    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes {
        Files.createDirectories(directory)
        val target = directory.resolve(fileName)
        val temporary = directory.resolve("${target.fileName}.tmp")
        return try {
            Files.newOutputStream(temporary).use { output ->
                val writer = AudioByteWriter { bytes, count -> output.write(bytes, 0, count) }
                if (!writeBytes(writer)) throw IllegalStateException(errorMessage)
            }
            moveAtomicallyWhenSupported(temporary, target)
            StoredAudioBytes(
                filePath = target.toAbsolutePath().toString(),
                sizeBytes = Files.size(target),
            )
        } catch (failure: Throwable) {
            Files.deleteIfExists(temporary)
            throw failure
        }
    }

    override fun deleteAudioBytes(filePath: String) {
        Files.deleteIfExists(Path.of(filePath))
    }
}

/** Keeps Core's byte-store identity stable while the user changes a native storage directory. */
class DesktopMutableAudioByteStore(
    initialDirectory: Path,
) : AudioByteStore {
    @Volatile
    private var store = DesktopAudioByteStore(initialDirectory)

    fun updateDirectory(directory: Path) {
        store = DesktopAudioByteStore(directory)
    }

    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes = store.writeAudioBytes(fileName, errorMessage, writeBytes)

    override fun deleteAudioBytes(filePath: String) {
        store.deleteAudioBytes(filePath)
    }
}

private fun moveAtomicallyWhenSupported(source: Path, target: Path) {
    runCatching {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }.getOrElse {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
