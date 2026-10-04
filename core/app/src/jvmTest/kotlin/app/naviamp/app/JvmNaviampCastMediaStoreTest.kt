package app.naviamp.app

import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmNaviampCastMediaStoreTest {
    @Test
    fun temporaryFileSupportsRandomReadsAndIsRemovedOnRelease() = runTest {
        val directory = Files.createTempDirectory("cast-store-test-")
        try {
            val store = JvmNaviampCastMediaStore(directory)
            val file = store.write { writer ->
                writer.write(byteArrayOf(1, 2, 3), 3)
                writer.write(byteArrayOf(4, 5), 2)
                true
            }
            assertEquals(5L, file.sizeBytes)
            assertContentEquals(byteArrayOf(2, 3, 4), file.read(1, 3))
            assertContentEquals(byteArrayOf(1), file.read(0, 1))
            file.delete()
            file.delete()
            assertEquals(0L, Files.list(directory).use { it.count() })
        } finally { Files.delete(directory) }
    }

    @Test
    fun failedAndCancelledWritesDeletePartialFiles() = runTest {
        val directory = Files.createTempDirectory("cast-store-test-")
        try {
            val store = JvmNaviampCastMediaStore(directory)
            assertFailsWith<IllegalStateException> {
                store.write { writer -> writer.write(byteArrayOf(1), 1); false }
            }
            assertFailsWith<CancellationException> {
                store.write { writer -> writer.write(byteArrayOf(1), 1); throw CancellationException("Cancelled transfer") }
            }
            assertEquals(0L, Files.list(directory).use { it.count() })
        } finally { Files.delete(directory) }
    }
}
