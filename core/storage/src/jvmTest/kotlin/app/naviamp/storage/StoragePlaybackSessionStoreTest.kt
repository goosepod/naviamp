package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.domain.queue.PlaybackQueueGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoragePlaybackSessionStoreTest {
    @Test
    fun storesRestoresAndDeletesASourceScopedSession() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val database = NaviampStorageDatabase(driver)
            val store = StoragePlaybackSessionStore(database.naviampStorageQueries, { 42L })
            val session = PlaybackSessionSettings.fromTracks(
                tracks = listOf(testTrack("track")),
                currentIndex = 0,
                queueGroups = listOf(
                    PlaybackQueueGroup(
                        id = "album-group",
                        target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album"),
                        label = "Album",
                        startIndex = 0,
                        endIndexExclusive = 1,
                        profile = PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless),
                    ),
                ),
                positionSeconds = 12.5,
            )!!

            store.savePlaybackSession(session, "source")

            assertEquals(session, store.loadPlaybackSession("source"))
            val performance = store.performanceSnapshot()
            assertTrue(assertNotNull(performance.readMillis) >= 0.0)
            assertTrue(assertNotNull(performance.decodeMillis) >= 0.0)
            assertTrue(assertNotNull(performance.encodeMillis) >= 0.0)
            assertTrue(assertNotNull(performance.writeMillis) >= 0.0)
            assertTrue((performance.payloadCharacters ?: 0) > 0)

            store.savePlaybackSession(null, "source")

            assertNull(store.loadPlaybackSession("source"))
            assertNull(store.loadPlaybackSession(null))
            assertNull(store.loadPlaybackSession(""))
            store.savePlaybackSession(session, "")
            assertNull(store.loadPlaybackSession(""))
        } finally {
            driver.close()
        }
    }

    @Test
    fun updatesCursorStateWithoutRewritingAnUnchangedQueue() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val store = StoragePlaybackSessionStore(NaviampStorageDatabase(driver).naviampStorageQueries, { 42L })
            val first = PlaybackSessionSettings.fromTracks(
                tracks = listOf(testTrack("one"), testTrack("two")),
                currentIndex = 0,
                positionSeconds = 10.0,
            )!!

            store.savePlaybackSession(first, "source")
            assertEquals(true, store.performanceSnapshot().queueRewritten)

            val second = first.copy(currentIndex = 1, positionSeconds = 20.0)
            store.savePlaybackSession(second, "source")

            assertEquals(false, store.performanceSnapshot().queueRewritten)
            assertEquals(second, store.loadPlaybackSession("source"))
        } finally {
            driver.close()
        }
    }

    @Test
    fun readsLegacyJsonAndConvertsItOnTheNextSave() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NaviampStorageDatabase.Schema.create(driver)
            val queries = NaviampStorageDatabase(driver).naviampStorageQueries
            val store = StoragePlaybackSessionStore(queries, { 42L })
            val session = PlaybackSessionSettings.fromTracks(
                tracks = listOf(testTrack("legacy")),
                currentIndex = 0,
                positionSeconds = 12.0,
            )!!
            queries.upsertPlaybackSession("source", kotlinx.serialization.json.Json.encodeToString(session), 1L)

            assertEquals(session, store.loadPlaybackSession("source"))

            store.savePlaybackSession(session.copy(positionSeconds = 15.0), "source")

            assertNull(queries.selectPlaybackSession("source").executeAsOneOrNull())
            assertEquals(listOf("legacy"), queries.selectPlaybackSessionQueueTrackIds("source").executeAsList())
            assertEquals(15.0, store.loadPlaybackSession("source")?.positionSeconds)
        } finally {
            driver.close()
        }
    }
}

private fun testTrack(id: String) = Track(
    id = TrackId(id),
    title = "Track",
    artistName = "Artist",
    albumTitle = null,
    durationSeconds = null,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
)
