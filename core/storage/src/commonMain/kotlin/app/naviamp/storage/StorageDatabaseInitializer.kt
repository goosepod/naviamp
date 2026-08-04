package app.naviamp.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/** Shared schema creation, migration, compatibility, and connection initialization policy. */
fun initializeNaviampStorageDatabase(driver: SqlDriver): NaviampStorageDatabase {
    val currentVersion = driver.storageDatabaseVersion()
    val targetVersion = NaviampStorageDatabase.Schema.version
    when {
        currentVersion == 0L && !driver.hasStorageUserTables() -> {
            NaviampStorageDatabase.Schema.create(driver)
            driver.setStorageDatabaseVersion(targetVersion)
        }
        currentVersion == 0L -> error(
            "The existing Naviamp database predates versioned shared storage and must be migrated " +
                "by a compatible earlier Naviamp version.",
        )
        currentVersion < targetVersion -> {
            NaviampStorageDatabase.Schema.migrate(driver, currentVersion, targetVersion)
            driver.setStorageDatabaseVersion(targetVersion)
        }
        currentVersion > targetVersion -> error(
            "The Naviamp database version $currentVersion is newer than supported version $targetVersion.",
        )
    }
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    return NaviampStorageDatabase(driver)
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

private fun SqlDriver.setStorageDatabaseVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0)
}
