package app.naviamp.desktop

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopKnownFileDeleterTest {
    @Test
    fun ownedAudioDeletionFailsClosedForUnverifiedPaths() {
        val root = Files.createTempDirectory("naviamp-known-delete")
        val sibling = Files.createTempDirectory("naviamp-known-delete-sibling")
        val owned = Files.write(root.resolve("0123456789abcdef0123456789abcdef.flac"), byteArrayOf(1))
        val arbitrary = Files.write(root.resolve("personal.flac"), byteArrayOf(2))
        val outside = Files.write(sibling.resolve("fedcba9876543210fedcba9876543210.flac"), byteArrayOf(3))
        val deleter = DesktopKnownFileDeleter()
        try {
            assertFalse(deleter.deleteOwnedAudioFile(root, arbitrary))
            assertFalse(deleter.deleteOwnedAudioFile(root, outside))
            assertTrue(arbitrary.exists())
            assertTrue(outside.exists())
            assertTrue(deleter.deleteOwnedAudioFile(root, owned))
            assertFalse(owned.exists())
        } finally {
            Files.deleteIfExists(arbitrary)
            Files.deleteIfExists(outside)
            Files.deleteIfExists(root)
            Files.deleteIfExists(sibling)
        }
    }
}
