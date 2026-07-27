package app.naviamp.android

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAudioByteStoreSafetyTest {
    @Test
    fun exactDeletionLeavesUnrelatedAndNestedFilesUntouched() {
        val root = Files.createTempDirectory("naviamp-android-audio-delete").toFile()
        val tracked = root.resolve("tracked-download.bin").apply { writeBytes(byteArrayOf(1)) }
        val trackedConverted = root.resolve("tracked-download.opus").apply { writeBytes(byteArrayOf(1)) }
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
}
