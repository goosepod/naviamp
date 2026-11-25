@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.storage

import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppleAudioStorageTest {
    @Test
    fun storedPathIsRebasedWhenIosRelocatesTheApplicationContainer() {
        val currentDirectory = "/current/container/Library/Application Support/Naviamp/downloads"
        val oldPath = "/old/container/Library/Application Support/Naviamp/downloads/track.flac"

        assertEquals(
            "$currentDirectory/track.flac",
            AppleAudioFileSystem.resolveStoredFilePath(currentDirectory, oldPath),
        )
    }

    @Test
    fun successfulWriteAtomicallyPublishesTheFinalFile() = withTestDirectory { directory ->
        val store = AppleAudioByteStore(directory)
        val stored = runBlocking {
            store.writeAudioBytes("track.part", "write failed") { writer ->
                writer.write(byteArrayOf(1, 2, 3), 3)
                writer.write(byteArrayOf(4, 5), 2)
                "track.mp3"
            }
        }

        assertEquals("$directory/track.mp3", stored.filePath)
        assertEquals(store.audioFilePath("track.mp3"), stored.filePath)
        assertEquals(5L, stored.sizeBytes)
        assertTrue(AppleAudioFileSystem.isRegularFile(stored.filePath))
        assertFalse(AppleAudioFileSystem.isRegularFile("$directory/track.part"))
    }

    @Test
    fun failedWriteRemovesTheTemporaryFileAndPublishesNothing() = withTestDirectory { directory ->
        val store = AppleAudioByteStore(directory)

        assertFailsWith<IllegalStateException> {
            runBlocking {
                store.writeAudioBytes("track.part", "write failed") { writer ->
                    writer.write(byteArrayOf(1, 2, 3), 3)
                    throw IllegalStateException("validation failed")
                }
            }
        }

        assertFalse(AppleAudioFileSystem.isRegularFile("$directory/track.flac"))
        assertFalse(AppleAudioFileSystem.isRegularFile("$directory/track.part"))
    }

    @Test
    fun exactDirectoryDeletionCannotRemoveAnUnrelatedSibling() = withTestDirectory { directory ->
        val siblingDirectory = "${directory}-sibling"
        val siblingStore = AppleAudioByteStore(siblingDirectory)
        val siblingFile = runBlocking {
            siblingStore.writeAudioBytes("keep.part", "write failed") { writer ->
                writer.write(byteArrayOf(1), 1)
                "keep.mp3"
            }
        }
        try {
            assertFalse(AppleAudioFileSystem.deleteKnownRegularFile(directory, siblingFile.filePath))
            assertTrue(AppleAudioFileSystem.isRegularFile(siblingFile.filePath))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(siblingDirectory, null)
        }
    }

    @Test
    fun deletionRequiresANaviampOwnedFileNameEvenInsideTheAudioDirectory() = withTestDirectory { directory ->
        val store = AppleAudioByteStore(directory)
        val unrelated = runBlocking {
            store.writeAudioBytes("personal-recording.part", "write failed") { writer ->
                writer.write(byteArrayOf(1), 1)
                "personal-recording.flac"
            }
        }
        val owned = runBlocking {
            store.writeAudioBytes("0123456789abcdef0123456789abcdef.part", "write failed") { writer ->
                writer.write(byteArrayOf(2), 1)
                "0123456789abcdef0123456789abcdef.flac"
            }
        }

        assertFalse(AppleAudioFileSystem.deleteKnownRegularFile(directory, unrelated.filePath))
        assertTrue(AppleAudioFileSystem.isRegularFile(unrelated.filePath))
        assertTrue(AppleAudioFileSystem.deleteKnownRegularFile(directory, owned.filePath))
        assertFalse(AppleAudioFileSystem.isRegularFile(owned.filePath))
    }
}

private inline fun withTestDirectory(block: (String) -> Unit) {
    val directory = "${NSTemporaryDirectory().trimEnd('/')}/naviamp-audio-${NSUUID.UUID().UUIDString}"
    try {
        block(directory)
    } finally {
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }
}
