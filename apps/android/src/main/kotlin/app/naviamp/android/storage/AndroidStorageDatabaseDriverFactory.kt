package app.naviamp.android

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageDatabaseDriverFactory
import app.naviamp.storage.StorageDatabaseLocation
import java.io.File

/** Android host adapter for SQLDelight creation, compatibility repair, and SQLite setup. */
internal class AndroidStorageDatabaseDriverFactory(context: Context) : StorageDatabaseDriverFactory {
    private val context = context.applicationContext

    override fun create(location: StorageDatabaseLocation): SqlDriver {
        val expectedDirectory = context.getDatabasePath(location.fileName).parentFile?.canonicalFile
        require(File(location.directoryPath).canonicalFile == expectedDirectory) {
            "Android storage databases must use the app database directory."
        }
        val databaseFile = context.getDatabasePath(location.fileName)
        val existedBeforeOpen = databaseFile.exists()
        removeUnsupportedFutureSchema(databaseFile, location.fileName)
        return AndroidSqliteDriver(
            schema = NaviampStorageDatabase.Schema,
            context = context,
            name = location.fileName,
        ).also { driver ->
            driver.configureSqliteLockHandling()
            driver.ensureCompatibilitySchema()
            if (existedBeforeOpen) driver.reclaimAfterSchemaUpgrade()
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
        }
    }

    private fun removeUnsupportedFutureSchema(databaseFile: File, databaseName: String) {
        if (!databaseFile.exists()) return
        val installedVersion = runCatching {
            SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                .use { it.version.toLong() }
        }.getOrNull()
        if (installedVersion != null && installedVersion > NaviampStorageDatabase.Schema.version) {
            context.deleteDatabase(databaseName)
        }
    }

    private fun SqlDriver.reclaimAfterSchemaUpgrade() {
        val preferences = context.getSharedPreferences(DatabaseMaintenancePreferences, Context.MODE_PRIVATE)
        val schemaVersion = NaviampStorageDatabase.Schema.version
        if (preferences.getLong(LastReclaimedSchemaVersion, 0L) < schemaVersion) {
            execute(null, "VACUUM", 0)
            preferences.edit().putLong(LastReclaimedSchemaVersion, schemaVersion).apply()
        }
    }
}

private fun SqlDriver.configureSqliteLockHandling() {
    executePragma("PRAGMA busy_timeout=$SqliteBusyTimeoutMillis")
    executePragma("PRAGMA journal_mode=WAL")
}

private fun SqlDriver.executePragma(sql: String) {
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            while (cursor.next().value) Unit
            app.cash.sqldelight.db.QueryResult.Unit
        },
        parameters = 0,
    )
}

private fun SqlDriver.ensureCompatibilitySchema() {
    if (!tableHasColumn("media_source", "secondary_urls_json")) execute(null, "ALTER TABLE media_source ADD COLUMN secondary_urls_json TEXT", 0)
    if (!tableHasColumn("media_source", "custom_headers_json")) execute(null, "ALTER TABLE media_source ADD COLUMN custom_headers_json TEXT", 0)
    if (!tableHasColumn("media_source", "selected_music_folder_ids_json")) execute(null, "ALTER TABLE media_source ADD COLUMN selected_music_folder_ids_json TEXT", 0)
    if (!tableHasColumn("media_source", "server_connection_key")) execute(null, "ALTER TABLE media_source ADD COLUMN server_connection_key TEXT", 0)
    if (!tableHasColumn("media_source", "library_scope_key")) execute(null, "ALTER TABLE media_source ADD COLUMN library_scope_key TEXT", 0)
    if (!tableHasColumn("library_track", "play_count")) execute(null, "ALTER TABLE library_track ADD COLUMN play_count INTEGER", 0)
    if (!tableHasColumn("library_track", "last_played_at_iso8601")) execute(null, "ALTER TABLE library_track ADD COLUMN last_played_at_iso8601 TEXT", 0)
    execute(null, TrackLyricsOffsetSchema, 0)
    execute(null, KeepDownloadedCollectionSchema, 0)
    execute(null, KeepDownloadedCollectionTrackSchema, 0)
    execute(null, KeepDownloadedManagedTrackSchema, 0)
    execute(null, KeepDownloadedRemoteIndex, 0)
    execute(null, PendingProviderActionSchema, 0)
    execute(null, PendingProviderActionIndex, 0)
    execute(null, RadioDjPresetSchema, 0)
    execute(null, RadioDjPresetIndex, 0)
}

