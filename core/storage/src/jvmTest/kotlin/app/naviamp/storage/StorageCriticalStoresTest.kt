package app.naviamp.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.radio.RadioArtistRunMode
import app.naviamp.domain.radio.RadioArtistSpread
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioFamiliarity
import app.naviamp.domain.radio.RadioTuningSettings
import app.naviamp.domain.waveform.AudioWaveform
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageCriticalStoresTest {
    @Test
    fun libraryIndexRoundTripsSearchesAndClearsPortableMetadata() = withStorage { fixture ->
        val store = fixture.library
        val artist = Artist(ArtistId("artist"), "Björk")
        val album = Album(AlbumId("album"), "Debut", "Björk", "cover", null, 1993)
        val track = testTrack(
            id = "track",
            title = "Human Behaviour",
            artistId = artist.id,
            albumId = album.id,
            genres = listOf("Electronic", "Alternative"),
            playCount = 9,
            lastPlayedAt = "2026-08-12T10:00:00Z",
        )

        store.upsertLibraryArtists(fixture.sourceId, listOf(artist))
        store.upsertLibraryAlbums(fixture.sourceId, listOf(album))
        store.upsertLibraryTracks(fixture.sourceId, listOf(track))

        assertEquals(track.copy(albumReleaseYear = null), store.librarySnapshot(fixture.sourceId, 10, 0).tracks.single())
        assertEquals("track", store.searchLibrary(fixture.sourceId, "behav", 10, 0).tracks.single().id.value)
        assertEquals("track", store.recentlyPlayedLibraryTracks(fixture.sourceId, 10).single().id.value)
        assertEquals(listOf(1993), store.libraryAlbumYears(fixture.sourceId).map { it.year })
        assertEquals(1L, store.libraryIndexStats(fixture.sourceId).trackCount)

        store.clearLibraryData(fixture.sourceId)
        assertEquals(0L, store.libraryIndexStats(fixture.sourceId).trackCount)
    }

    @Test
    fun playbackHistoryPreservesMetadataOrderingAndDuplicatePlays() = withStorage { fixture ->
        val history = StoragePlaybackHistoryStore(fixture.queries)
        val track = testTrack(id = "history", title = "History", playCount = null, lastPlayedAt = null)

        history.recordPlaybackHistory(fixture.sourceId, track, 100L)
        history.recordPlaybackHistory(fixture.sourceId, track.copy(title = "History Again"), 200L)

        val rows = history.playbackHistory(fixture.sourceId, 10)
        assertEquals(listOf(200L, 100L), rows.map { it.playedAtEpochMillis })
        assertEquals("History Again", rows.first().track.title)
        assertEquals("flac", rows.first().track.audioInfo?.codec)

        history.clear()
        assertTrue(history.playbackHistory(fixture.sourceId, 10).isEmpty())
    }

    @Test
    fun waveformCacheHonorsBucketCountTouchesAndEvictsOldestRows() = runBlocking {
        withStorage { fixture ->
            var now = 10L
            val store = StorageAudioWaveformStore(
                fixture.queries,
                Json,
                nowMillis = { now++ },
                maxAudioWaveformCacheBytes = 10L,
            )
            val first = AudioWaveform(listOf(0.1f, 0.2f))
            val second = AudioWaveform(listOf(0.3f, 0.4f))

            store.storeAudioWaveform(fixture.sourceId, TrackId("one"), StreamQuality.Original, null, first)
            assertNull(store.cachedAudioWaveform(fixture.sourceId, TrackId("one"), StreamQuality.Original, 3))
            assertEquals(first, store.cachedAudioWaveform(fixture.sourceId, TrackId("one"), StreamQuality.Original, 2))

            store.storeAudioWaveform(fixture.sourceId, TrackId("two"), StreamQuality.Original, null, second)
            assertNull(store.cachedAudioWaveform(fixture.sourceId, TrackId("one"), StreamQuality.Original, 2))
            assertEquals(second, store.cachedAudioWaveform(fixture.sourceId, TrackId("two"), StreamQuality.Original, 2))
            assertEquals(1L, fixture.queries.audioWaveformCacheCount().executeAsOne())
            assertTrue(fixture.queries.audioWaveformCacheSize().executeAsOne() <= 10L)
        }
    }

    @Test
    fun lyricsOffsetsResponsesSidecarsAndPendingActionsRoundTrip() = withStorage { fixture ->
        val lyrics = StorageLyricsSidecarStore(fixture.queries)
        val offsets = StorageLyricsOffsetStore(fixture.queries) { 50L }
        val responses = StorageProviderResponseStore(fixture.queries)
        val sidecars = StorageSidecarStatusStore(fixture.queries)
        val pending = StoragePendingProviderActionStore(fixture.queries) { 60L }

        lyrics.upsertCachedLyrics(
            fixture.sourceId, "track", "Embedded", true, "[]", "Artist", "Title", "en",
            125L, 2L, 1L, 1L,
        )
        lyrics.upsertCachedOnlineLyrics(
            fixture.sourceId, "track", "lrclib", "Lrclib", false, "[]", null, null, null,
            0L, 2L, 1L, 1L,
        )
        offsets.saveLyricsOffsetMillis(fixture.sourceId, TrackId("track"), 375)
        responses.upsertResponse("key", "navidrome", "album", "one", "{}", 1L, 1L)
        sidecars.upsertSidecarStatus(fixture.sourceId, "track", "original", "lyrics", "failed", 2L, "error", 3L)
        pending.enqueuePendingProviderAction(fixture.sourceId, "favorite", "track", true, null, true)

        assertEquals(125L, lyrics.cachedLyrics(fixture.sourceId, "track")?.offsetMillis)
        assertEquals("Lrclib", lyrics.cachedOnlineLyrics(fixture.sourceId, "track", "lrclib")?.lyricSource)
        assertEquals(375, offsets.lyricsOffsetMillis(fixture.sourceId, TrackId("track")))
        assertEquals("{}", responses.cachedResponse("key"))
        assertEquals(
            "failed",
            fixture.queries.selectCachedSidecarStatus(fixture.sourceId, "track", "original").executeAsOne().status,
        )

        val action = pending.pendingProviderActions(fixture.sourceId, 10).single()
        pending.markPendingProviderActionFailed(action.id, "offline")
        val failed = pending.pendingProviderActions(fixture.sourceId, 10).single()
        assertEquals(1L, failed.attemptCount)
        assertEquals("offline", failed.lastError)
    }

    @Test
    fun radioDjPresetsNormalizePreserveOrderAndDelete() = withStorage { fixture ->
        val store = StorageRadioDjPresetStore(fixture.queries) { 100L }
        val broad = RadioDjPreset(
            id = "broad",
            name = " Broad ",
            tuning = RadioTuningSettings(
                familiarity = RadioFamiliarity.DeepCuts,
                artistSpread = RadioArtistSpread.Broad,
                sameDecadeOnly = true,
                artistRunMode = RadioArtistRunMode.ArtistBlocks,
                sameArtistRunLength = 99,
                otherArtistRunLength = -1,
            ),
        )
        store.replaceRadioDjPresets(listOf(broad, RadioDjPreset("balanced", "Balanced")))

        val stored = store.radioDjPresets()
        assertEquals(listOf("broad", "balanced"), stored.map { it.id })
        assertEquals("Broad", stored.first().name)
        assertTrue(stored.first().tuning.sameArtistRunLength < 99)
        assertTrue(stored.first().tuning.otherArtistRunLength > -1)

        store.deleteRadioDjPreset("broad")
        assertEquals(listOf("balanced"), store.radioDjPresets().map { it.id })
    }
}

