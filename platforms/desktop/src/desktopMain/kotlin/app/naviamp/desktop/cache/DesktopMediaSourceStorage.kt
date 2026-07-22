package app.naviamp.desktop

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.naviamp.desktop.security.DesktopCredentialProtector
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseDriverFactory
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.storage.StorageMediaSourceStore

/**
 * Durable Desktop owner for Core's shared media-source repository.
 *
 * SQL schema and credential persistence remain shared concerns. Desktop supplies only the JDBC
 * driver, its lifecycle, and the operating-system credential protector.
 */
class DesktopMediaSourceStorage private constructor(
    private val driver: SqlDriver,
    internal val database: NaviampStorageDatabase,
    private val store: StorageMediaSourceStore,
) : MediaSourceRepository by store,
    ProviderMediaSourceRepository by store,
    AutoCloseable {
    override fun close() {
        driver.close()
    }

    companion object {
        fun open(
            location: StorageDatabaseLocation,
            nowEpochMillis: () -> Long = DesktopSystemClock::nowEpochMillis,
            credentialProtector: StorageCredentialProtector = DesktopCredentialProtector(),
            driverFactory: StorageDatabaseDriverFactory = DesktopStorageDatabaseDriverFactory,
        ): DesktopMediaSourceStorage {
            val driver = driverFactory.create(location)
            return try {
                val database = initializeDatabase(driver)
                DesktopMediaSourceStorage(
                    driver = driver,
                    database = database,
                    store = StorageMediaSourceStore(
                        queries = database.naviampStorageQueries,
                        nowMillis = nowEpochMillis,
                        credentialProtector = credentialProtector,
                    ),
                )
            } catch (failure: Throwable) {
                driver.close()
                throw failure
            }
        }
    }
}

private fun initializeDatabase(driver: SqlDriver): NaviampStorageDatabase {
    val currentVersion = driver.databaseVersion()
    val targetVersion = NaviampStorageDatabase.Schema.version
    when {
        currentVersion == 0L && !driver.hasUserTables() -> {
            NaviampStorageDatabase.Schema.create(driver)
            driver.setDatabaseVersion(targetVersion)
        }
        currentVersion == 0L -> error(
            "The existing Naviamp database predates versioned shared storage and must be opened " +
                "once by the legacy Desktop host before migration.",
        )
        currentVersion < targetVersion -> {
            NaviampStorageDatabase.Schema.migrate(driver, currentVersion, targetVersion)
            driver.setDatabaseVersion(targetVersion)
        }
        currentVersion > targetVersion -> error(
            "The Naviamp database version $currentVersion is newer than supported version $targetVersion.",
        )
    }
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    return NaviampStorageDatabase(driver)
}

private fun SqlDriver.databaseVersion(): Long =
    executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
        parameters = 0,
    ).value

private fun SqlDriver.hasUserTables(): Boolean =
    executeQuery(
        identifier = null,
        sql = "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%')",
        mapper = { cursor -> QueryResult.Value(cursor.next().value && cursor.getLong(0) == 1L) },
        parameters = 0,
    ).value

private fun SqlDriver.setDatabaseVersion(version: Long) {
    execute(null, "PRAGMA user_version = $version", 0)
}
