package app.naviamp.desktop

import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageDatabaseLocation
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopStorageDatabaseDriverFactoryTest {
    @Test
    fun createsAConfiguredJdbcDatabaseInTheHostSelectedDirectory() {
        val directory = Files.createTempDirectory("naviamp-desktop-database-test")
        val databaseFile = directory.resolve("core.db")
        val driver = DesktopStorageDatabaseDriverFactory.create(
            StorageDatabaseLocation(directory.toString(), databaseFile.fileName.toString()),
        )
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val database = NaviampStorageDatabase(driver)

            assertEquals(0L, database.naviampStorageQueries.storageStats().executeAsOne().media_source_count)
            assertTrue(databaseFile.exists())
        } finally {
            driver.close()
            databaseFile.deleteIfExists()
            directory.resolve("core.db-shm").deleteIfExists()
            directory.resolve("core.db-wal").deleteIfExists()
            directory.deleteIfExists()
        }
    }
}
