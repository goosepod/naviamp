package app.naviamp.android

import app.naviamp.domain.cache.AudioByteWriter
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAudioByteStoreSafetyTest {
    @Test
    fun exactDeletionLeavesUnrelatedAndNestedFilesUntouched() {
        val root = Files.createTempDirectory("naviamp-android-audio-delete").toFile()
        val tracked = root.resolve("0123456789abcdef0123456789abcdef.audio").apply { writeBytes(byteArrayOf(1)) }
        val trackedConverted = root.resolve("fedcba9876543210fedcba9876543210.opus").apply { writeBytes(byteArrayOf(1)) }
        val unrelated = root.resolve("music-library-track.flac").apply { writeBytes(byteArrayOf(2)) }
        val unrelatedConverted = root.resolve("personal-conversion.opus").apply { writeBytes(byteArrayOf(2)) }
        val databasePathThatIsNotAFile = root.resolve("not-a-file").apply { mkdirs() }
        val nested = root.resolve("artist/album/track.flac").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(byteArrayOf(3))
        }

        AndroidAudioByteStore(root).deleteAudioBytes(tracked.absolutePath)
        AndroidAudioByteStore(root).deleteAudioBytes(trackedConverted.absolutePath)
        AndroidAudioByteStore(root).deleteAudioBytes(databasePathThatIsNotAFile.absolutePath)

        assertFalse(tracked.exists())
        assertFalse(trackedConverted.exists())
        assertTrue(unrelated.exists())
        assertTrue(unrelatedConverted.exists())
        assertTrue(databasePathThatIsNotAFile.isDirectory)
        assertTrue(nested.exists())
        root.deleteRecursively()
    }

    @Test
    fun writesRenameTheValidatedTemporaryFileAndFailuresLeaveNothing(): Unit = runBlocking {
        val root = Files.createTempDirectory("naviamp-android-audio-write").toFile()
        val store = AndroidAudioByteStore(root)
        val name = "0123456789abcdef0123456789abcdef.flac"

        val stored = store.writeAudioBytes("0123456789abcdef0123456789abcdef.part", "failed") { writer: AudioByteWriter ->
            writer.write(byteArrayOf(1, 2, 3), 3)
            name
        }
        assertEquals(store.audioFilePath(name), stored.filePath)
        assertContentEquals(byteArrayOf(1, 2, 3), root.resolve(name).readBytes())
        assertFalse(root.resolve("0123456789abcdef0123456789abcdef.part").exists())

        assertFailsWith<IllegalStateException> {
            store.writeAudioBytes("fedcba9876543210fedcba9876543210.part", "failed") { writer ->
                writer.write(byteArrayOf(4), 1)
                throw IllegalStateException("validation failed")
            }
        }
        assertEquals(listOf(name), root.list()!!.toList())
        root.deleteRecursively()
    }
}
