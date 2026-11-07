package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.resolveAgainst
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackReplayGainMode
import app.naviamp.domain.playback.PlaybackTransitionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoragePlaybackProfileStoreTest {
    @Test
    fun reopeningTheDatabasePreservesAlbumOverridesAndSourceIsolation() {
        val directory = java.nio.file.Files.createTempDirectory("naviamp-profile-restart").toFile()
        val url = "jdbc:sqlite:${directory.resolve("profiles.db").absolutePath}"
        val profiles = listOf(
            PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless),
            PlaybackProfile(transitionMode = PlaybackTransitionMode.Crossfade, crossfadeDurationSeconds = 3),
            PlaybackProfile(transitionMode = PlaybackTransitionMode.Pause),
        ).mapIndexed { index, profile ->
            PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album-$index") to profile
        }
        try {
            JdbcSqliteDriver(url).use { driver ->
                val database = NaviampStorageDatabase(driver)
                NaviampStorageDatabase.Schema.create(driver)
                seedSource(database)
                val store = StoragePlaybackProfileStore(database.naviampStorageQueries, { 42L })
                profiles.forEach { (target, profile) -> store.savePlaybackProfile("source", target, profile) }
            }
            JdbcSqliteDriver(url).use { driver ->
                val store = StoragePlaybackProfileStore(NaviampStorageDatabase(driver).naviampStorageQueries, { 43L })
                val global = app.naviamp.domain.settings.PlaybackSettings(
                    gaplessEnabled = false, crossfadeDurationSeconds = 8)
                profiles.forEach { (target, expected) ->
                    val restored = kotlin.test.assertNotNull(store.playbackProfile("source", target))
                    assertEquals(expected, restored)
                    val effective = restored.resolveAgainst(global)
                    assertEquals(if (expected.transitionMode == PlaybackTransitionMode.Crossfade) 3 else 0,
                        effective.crossfadeDurationSeconds)
                    assertEquals(expected.transitionMode == PlaybackTransitionMode.Gapless, effective.gaplessEnabled)
                    assertNull(store.playbackProfile("other-source", target))
                }
                store.savePlaybackProfile("source", profiles.first().first, PlaybackProfile())
            }
            JdbcSqliteDriver(url).use { driver ->
                val store = StoragePlaybackProfileStore(NaviampStorageDatabase(driver).naviampStorageQueries, { 44L })
                assertNull(store.playbackProfile("source", profiles.first().first))
                assertEquals(profiles.drop(1).toMap(), store.playbackProfiles("source").associate { it.target to it.profile })
            }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun storesUpdatesListsAndDeletesSourceScopedProfiles() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val database = NaviampStorageDatabase(driver)
            seedSource(database)
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

private fun seedSource(database: NaviampStorageDatabase) {
    database.naviampStorageQueries.upsertMediaSource(
        id = "source",
        provider_id = "navidrome",
        cache_namespace = "source",
        server_connection_key = "server",
        library_scope_key = "library",
        display_name = "Server",
        base_url = "https://example.test",
        username = "user",
        password = null,
        token = "token",
        salt = "salt",
        authentication_mode = "token",
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
}
