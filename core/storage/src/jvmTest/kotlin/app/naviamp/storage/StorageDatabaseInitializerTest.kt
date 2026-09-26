package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StorageDatabaseInitializerTest {
    @Test
    fun versionTwentySixAddsAuthenticationModeAndPreservesSavedCredentials() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            driver.execute(null, "ALTER TABLE media_source DROP COLUMN authentication_mode", 0)
            driver.execute(null, """
                INSERT INTO media_source(id, provider_id, cache_namespace, display_name, base_url,
                    username, token, salt, password, created_at_epoch_millis)
                VALUES ('source', 'navidrome', 'cache', 'Server', 'https://example.test',
                    'user', '', '', 'saved-password', 1)
            """.trimIndent(), 0)
            driver.execute(null, "PRAGMA user_version = 26", 0)
            val database = initializeNaviampStorageDatabase(driver)
            val source = database.naviampStorageQueries.selectMediaSourceById("source").executeAsOne()
            assertEquals("token", source.authentication_mode)
            assertEquals("saved-password", source.password)
            assertEquals("", source.token)
        } finally {
            driver.close()
        }
    }

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

    @Test
    fun versionTwentyOneDatabaseMissingSelectedLibrariesIsRepairedDuringMigration() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.createVersionTwentyOneSchema(includeSelectedMusicFolders = false)

            initializeNaviampStorageDatabase(driver)

            assertEquals(NaviampStorageSchema.version, driver.userVersion())
            assertTrue(driver.tableColumns("media_source").contains("selected_music_folder_ids_json"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun versionTwentyOneDatabasePreservesExistingSelectedLibrariesDuringMigration() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.createVersionTwentyOneSchema(includeSelectedMusicFolders = true)
            driver.execute(
                null,
                """
                INSERT INTO media_source(
                  id, provider_id, cache_namespace, display_name, base_url, username, token, salt,
                  created_at_epoch_millis, selected_music_folder_ids_json
                ) VALUES ('source', 'jellyfin', 'cache', 'Server', 'https://example.test', 'user',
                  'token', 'salt', 1, '["music"]')
                """.trimIndent(),
                0,
            )
            driver.execute(null, "PRAGMA user_version = 21", 0)

            val database = initializeNaviampStorageDatabase(driver)

            assertEquals(
                "[\"music\"]",
                database.naviampStorageQueries.selectMediaSourceById("source")
                    .executeAsOne()
                    .selected_music_folder_ids_json,
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun releasedVersionTwentyFiveAddsConnectPasswordWithoutChangingExistingData() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            driver.execute(null, "ALTER TABLE media_source DROP COLUMN password", 0)
            driver.execute(null, "ALTER TABLE media_source DROP COLUMN authentication_mode", 0)
            driver.execute(null, """
                INSERT INTO media_source(id, provider_id, cache_namespace, display_name, base_url,
                    username, token, salt, created_at_epoch_millis)
                VALUES ('source', 'navidrome', 'cache', 'Server', 'https://example.test',
                    'user', 'token', 'salt', 1)
            """.trimIndent(), 0)
            driver.execute(null, "PRAGMA user_version = 25", 0)

            val database = initializeNaviampStorageDatabase(driver)

            assertEquals(NaviampStorageSchema.version, driver.userVersion())
            val source = database.naviampStorageQueries.selectMediaSourceById("source").executeAsOne()
            assertEquals("token", source.token)
            assertEquals("salt", source.salt)
            assertEquals(null, source.password)
            assertTrue(driver.tableColumns("favorite_artist_activity").contains("artist_name"))
            assertTrue(driver.tableColumns("album_catalog_snapshot").contains("albums_json"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun releaseBaselineDatabaseAddsBranchTablesAndPreservesExistingLibrary() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE favorite_artist_activity", 0)
            driver.execute(null, "DROP TABLE album_catalog_snapshot", 0)
            driver.execute(null, "DROP TABLE library_track_artist_credit", 0)
            driver.execute(null, "ALTER TABLE media_source DROP COLUMN password", 0)
            driver.execute(null, "ALTER TABLE media_source DROP COLUMN authentication_mode", 0)
            driver.execute(null, "PRAGMA user_version = 24", 0)
            driver.execute(null, """
                INSERT INTO media_source(id, provider_id, cache_namespace, display_name, base_url,
                  username, token, salt, created_at_epoch_millis, selected_music_folder_ids_json)
                VALUES ('source', 'jellyfin', 'cache', 'Existing server', 'https://example.test',
                  'user', 'token', 'salt', 1, '["music"]')
            """.trimIndent(), 0)
            driver.execute(null, """
                INSERT INTO library_album(source_id, remote_album_id, title, artist_name,
                  search_title, search_artist_name, updated_at_epoch_millis, original_release_year)
                VALUES ('source', 'album', 'Existing album', 'Artist', 'existing album', 'artist', 1, 1977)
            """.trimIndent(), 0)

            val database = initializeNaviampStorageDatabase(driver)

            assertEquals(NaviampStorageSchema.version, driver.userVersion())
            assertEquals("Existing server", database.naviampStorageQueries.selectMediaSourceById("source").executeAsOne().display_name)
            assertEquals("[\"music\"]", database.naviampStorageQueries.selectMediaSourceById("source").executeAsOne().selected_music_folder_ids_json)
            assertEquals(1L, driver.queryLong("SELECT COUNT(*) FROM library_album WHERE remote_album_id = 'album' AND original_release_year = 1977"))
            assertEquals(1L, driver.queryLong("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'album_catalog_snapshot'"))
            assertEquals(
                1L,
                driver.queryLong(
                    "SELECT COUNT(*) FROM sqlite_master " +
                        "WHERE type = 'table' AND name = 'favorite_artist_activity'",
                ),
            )
            assertEquals(
                1L,
                driver.queryLong(
                    "SELECT COUNT(*) FROM sqlite_master " +
                        "WHERE type = 'table' AND name = 'library_track_artist_credit'",
                ),
            )
        } finally {
            driver.close()
        }
    }
}

private fun JdbcSqliteDriver.userVersion(): Long = queryLong("PRAGMA user_version")

private fun JdbcSqliteDriver.createVersionTwentyOneSchema(includeSelectedMusicFolders: Boolean) {
    NaviampStorageDatabase.Schema.create(this)
    execute(null, "ALTER TABLE library_album DROP COLUMN original_release_year", 0)
    execute(null, "ALTER TABLE library_track DROP COLUMN music_folder_id", 0)
    execute(null, "ALTER TABLE library_track DROP COLUMN album_release_year", 0)
    execute(null, "ALTER TABLE library_track DROP COLUMN original_release_year", 0)
    execute(null, "ALTER TABLE downloaded_audio DROP COLUMN original_release_year", 0)
    execute(null, "ALTER TABLE playback_history DROP COLUMN original_release_year", 0)
    execute(null, "ALTER TABLE playback_session_state DROP COLUMN queue_groups_payload", 0)
    execute(null, "ALTER TABLE media_source DROP COLUMN password", 0)
    execute(null, "ALTER TABLE media_source DROP COLUMN authentication_mode", 0)
    execute(null, "DROP TABLE playback_profile", 0)
    execute(null, "DROP TABLE favorite_artist_activity", 0)
    execute(null, "DROP TABLE album_catalog_snapshot", 0)
    execute(null, "DROP TABLE library_track_artist_credit", 0)
    if (!includeSelectedMusicFolders) {
        execute(null, "ALTER TABLE media_source DROP COLUMN selected_music_folder_ids_json", 0)
    }
    execute(null, "PRAGMA user_version = 21", 0)
}

private fun JdbcSqliteDriver.foreignKeysEnabled(): Long = queryLong("PRAGMA foreign_keys")

private fun JdbcSqliteDriver.queryLong(sql: String): Long =
    executeQuery(null, sql, { cursor ->
        app.cash.sqldelight.db.QueryResult.Value(
            if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L,
        )
    }, 0).value

private fun JdbcSqliteDriver.tableColumns(tableName: String): Set<String> =
    executeQuery(null, "PRAGMA table_info($tableName)", { cursor ->
        val columns = mutableSetOf<String>()
        while (cursor.next().value) cursor.getString(1)?.let(columns::add)
        app.cash.sqldelight.db.QueryResult.Value(columns)
    }, 0).value
