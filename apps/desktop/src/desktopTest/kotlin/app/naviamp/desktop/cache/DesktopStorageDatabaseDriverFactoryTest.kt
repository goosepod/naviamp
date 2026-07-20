package app.naviamp.desktop

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.naviamp.storage.StorageDatabaseLocation
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopStorageDatabaseDriverFactoryTest {
    @Test
    fun createsTheDatabaseDirectoryAndConfiguresDesktopConnections() {
        val root = Files.createTempDirectory("naviamp-driver-factory")
        val databaseDirectory = root.resolve("nested")
        val databaseFile = databaseDirectory.resolve("test.db")
        val driver = DesktopStorageDatabaseDriverFactory.create(
            StorageDatabaseLocation(
                directoryPath = databaseDirectory.toString(),
                fileName = databaseFile.fileName.toString(),
            ),
        )

        try {
            val busyTimeout = driver.longPragma("busy_timeout")
            val journalMode = driver.stringPragma("journal_mode")
            assertTrue(Files.isDirectory(databaseDirectory))
            assertTrue(Files.isRegularFile(databaseFile))
            assertEquals(10_000L, busyTimeout)
            assertEquals("wal", journalMode)
        } finally {
            driver.close()
        }
    }
}

private fun SqlDriver.longPragma(name: String): Long? =
    executeQuery(null, "PRAGMA $name", { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
    }, 0).value

private fun SqlDriver.stringPragma(name: String): String? =
    executeQuery(null, "PRAGMA $name", { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
    }, 0).value
