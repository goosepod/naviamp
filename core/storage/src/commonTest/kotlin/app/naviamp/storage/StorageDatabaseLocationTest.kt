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
    fun rejectsFilenamesThatCanEscapeTheSelectedDirectory() {
        assertFailsWith<IllegalArgumentException> {
            StorageDatabaseLocation("/app/support", "../outside.db")
        }
        assertFailsWith<IllegalArgumentException> {
            StorageDatabaseLocation("/app/support", "nested/storage.db")
        }
    }
}
