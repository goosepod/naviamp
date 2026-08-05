package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.LyricCue
import app.naviamp.domain.LyricCueLine
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LyricsSidecarCacheServiceTest {
    @Test
    fun refreshesLegacyLineOnlyCacheOnceToDiscoverKaraokeCues() = runTest {
        val store = InMemoryLyricsSidecarStore(
            cached = CachedLyricsRow(
                lyricSource = LyricsSource.Provider.name,
                synced = true,
                linesJson = """[{"startMillis":1000,"text":"Old line"}]""",
                displayArtist = null,
                displayTitle = null,
                language = null,
                offsetMillis = 0,
            ),
        )
        val provider = RecordingLyricsProvider(karaokeLyrics())
        val service = LyricsSidecarCacheService(store = store, nowMillis = { 42L })

        val first = service.providerLyrics("source", provider, TrackId("track"))
        val second = service.providerLyrics("source", provider, TrackId("track"))

        assertTrue(first?.hasKaraokeCues == true)
        assertTrue(second?.hasKaraokeCues == true)
        assertEquals(1, provider.lyricsRequests)
        assertEquals(1, store.upserts)
    }

    @Test
    fun preservesLegacyLyricsWhenProviderHasNoReplacementAndMarksThemChecked() = runTest {
        val store = InMemoryLyricsSidecarStore(
            cached = CachedLyricsRow(
                lyricSource = LyricsSource.Provider.name,
                synced = true,
                linesJson = """[{"startMillis":1000,"text":"Keep me"}]""",
                displayArtist = null,
                displayTitle = null,
                language = null,
                offsetMillis = 0,
            ),
        )
        val provider = RecordingLyricsProvider(null)
        val service = LyricsSidecarCacheService(store = store, nowMillis = { 42L })

        val first = service.providerLyrics("source", provider, TrackId("track"))
        val second = service.providerLyrics("source", provider, TrackId("track"))

        assertEquals("Keep me", first?.lines?.single()?.text)
        assertEquals("Keep me", second?.lines?.single()?.text)
        assertEquals(1, provider.lyricsRequests)
        assertEquals(1, store.upserts)
    }

    private fun karaokeLyrics(): Lyrics = Lyrics(
        source = LyricsSource.Provider,
        synced = true,
        lines = listOf(LyricLine(1_000, "New line")),
        cueLines = listOf(
            LyricCueLine(
                lineIndex = 0,
                startMillis = 1_000,
                endMillis = 2_000,
                text = "New line",
                cues = listOf(LyricCue(1_000, 2_000, "New", 0, 2)),
            ),
        ),
    )
}

private class InMemoryLyricsSidecarStore(
    cached: CachedLyricsRow?,
) : LyricsSidecarStore {
    private var providerRow = cached
    var upserts: Int = 0
        private set

    override fun cachedLyrics(sourceId: String, trackId: String): CachedLyricsRow? = providerRow
    override fun touchCachedLyrics(sourceId: String, trackId: String, lastAccessedEpochMillis: Long) = Unit
    override fun upsertCachedLyrics(
        sourceId: String,
        trackId: String,
        lyricSource: String,
        synced: Boolean,
        linesJson: String,
        displayArtist: String?,
        displayTitle: String?,
        language: String?,
        offsetMillis: Long,
        sizeBytes: Long,
        createdAtEpochMillis: Long,
        lastAccessedEpochMillis: Long,
    ) {
        upserts += 1
        providerRow = CachedLyricsRow(
            lyricSource = lyricSource,
            synced = synced,
            linesJson = linesJson,
            displayArtist = displayArtist,
            displayTitle = displayTitle,
            language = language,
            offsetMillis = offsetMillis,
        )
    }

    override fun cachedOnlineLyrics(sourceId: String, trackId: String, onlineProviderId: String): CachedLyricsRow? = null
    override fun touchCachedOnlineLyrics(
        sourceId: String,
        trackId: String,
        onlineProviderId: String,
        lastAccessedEpochMillis: Long,
    ) = Unit
    override fun upsertCachedOnlineLyrics(
        sourceId: String,
        trackId: String,
        onlineProviderId: String,
        lyricSource: String,
        synced: Boolean,
        linesJson: String,
        displayArtist: String?,
        displayTitle: String?,
        language: String?,
        offsetMillis: Long,
        sizeBytes: Long,
        createdAtEpochMillis: Long,
        lastAccessedEpochMillis: Long,
    ) = Unit
}

private class RecordingLyricsProvider(
    private val result: Lyrics?,
) : MediaProvider {
    var lyricsRequests: Int = 0
        private set

    override val id = ProviderId("fake")
    override val displayName = "Fake"
    override val capabilities = ProviderCapabilities(
        supportsStreamingTranscode = true,
        supportsDownloadTranscode = true,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
    )

    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int): List<Artist> = emptyList()
    override suspend fun tracks(limit: Int): List<Track> = emptyList()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun lyrics(trackId: TrackId): Lyrics? {
        lyricsRequests += 1
        return result
    }
    override suspend fun streamUrl(request: StreamRequest) = "https://example.test/stream"
    override fun coverArtUrl(coverArtId: String) = "https://example.test/cover/$coverArtId"
}
