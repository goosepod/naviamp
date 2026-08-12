package app.naviamp.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.naviamp.storage.NaviampStorageSchema
import app.naviamp.storage.StorageDatabaseDriverFactory
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.storage.prepareNaviampStorageDriver
import app.naviamp.storage.shouldReplaceNaviampStorageDatabase
import java.io.File

/** Android adapter for Context-backed SQLDelight driver creation and database-file effects. */
internal class AndroidStorageDatabaseDriverFactory(context: Context) : StorageDatabaseDriverFactory {
    private val context = context.applicationContext

    override fun create(location: StorageDatabaseLocation): SqlDriver {
        val expectedDirectory = context.getDatabasePath(location.fileName).parentFile?.canonicalFile
        require(File(location.directoryPath).canonicalFile == expectedDirectory) {
            "Android storage databases must use the app database directory."
        }
        val databaseFile = context.getDatabasePath(location.fileName)
        val existedBeforeOpen = databaseFile.exists()
        replaceUnsupportedFutureSchema(databaseFile, location.fileName)
        return AndroidSqliteDriver(
            schema = NaviampStorageSchema,
            context = context,
            name = location.fileName,
        ).also { driver ->
            val preferences = context.getSharedPreferences(DatabaseMaintenancePreferences, Context.MODE_PRIVATE)
            prepareNaviampStorageDriver(
                driver = driver,
                existedBeforeOpen = existedBeforeOpen,
                lastReclaimedSchemaVersion = preferences.getLong(LastReclaimedSchemaVersion, 0L),
                recordReclaimedSchemaVersion = { version ->
                    preferences.edit().putLong(LastReclaimedSchemaVersion, version).apply()
                },
            )
        }
    }

    private fun replaceUnsupportedFutureSchema(databaseFile: File, databaseName: String) {
        if (!databaseFile.exists()) return
        val installedVersion = runCatching {
            SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { it.version.toLong() }
        }.getOrNull()
        if (shouldReplaceNaviampStorageDatabase(installedVersion)) context.deleteDatabase(databaseName)
    }
}

internal const val AndroidStorageDatabaseName = "naviamp-storage.db"
private const val DatabaseMaintenancePreferences = "naviamp-storage-maintenance"
private const val LastReclaimedSchemaVersion = "last-reclaimed-schema-version"
