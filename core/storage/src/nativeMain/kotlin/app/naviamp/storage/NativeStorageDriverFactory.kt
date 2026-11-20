package app.naviamp.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.JournalMode

/** Kotlin/Native SQLDelight effect. Common policy validates the host-selected path and owns schemas. */
class NativeStorageDriverFactory(
    private val location: StorageDatabaseLocation,
) {
    init {
        location.requireAbsolutePosixDirectoryPath()
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
