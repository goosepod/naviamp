package app.naviamp.desktop

import app.cash.sqldelight.db.SqlDriver
import app.naviamp.desktop.security.DesktopCredentialProtector
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseDriverFactory
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.storage.StorageMediaSourceStore
import app.naviamp.storage.initializeNaviampStorageDatabase

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
                val database = initializeNaviampStorageDatabase(driver)
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
