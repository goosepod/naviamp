package app.naviamp.storage

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/** Shared policy for deciding whether an unsupported future database must be replaced. */
fun shouldReplaceNaviampStorageDatabase(installedVersion: Long?): Boolean =
    installedVersion != null && installedVersion > NaviampStorageDatabase.Schema.version

/**
 * Applies connection configuration and legacy-schema repair after a platform opens its driver.
 *
 * The host supplies only its persisted maintenance marker. All schema and maintenance decisions
 * remain shared so every platform converges on the same database.
 */
fun prepareNaviampStorageDriver(
    driver: SqlDriver,
    existedBeforeOpen: Boolean,
    lastReclaimedSchemaVersion: Long,
    recordReclaimedSchemaVersion: (Long) -> Unit,
) {
    driver.executePragma("PRAGMA busy_timeout=$StorageBusyTimeoutMillis")
    driver.executePragma("PRAGMA journal_mode=WAL")
    driver.ensureLegacyCompatibilitySchema()
    val schemaVersion = NaviampStorageDatabase.Schema.version
    if (existedBeforeOpen && lastReclaimedSchemaVersion < schemaVersion) {
        driver.execute(null, "VACUUM", 0)
        recordReclaimedSchemaVersion(schemaVersion)
    }
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
}

private fun SqlDriver.executePragma(sql: String) {
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            while (cursor.next().value) Unit
            QueryResult.Unit
        },
        parameters = 0,
    )
}

private fun SqlDriver.ensureLegacyCompatibilitySchema() {
    if (!tableHasColumn("media_source", "secondary_urls_json")) execute(null, "ALTER TABLE media_source ADD COLUMN secondary_urls_json TEXT", 0)
    if (!tableHasColumn("media_source", "custom_headers_json")) execute(null, "ALTER TABLE media_source ADD COLUMN custom_headers_json TEXT", 0)
    if (!tableHasColumn("media_source", "selected_music_folder_ids_json")) execute(null, "ALTER TABLE media_source ADD COLUMN selected_music_folder_ids_json TEXT", 0)
    if (!tableHasColumn("media_source", "server_connection_key")) execute(null, "ALTER TABLE media_source ADD COLUMN server_connection_key TEXT", 0)
    if (!tableHasColumn("media_source", "library_scope_key")) execute(null, "ALTER TABLE media_source ADD COLUMN library_scope_key TEXT", 0)
    if (!tableHasColumn("library_track", "play_count")) execute(null, "ALTER TABLE library_track ADD COLUMN play_count INTEGER", 0)
    if (!tableHasColumn("library_track", "last_played_at_iso8601")) execute(null, "ALTER TABLE library_track ADD COLUMN last_played_at_iso8601 TEXT", 0)
    LegacyCompatibilitySchema.forEach { statement -> execute(null, statement, 0) }
}

private fun SqlDriver.tableHasColumn(tableName: String, columnName: String): Boolean {
    var found = false
    executeQuery(null, "PRAGMA table_info($tableName)", { cursor ->
        while (cursor.next().value) {
            if (cursor.getString(1) == columnName) {
                found = true
                break
            }
        }
        QueryResult.Unit
    }, 0)
    return found
}

private const val StorageBusyTimeoutMillis = 10_000

private val LegacyCompatibilitySchema = listOf(
    """
    CREATE TABLE IF NOT EXISTS track_lyrics_offset (
      source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
      remote_track_id TEXT NOT NULL,
      offset_millis INTEGER NOT NULL,
      updated_at_epoch_millis INTEGER NOT NULL,
      PRIMARY KEY(source_id, remote_track_id)
    )
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS keep_downloaded_collection (
      source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
      collection_kind TEXT NOT NULL,
      collection_id TEXT NOT NULL,
      name TEXT NOT NULL,
      remove_unneeded_files INTEGER NOT NULL DEFAULT 0,
      updated_at_epoch_millis INTEGER NOT NULL,
      PRIMARY KEY(source_id, collection_kind, collection_id)
    )
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS keep_downloaded_collection_track (
      source_id TEXT NOT NULL,
      collection_kind TEXT NOT NULL,
      collection_id TEXT NOT NULL,
      remote_track_id TEXT NOT NULL,
      PRIMARY KEY(source_id, collection_kind, collection_id, remote_track_id),
      FOREIGN KEY(source_id, collection_kind, collection_id)
        REFERENCES keep_downloaded_collection(source_id, collection_kind, collection_id) ON DELETE CASCADE
    )
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS keep_downloaded_managed_track (
      source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
      remote_track_id TEXT NOT NULL,
      PRIMARY KEY(source_id, remote_track_id)
    )
    """.trimIndent(),
    """CREATE INDEX IF NOT EXISTS keep_downloaded_collection_track_remote
       ON keep_downloaded_collection_track(source_id, remote_track_id)""".trimIndent(),
    """
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
    """.trimIndent(),
    """CREATE INDEX IF NOT EXISTS pending_provider_action_source_created
       ON pending_provider_action(source_id, created_at_epoch_millis)""".trimIndent(),
    """
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
    """.trimIndent(),
    """CREATE INDEX IF NOT EXISTS radio_dj_preset_sort
       ON radio_dj_preset(sort_order, name)""".trimIndent(),
)
