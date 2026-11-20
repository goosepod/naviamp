package app.naviamp.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StorageDatabaseLocationTest {
    @Test
    fun acceptsHostSelectedDirectory() {
        val location = StorageDatabaseLocation("/app/support/naviamp")

        assertEquals("/app/support/naviamp", location.directoryPath)
        assertEquals(DefaultStorageDatabaseFileName, location.fileName)
    }

    @Test
    fun rejectsBlankDirectories() {
        assertFailsWith<IllegalArgumentException> { StorageDatabaseLocation("") }
    }

    @Test
    fun nativeBasePathRequiresAbsolutePosixSyntaxWithoutChoosingADirectory() {
        assertEquals("/owned/cache", StorageDatabaseLocation("/owned/cache").requireAbsolutePosixDirectoryPath())
        listOf("Library/Caches", "../cache", "C:\\cache").forEach { path ->
            assertFailsWith<IllegalArgumentException> {
                StorageDatabaseLocation(path).requireAbsolutePosixDirectoryPath()
            }
        }
        // Other driver contracts can still accept a relative location.
        assertEquals("relative", StorageDatabaseLocation("relative").directoryPath)
    }

    @Test
    fun rejectsFilenamesThatCanEscapeTheSelectedDirectory() {
        assertFailsWith<IllegalArgumentException> {
            StorageDatabaseLocation("/app/support", "../outside.db")
        }
        assertFailsWith<IllegalArgumentException> {
            StorageDatabaseLocation("/app/support", "nested/storage.db")
        }
    }
}
