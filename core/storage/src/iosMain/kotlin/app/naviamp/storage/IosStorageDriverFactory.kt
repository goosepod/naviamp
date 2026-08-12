package app.naviamp.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.JournalMode

/**
 * Builds the native SQLDelight driver without selecting an iOS filesystem location itself.
 *
 * The thin iOS host must create an Application Support directory, opt it out of backup if desired,
 * and pass that absolute path as [StorageDatabaseLocation.directoryPath].
 */
class IosStorageDriverFactory(
    private val location: StorageDatabaseLocation,
) {
    init {
        require(location.directoryPath.startsWith('/')) {
            "The iOS storage database directory must be an absolute path."
        }
    }

    fun createDriver(): SqlDriver = NativeSqliteDriver(
        schema = NaviampStorageSchema,
        name = location.fileName,
        onConfiguration = { configuration ->
            configuration.copy(
                journalMode = JournalMode.WAL,
                extendedConfig = configuration.extendedConfig.copy(
                    basePath = location.directoryPath,
                    busyTimeout = SqliteBusyTimeoutMillis,
                    foreignKeyConstraints = true,
                ),
            )
        },
    )
}

private const val SqliteBusyTimeoutMillis = 10_000