private inline fun <T> withStorage(block: (CriticalStorageFixture) -> T): T {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    try {
        NaviampStorageDatabase.Schema.create(driver)
        val queries = NaviampStorageDatabase(driver).naviampStorageQueries
        val mediaSources = StorageMediaSourceStore(queries, nowMillis = { 1L })
        val sourceId = mediaSources.upsertProviderMediaSource(
            ProviderMediaSourceConnection("Server", "https://example.test", "user", "token", "salt"),
            cacheNamespace = "cache",
            providerId = "navidrome",
        ).id
        return block(
            CriticalStorageFixture(
                queries = queries,
                sourceId = sourceId,
                library = StorageLibraryIndexStore(queries, mediaSources, nowMillis = { 2L }),
            ),
        )
    } finally {
        driver.close()
    }
}

private data class CriticalStorageFixture(
    val queries: NaviampStorageQueries,
    val sourceId: String,
    val library: StorageLibraryIndexStore,
)

private fun testTrack(
    id: String,
    title: String,
    artistId: ArtistId? = ArtistId("artist"),
    albumId: AlbumId? = AlbumId("album"),
    genres: List<String> = listOf("Electronic"),
    playCount: Int? = 2,
    lastPlayedAt: String? = "2026-08-12T10:00:00Z",
): Track = Track(
    id = TrackId(id),
    title = title,
    artistId = artistId,
    artistName = "Björk",
    albumId = albumId,
    albumTitle = "Debut",
    albumReleaseYear = 1993,
    durationSeconds = 240,
    coverArtId = "cover",
    audioInfo = AudioInfo("flac", 900, "audio/flac", 24, 96_000),
    replayGain = null,
    genres = genres,
    playCount = playCount,
    lastPlayedAtIso8601 = lastPlayedAt,
)
