package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackReplayGainMode
import app.naviamp.domain.playback.PlaybackTransitionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoragePlaybackProfileStoreTest {
    @Test
    fun storesUpdatesListsAndDeletesSourceScopedProfiles() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val database = NaviampStorageDatabase(driver)
            database.naviampStorageQueries.upsertMediaSource(
                id = "source",
                provider_id = "navidrome",
                cache_namespace = "source",
                server_connection_key = "server",
                library_scope_key = "library",
                display_name = "Server",
                base_url = "https://example.test",
                username = "user",
                token = "token",
                salt = "salt",
                native_token = null,
                insecure_skip_tls_verification = 0,
                custom_certificate_path = null,
                client_certificate_keystore_path = null,
                client_certificate_keystore_password = null,
                secondary_urls_json = "[]",
                custom_headers_json = "[]",
                selected_music_folder_ids_json = "[]",
                created_at_epoch_millis = 1,
                last_connected_at_epoch_millis = 1,
                last_sync_started_at_epoch_millis = null,
                last_sync_completed_at_epoch_millis = null,
                last_library_scan_signature = null,
                last_library_scan_checked_at_epoch_millis = null,
            )
            val store = StoragePlaybackProfileStore(database.naviampStorageQueries, { 42L })
            val target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, " album ")
            val profile = PlaybackProfile(
                transitionMode = PlaybackTransitionMode.Crossfade,
                crossfadeDurationSeconds = 7,
                replayGainMode = PlaybackReplayGainMode.Album,
            )

            store.savePlaybackProfile("source", target, profile)

            assertEquals(profile, store.playbackProfile("source", target))
            assertEquals(listOf("album"), store.playbackProfiles("source").map { it.target.id })
            assertNull(store.playbackProfile("other", target))

            store.savePlaybackProfile("source", target, PlaybackProfile())
            assertNull(store.playbackProfile("source", target))
        } finally {
            driver.close()
        }
    }
}
