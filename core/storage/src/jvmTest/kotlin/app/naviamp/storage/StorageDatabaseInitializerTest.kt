package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageDatabaseInitializerTest {
    @Test
    fun createsAndVersionsAFreshDatabase() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            val database = initializeNaviampStorageDatabase(driver)

            assertEquals(0L, database.naviampStorageQueries.storageStats().executeAsOne().media_source_count)
            assertEquals(NaviampStorageDatabase.Schema.version, driver.userVersion())
            assertEquals(1L, driver.foreignKeysEnabled())
        } finally {
            driver.close()
        }
    }

    @Test
    fun rejectsUnversionedExistingAndNewerSchemas() {
        val unversioned = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            unversioned.execute(null, "CREATE TABLE legacy_data (id INTEGER NOT NULL)", 0)
            val failure = assertFailsWith<IllegalStateException> {
                initializeNaviampStorageDatabase(unversioned)
            }
            assertTrue(failure.message.orEmpty().contains("predates versioned shared storage"))
        } finally {
            unversioned.close()
        }

        val newer = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            newer.execute(null, "PRAGMA user_version = ${NaviampStorageDatabase.Schema.version + 1}", 0)
            assertFailsWith<IllegalStateException> { initializeNaviampStorageDatabase(newer) }
        } finally {
            newer.close()
        }
    }

    @Test
    fun sharedCompatibilityPolicyConfiguresAndMaintainsAnOpenedDriver() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            var reclaimedVersion: Long? = null

            prepareNaviampStorageDriver(
                driver = driver,
                existedBeforeOpen = true,
                lastReclaimedSchemaVersion = 0L,
                recordReclaimedSchemaVersion = { reclaimedVersion = it },
            )

            assertEquals(NaviampStorageDatabase.Schema.version, reclaimedVersion)
            assertEquals(1L, driver.foreignKeysEnabled())
            assertTrue(shouldReplaceNaviampStorageDatabase(NaviampStorageDatabase.Schema.version + 1))
            assertTrue(!shouldReplaceNaviampStorageDatabase(NaviampStorageDatabase.Schema.version))
            assertTrue(!shouldReplaceNaviampStorageDatabase(null))
        } finally {
            driver.close()
        }
    }
}

private fun JdbcSqliteDriver.userVersion(): Long = queryLong("PRAGMA user_version")

private fun JdbcSqliteDriver.foreignKeysEnabled(): Long = queryLong("PRAGMA foreign_keys")

private fun JdbcSqliteDriver.queryLong(sql: String): Long =
    executeQuery(null, sql, { cursor ->
        app.cash.sqldelight.db.QueryResult.Value(
            if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L,
        )
    }, 0).value
