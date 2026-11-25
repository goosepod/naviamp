package app.naviamp.desktop

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest

class DesktopAudioByteStoreTest {
    @Test
    fun writesAtomicallyAndDeletesThroughTheSharedByteStoreContract() = runTest {
        val directory = Files.createTempDirectory("naviamp-audio-byte-store")
        val store = DesktopAudioByteStore(directory)

        val fileName = "0123456789abcdef0123456789abcdef.audio"
        val temporaryFileName = "0123456789abcdef0123456789abcdef.part"
        val stored = store.writeAudioBytes(temporaryFileName, "failed") { writer ->
            val bytes = byteArrayOf(1, 2, 3)
            writer.write(bytes, bytes.size)
            fileName
        }

        assertEquals(3L, stored.sizeBytes)
        assertEquals(store.audioFilePath(fileName), stored.filePath)
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(directory.resolve(fileName)))
        assertFalse(directory.resolve(temporaryFileName).exists())

        store.deleteAudioBytes(stored.filePath)
        assertFalse(directory.resolve(fileName).exists())
        Files.delete(directory)
    }

    @Test
    fun failedWritesRemovePartialBytes() = runTest {
        val directory = Files.createTempDirectory("naviamp-audio-byte-store-failure")
        val store = DesktopAudioByteStore(directory)

        assertFailsWith<IllegalStateException> {
            store.writeAudioBytes("track.part", "failed") { writer ->
                writer.write(byteArrayOf(1), 1)
                throw IllegalStateException("validation failed")
            }
        }

        assertFalse(directory.resolve("track.bin").exists())
        assertFalse(directory.resolve("track.part").exists())
        Files.delete(directory)
    }

    @Test
    fun renameStaysInsideTheStoreDirectory() = runTest {
        val directory = Files.createTempDirectory("naviamp-audio-byte-store-escape")
        val store = DesktopAudioByteStore(directory)

        assertFailsWith<IllegalArgumentException> {
            store.writeAudioBytes("track.part", "failed") { writer ->
                writer.write(byteArrayOf(1), 1)
                "../escaped.mp3"
            }
        }

        assertFalse(directory.resolveSibling("escaped.mp3").exists())
        assertFalse(directory.resolve("track.part").exists())
        Files.delete(directory)
    }
}
