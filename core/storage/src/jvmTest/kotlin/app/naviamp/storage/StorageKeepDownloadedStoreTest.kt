package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.ArtistAlbumScope
import app.naviamp.domain.cache.PersistedDownloadJob
import app.naviamp.domain.cache.createDownloadJob
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StorageKeepDownloadedStoreTest {
    @Test
    fun interruptedJobRequestSurvivesStoreRecreationWithItsQualityAndTracks() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            driver.execute(null, """
                INSERT INTO media_source(id, provider_id, cache_namespace, display_name, base_url,
                    username, token, salt, created_at_epoch_millis)
                VALUES ('source', 'navidrome', 'cache', 'Server', 'https://example.test',
                    'user', '', '', 1)
            """.trimIndent(), 0)
            val first = StorageKeepDownloadedStore(queries, nowEpochMillis = { 42L })
            val track = Track(TrackId("three"), "Three", artistName = "Artist", albumTitle = "Album",
                durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
            val saved = PersistedDownloadJob("source", createDownloadJob("download-000000000001", "Album", listOf(track)),
                "transcoded:mp3:192", replaceExisting = false, manualRetention = true,
                includeCompletedCount = true)
            first.saveDownloadJob(saved)

            val restarted = StorageKeepDownloadedStore(queries, nowEpochMillis = { 43L })
            assertEquals(listOf(saved), restarted.savedDownloadJobs("source"))
            restarted.deleteDownloadJob("source", saved.job.id)
            assertEquals(emptyList(), restarted.savedDownloadJobs("source"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun albumAndArtistSubscriptionsSurviveStoreRecreationWithMembership() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            val first = StorageKeepDownloadedStore(queries, nowEpochMillis = { 42L })
            val album = KeepDownloadedCollectionPolicy("source", KeepDownloadedCollectionKind.Album, "album", "Album")
            val artist = KeepDownloadedCollectionPolicy("source", KeepDownloadedCollectionKind.Artist, "artist", "Artist",
                artistAlbumScope = ArtistAlbumScope.FavoriteAlbums)
            first.replaceKeepDownloadedTrackIds(album, setOf("one", "two"))
            first.replaceKeepDownloadedTrackIds(artist, setOf("two", "three"))

            val restarted = StorageKeepDownloadedStore(queries, nowEpochMillis = { 43L })
            assertEquals(setOf(album, artist), restarted.keepDownloadedPolicies("source").toSet())
            assertEquals(setOf("one", "two"), restarted.keepDownloadedTrackIds("source", album.kind, album.collectionId))
            assertEquals(setOf("two", "three"), restarted.keepDownloadedTrackIds("source", artist.kind, artist.collectionId))
            assertEquals(emptyList(), restarted.keepDownloadedPolicies("another-source"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun persistsPolicyMembershipAndManagedOwnership() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val store = StorageKeepDownloadedStore(
                NaviampStorageDatabase(driver).naviampStorageQueries,
                nowEpochMillis = { 42L },
            )
            val policy = KeepDownloadedCollectionPolicy(
                sourceId = "source",
                kind = KeepDownloadedCollectionKind.Playlist,
                collectionId = "playlist",
                name = "Playlist",
            )

            store.replaceKeepDownloadedTrackIds(policy, setOf("one", "two"))
            store.markManagedKeepDownloadedTracks("source", setOf("one", "two"))
            store.unmarkManagedKeepDownloadedTracks("source", setOf("two"))

            assertEquals(policy, store.keepDownloadedPolicy("source", policy.kind, policy.collectionId))
            assertEquals(setOf("one", "two"), store.keepDownloadedTrackIds("source", policy.kind, policy.collectionId))
            assertEquals(setOf("one"), store.managedKeepDownloadedTrackIds("source"))

            store.deleteKeepDownloadedPolicy("source", policy.kind, policy.collectionId)
            assertNull(store.keepDownloadedPolicy("source", policy.kind, policy.collectionId))
        } finally {
            driver.close()
        }
    }

    @Test
    fun manualRetentionIsSourceScopedAndCanBeReleased() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val store = StorageKeepDownloadedStore(
                NaviampStorageDatabase(driver).naviampStorageQueries,
                nowEpochMillis = { 42L },
            )

            store.retainManualTrack("one", "same-track")
            store.retainManualTrack("one", "same-track")
            store.retainManualTrack("two", "same-track")
            assertEquals(setOf("same-track"), store.manuallyRetainedTrackIds("one"))
            assertEquals(setOf("same-track"), store.manuallyRetainedTrackIds("two"))

            store.releaseTrackRetention("one", "same-track")
            assertEquals(emptySet(), store.manuallyRetainedTrackIds("one"))
            assertEquals(setOf("same-track"), store.manuallyRetainedTrackIds("two"))
        } finally {
            driver.close()
        }
    }
}
