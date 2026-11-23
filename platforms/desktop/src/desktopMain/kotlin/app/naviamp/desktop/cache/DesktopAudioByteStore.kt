package app.naviamp.desktop

import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.StoredAudioBytes
import app.naviamp.domain.cache.isNaviampOwnedAudioFileName
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Atomic Desktop filesystem implementation of Core's audio-byte effect. */
class DesktopAudioByteStore(
    private val directory: Path,
) : AudioByteStore {
    override fun audioFilePath(fileName: String): String =
        directory.resolve(fileName).toAbsolutePath().toString()

    override suspend fun writeAudioBytes(
        temporaryFileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> String,
    ): StoredAudioBytes {
        Files.createDirectories(directory)
        val temporary = directory.resolve(temporaryFileName)
        return try {
            val fileName = FileChannel.open(
                temporary,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ).use { channel ->
                val output = Channels.newOutputStream(channel)
                val writer = AudioByteWriter { bytes, count -> output.write(bytes, 0, count) }
                writeBytes(writer).also {
                    output.flush()
                    channel.force(true)
                }
            }
            val target = directory.resolve(fileName)
            require(target.parent == temporary.parent) { errorMessage }
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
        runCatching {
            val root = directory.toAbsolutePath().normalize()
            val path = Path.of(filePath).toAbsolutePath().normalize()
            path.takeIf {
                it.parent == root && isNaviampOwnedAudioFileName(it.fileName.toString()) && Files.isRegularFile(it)
            }?.let(Files::deleteIfExists)
        }
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

    override fun audioFilePath(fileName: String): String = store.audioFilePath(fileName)

    override suspend fun writeAudioBytes(
        temporaryFileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> String,
    ): StoredAudioBytes = store.writeAudioBytes(temporaryFileName, errorMessage, writeBytes)

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
