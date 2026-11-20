@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real native SQLite effects in an owned temporary directory on both iOS and tvOS. */
class AppleStorageDriverTest {
    @Test
    fun nativeDriverCreatesCanonicalSchemaAndEnforcesForeignKeys() = withDatabaseLocation { location ->
        val driver = NativeStorageDriverFactory(location).createDriver()
        try {
            val database = NaviampStorageDatabase(driver)
            assertEquals(emptyList(), database.naviampStorageQueries.selectMediaSources().executeAsList())
            assertEquals(NaviampStorageSchema.version, driver.readLong("PRAGMA user_version"))
            assertEquals(1L, driver.readLong("PRAGMA foreign_keys"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun nativeDriverReopensCommittedRowsInTheHostSelectedDirectory() = withDatabaseLocation { location ->
        val first = NativeStorageDriverFactory(location).createDriver()
        try {
            first.execute(null, """
                INSERT INTO cached_response(cache_key, provider_id, resource_type, resource_id,
                    payload, created_at_epoch_millis, last_accessed_epoch_millis)
                VALUES ('fixture', 'navidrome', 'test', 'one', '{}', 1, 1)
            """.trimIndent(), 0)
        } finally {
            first.close()
        }
        assertTrue(NSFileManager.defaultManager.fileExistsAtPath("${location.directoryPath}/${location.fileName}"))
        val reopened = NativeStorageDriverFactory(location).createDriver()
        try {
            assertEquals(1L, reopened.readLong("SELECT COUNT(*) FROM cached_response WHERE cache_key='fixture'"))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun emptyReplacementDirectoryRebuildsSchemaAfterSimulatedCachePurge() = withDatabaseLocation { location ->
        NativeStorageDriverFactory(location).createDriver().close()
        val manager = NSFileManager.defaultManager
        assertTrue(manager.removeItemAtPath(location.directoryPath, null))
        assertTrue(manager.createDirectoryAtPath(location.directoryPath, true, null, null))
        val rebuilt = NativeStorageDriverFactory(location).createDriver()
        try {
            assertEquals(emptyList(), NaviampStorageDatabase(rebuilt).naviampStorageQueries.selectMediaSources().executeAsList())
            assertEquals(NaviampStorageSchema.version, rebuilt.readLong("PRAGMA user_version"))
        } finally {
            rebuilt.close()
        }
    }

    private fun withDatabaseLocation(test: (StorageDatabaseLocation) -> Unit) {
        val path = NSTemporaryDirectory().trimEnd('/') + "/naviamp-590-native-db-" + NSUUID().UUIDString
        val manager = NSFileManager.defaultManager
        check(manager.createDirectoryAtPath(path, true, null, null))
        try {
            test(StorageDatabaseLocation(path))
        } finally {
            check(manager.removeItemAtPath(path, null))
        }
    }

    private fun SqlDriver.readLong(sql: String): Long? = executeQuery(null, sql,
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
        parameters = 0,
    ).value
}
