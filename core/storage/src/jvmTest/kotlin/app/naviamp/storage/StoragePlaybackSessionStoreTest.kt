package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
                positionSeconds = 12.5,
            )!!

            store.savePlaybackSession(session, "source")

            assertEquals(session, store.loadPlaybackSession("source"))

            store.savePlaybackSession(null, "source")

            assertNull(store.loadPlaybackSession("source"))
            assertNull(store.loadPlaybackSession(null))
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
