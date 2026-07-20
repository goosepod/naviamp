package app.naviamp.android

import app.naviamp.domain.cache.AudioByteWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AndroidAudioFileServicesTest {
    @Test
    fun audioByteStorePublishesCompletedFileAndRemovesTemporaryFile() = runTest {
        val directory = Files.createTempDirectory("naviamp-audio-store")
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            val stored = AndroidAudioByteStore(directory.toFile()).writeAudioBytes(
                fileName = "track.bin",
                errorMessage = "failed",
            ) { writer ->
                writer.write(bytes, bytes.size)
                true
            }

            assertEquals(directory.resolve("track.bin").toString(), stored.filePath)
            assertEquals(bytes.size.toLong(), stored.sizeBytes)
            assertContentEquals(bytes, directory.resolve("track.bin").toFile().readBytes())
            assertFalse(directory.resolve("track.bin.tmp").toFile().exists())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun failedWriteLeavesNeitherTargetNorTemporaryFile() = runTest {
        val directory = Files.createTempDirectory("naviamp-audio-store")
        try {
            assertFailsWith<IllegalStateException> {
                AndroidAudioByteStore(directory.toFile()).writeAudioBytes(
                    fileName = "track.bin",
                    errorMessage = "failed",
                ) { writer: AudioByteWriter ->
                    writer.write(byteArrayOf(1, 2), 2)
                    false
                }
            }

            assertFalse(directory.resolve("track.bin").toFile().exists())
            assertFalse(directory.resolve("track.bin.tmp").toFile().exists())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun mutableStoreWritesNewFilesToUpdatedDirectory() = runTest {
        val firstDirectory = Files.createTempDirectory("naviamp-audio-first")
        val secondDirectory = Files.createTempDirectory("naviamp-audio-second")
        try {
            val store = AndroidMutableAudioByteStore(firstDirectory.toFile())
            store.updateDirectory(secondDirectory.toFile())
            store.writeAudioBytes("track.bin", "failed") { writer ->
                writer.write(byteArrayOf(7), 1)
                true
            }

            assertFalse(firstDirectory.resolve("track.bin").toFile().exists())
            assertTrue(secondDirectory.resolve("track.bin").toFile().exists())
        } finally {
            firstDirectory.toFile().deleteRecursively()
            secondDirectory.toFile().deleteRecursively()
        }
    }
}
