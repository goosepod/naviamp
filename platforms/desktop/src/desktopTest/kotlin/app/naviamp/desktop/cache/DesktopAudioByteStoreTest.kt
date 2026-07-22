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

        val stored = store.writeAudioBytes("track.bin", "failed") { writer ->
            val bytes = byteArrayOf(1, 2, 3)
            writer.write(bytes, bytes.size)
            true
        }

        assertEquals(3L, stored.sizeBytes)
        assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(directory.resolve("track.bin")))
        assertFalse(directory.resolve("track.bin.tmp").exists())

        store.deleteAudioBytes(stored.filePath)
        assertFalse(directory.resolve("track.bin").exists())
        Files.delete(directory)
    }

    @Test
    fun failedWritesRemovePartialBytes() = runTest {
        val directory = Files.createTempDirectory("naviamp-audio-byte-store-failure")
        val store = DesktopAudioByteStore(directory)

        assertFailsWith<IllegalStateException> {
            store.writeAudioBytes("track.bin", "failed") { writer ->
                writer.write(byteArrayOf(1), 1)
                false
            }
        }

        assertFalse(directory.resolve("track.bin").exists())
        assertFalse(directory.resolve("track.bin.tmp").exists())
        Files.delete(directory)
    }
}