private fun SqlDriver.tableHasColumn(tableName: String, columnName: String): Boolean {
    var found = false
    executeQuery(null, "PRAGMA table_info($tableName)", { cursor ->
        while (cursor.next().value) if (cursor.getString(1) == columnName) { found = true; break }
        app.cash.sqldelight.db.QueryResult.Unit
    }, 0)
    return found
}

internal const val AndroidStorageDatabaseName = "naviamp-storage.db"
private const val DatabaseMaintenancePreferences = "naviamp-storage-maintenance"
private const val LastReclaimedSchemaVersion = "last-reclaimed-schema-version"
private const val SqliteBusyTimeoutMillis = 10_000

private val TrackLyricsOffsetSchema = """
CREATE TABLE IF NOT EXISTS track_lyrics_offset (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  offset_millis INTEGER NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, remote_track_id)
)
""".trimIndent()

private val KeepDownloadedCollectionSchema = """
CREATE TABLE IF NOT EXISTS keep_downloaded_collection (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  collection_kind TEXT NOT NULL,
  collection_id TEXT NOT NULL,
  name TEXT NOT NULL,
  remove_unneeded_files INTEGER NOT NULL DEFAULT 0,
  updated_at_epoch_millis INTEGER NOT NULL,
  PRIMARY KEY(source_id, collection_kind, collection_id)
)
""".trimIndent()

private val KeepDownloadedCollectionTrackSchema = """
CREATE TABLE IF NOT EXISTS keep_downloaded_collection_track (
  source_id TEXT NOT NULL,
  collection_kind TEXT NOT NULL,
  collection_id TEXT NOT NULL,
  remote_track_id TEXT NOT NULL,
  PRIMARY KEY(source_id, collection_kind, collection_id, remote_track_id),
  FOREIGN KEY(source_id, collection_kind, collection_id)
    REFERENCES keep_downloaded_collection(source_id, collection_kind, collection_id) ON DELETE CASCADE
)
""".trimIndent()

private val KeepDownloadedManagedTrackSchema = """
CREATE TABLE IF NOT EXISTS keep_downloaded_managed_track (
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  remote_track_id TEXT NOT NULL,
  PRIMARY KEY(source_id, remote_track_id)
)
""".trimIndent()

private val KeepDownloadedRemoteIndex = """
CREATE INDEX IF NOT EXISTS keep_downloaded_collection_track_remote
ON keep_downloaded_collection_track(source_id, remote_track_id)
""".trimIndent()

private val PendingProviderActionSchema = """
CREATE TABLE IF NOT EXISTS pending_provider_action (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
  action_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  bool_value INTEGER,
  long_value INTEGER,
  created_at_epoch_millis INTEGER NOT NULL,
  last_attempt_at_epoch_millis INTEGER,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
)
""".trimIndent()

private val PendingProviderActionIndex = """
CREATE INDEX IF NOT EXISTS pending_provider_action_source_created
ON pending_provider_action(source_id, created_at_epoch_millis)
""".trimIndent()

private val RadioDjPresetSchema = """
CREATE TABLE IF NOT EXISTS radio_dj_preset (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  familiarity TEXT NOT NULL,
  artist_spread TEXT NOT NULL,
  same_decade_only INTEGER NOT NULL,
  artist_run_mode TEXT NOT NULL,
  same_artist_run_length INTEGER NOT NULL,
  other_artist_run_length INTEGER NOT NULL,
  sort_order INTEGER NOT NULL,
  created_at_epoch_millis INTEGER NOT NULL,
  updated_at_epoch_millis INTEGER NOT NULL
)
""".trimIndent()

private val RadioDjPresetIndex = """
CREATE INDEX IF NOT EXISTS radio_dj_preset_sort ON radio_dj_preset(sort_order, name)
""".trimIndent()
