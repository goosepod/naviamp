@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.storage

import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosAudioStorageTest {
    @Test
    fun storedPathIsRebasedWhenIosRelocatesTheApplicationContainer() {
        val currentDirectory = "/current/container/Library/Application Support/Naviamp/downloads"
        val oldPath = "/old/container/Library/Application Support/Naviamp/downloads/track.flac"

        assertEquals(
            "$currentDirectory/track.flac",
            IosAudioFileSystem.resolveStoredFilePath(currentDirectory, oldPath),
        )
    }

    @Test
    fun successfulWriteAtomicallyPublishesTheFinalFile() = withTestDirectory { directory ->
        val store = IosAudioByteStore(directory)
        val stored = runBlocking {
            store.writeAudioBytes("track.mp3", "write failed") { writer ->
                writer.write(byteArrayOf(1, 2, 3), 3)
                writer.write(byteArrayOf(4, 5), 2)
                true
            }
        }

        assertEquals("$directory/track.mp3", stored.filePath)
        assertEquals(5L, stored.sizeBytes)
        assertTrue(IosAudioFileSystem.isRegularFile(stored.filePath))
        assertFalse(IosAudioFileSystem.isRegularFile("${stored.filePath}.tmp"))
    }

    @Test
    fun failedWriteRemovesTheTemporaryFileAndPublishesNothing() = withTestDirectory { directory ->
        val store = IosAudioByteStore(directory)

        assertFailsWith<IllegalStateException> {
            runBlocking {
                store.writeAudioBytes("track.flac", "write failed") { writer ->
                    writer.write(byteArrayOf(1, 2, 3), 3)
                    false
                }
            }
        }

        assertFalse(IosAudioFileSystem.isRegularFile("$directory/track.flac"))
        assertFalse(IosAudioFileSystem.isRegularFile("$directory/track.flac.tmp"))
    }

    @Test
    fun exactDirectoryDeletionCannotRemoveAnUnrelatedSibling() = withTestDirectory { directory ->
        val siblingDirectory = "${directory}-sibling"
        val siblingStore = IosAudioByteStore(siblingDirectory)
        val siblingFile = runBlocking {
            siblingStore.writeAudioBytes("keep.mp3", "write failed") { writer ->
                writer.write(byteArrayOf(1), 1)
                true
            }
        }
        try {
            assertFalse(IosAudioFileSystem.deleteKnownRegularFile(directory, siblingFile.filePath))
            assertTrue(IosAudioFileSystem.isRegularFile(siblingFile.filePath))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(siblingDirectory, null)
        }
    }

    @Test
    fun deletionRequiresANaviampOwnedFileNameEvenInsideTheAudioDirectory() = withTestDirectory { directory ->
        val store = IosAudioByteStore(directory)
        val unrelated = runBlocking {
            store.writeAudioBytes("personal-recording.flac", "write failed") { writer ->
                writer.write(byteArrayOf(1), 1)
                true
            }
        }
        val owned = runBlocking {
            store.writeAudioBytes("0123456789abcdef0123456789abcdef.flac", "write failed") { writer ->
                writer.write(byteArrayOf(2), 1)
                true
            }
        }

        assertFalse(IosAudioFileSystem.deleteKnownRegularFile(directory, unrelated.filePath))
        assertTrue(IosAudioFileSystem.isRegularFile(unrelated.filePath))
        assertTrue(IosAudioFileSystem.deleteKnownRegularFile(directory, owned.filePath))
        assertFalse(IosAudioFileSystem.isRegularFile(owned.filePath))
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
