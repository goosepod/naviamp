package app.naviamp.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * Runtime schema wrapper for compatibility repairs that cannot be expressed conditionally in
 * SQLite migration SQL. Platform drivers must use this schema instead of the generated schema.
 */
object NaviampStorageSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
        get() = NaviampStorageDatabase.Schema.version

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> =
        NaviampStorageDatabase.Schema.create(driver)

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: app.cash.sqldelight.db.AfterVersion,
    ): QueryResult.Value<Unit> {
        if (oldVersion == SelectedMusicFoldersCompatibilityVersion &&
            newVersion > SelectedMusicFoldersCompatibilityVersion &&
            !driver.storageTableHasColumn("media_source", "selected_music_folder_ids_json")
        ) {
            driver.execute(null, "ALTER TABLE media_source ADD COLUMN selected_music_folder_ids_json TEXT", 0)
        }
        return NaviampStorageDatabase.Schema.migrate(driver, oldVersion, newVersion, *callbacks)
    }
}

/** Shared schema creation, migration, compatibility, and connection initialization policy. */
fun initializeNaviampStorageDatabase(driver: SqlDriver): NaviampStorageDatabase {
    val currentVersion = driver.storageDatabaseVersion()
    val targetVersion = NaviampStorageSchema.version
    when {
        currentVersion == 0L && !driver.hasStorageUserTables() -> {
            NaviampStorageSchema.create(driver)
            driver.setStorageDatabaseVersion(targetVersion)
        }
        currentVersion == 0L -> error(
            "The existing Naviamp database predates versioned shared storage and must be migrated " +
                "by a compatible earlier Naviamp version.",
        )
        currentVersion < targetVersion -> {
            NaviampStorageSchema.migrate(driver, currentVersion, targetVersion)
            driver.setStorageDatabaseVersion(targetVersion)
        }
        currentVersion > targetVersion -> error(
            "The Naviamp database version $currentVersion is newer than supported version $targetVersion.",
        )
    }
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    return NaviampStorageDatabase(driver).also(::installBundledGenreOntology)
}

private fun SqlDriver.storageDatabaseVersion(): Long =
    executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        parameters = 0,
    ).value

private fun SqlDriver.hasStorageUserTables(): Boolean =
    executeQuery(
        identifier = null,
        sql = "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%')",
        mapper = { cursor -> QueryResult.Value(cursor.next().value && cursor.getLong(0) == 1L) },
        parameters = 0,
    ).value

private fun SqlDriver.storageTableHasColumn(tableName: String, columnName: String): Boolean =
    executeQuery(null, "PRAGMA table_info($tableName)", { cursor ->
        var found = false
        while (cursor.next().value) {
            if (cursor.getString(1) == columnName) found = true
        }
        QueryResult.Value(found)
    }, 0).value

private fun SqlDriver.setStorageDatabaseVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0)
}

private const val SelectedMusicFoldersCompatibilityVersion = 21L
