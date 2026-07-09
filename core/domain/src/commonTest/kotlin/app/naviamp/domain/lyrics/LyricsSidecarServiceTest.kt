package app.naviamp.domain.lyrics

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.settings.LyricsSourcePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class LyricsSidecarServiceTest {
    @Test
    fun prefersProviderLyricsBeforeEmbeddedAndOnlineLyrics() = runTest {
        val providerLyrics = lyrics(LyricsSource.Provider, synced = false, text = "Provider")
        val embeddedLyrics = lyrics(LyricsSource.Embedded, synced = false, text = "Embedded")
        val onlineLyrics = lyrics(LyricsSource.Lrclib, synced = true, text = "Online")
        val repository = RecordingLyricsRepository(
            providerLyrics = providerLyrics,
            onlineLyrics = onlineLyrics,
        )
        val service = service(
            repository = repository,
            audioAssets = RecordingAudioAssets(cached = localAudio("song.flac")),
            tags = listOf(AudioTag("Lyrics", embeddedLyrics.lines.single().text)),
        )

        val result = service.loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
        )

        assertSame(providerLyrics, result.lyrics)
        assertEquals(emptyList(), repository.embeddedStores)
        assertEquals(listOf("source:track"), repository.providerRequests)
        assertEquals(emptyList(), repository.onlineRequests)
    }

    @Test
    fun usesEmbeddedLyricsWhenProviderHasNone() = runTest {
        val embeddedLyrics = lyrics(LyricsSource.Embedded, synced = false, text = "Embedded")
        val onlineLyrics = lyrics(LyricsSource.Lrclib, synced = true, text = "Online")
        val repository = RecordingLyricsRepository(onlineLyrics = onlineLyrics)
        val service = service(
            repository = repository,
            audioAssets = RecordingAudioAssets(cached = localAudio("song.flac")),
            tags = listOf(AudioTag("Lyrics", embeddedLyrics.lines.single().text)),
        )

        val result = service.loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
        )

        assertEquals(LyricsSource.Embedded, result.lyrics?.source)
        assertEquals(listOf("source:track:Embedded"), repository.embeddedStores)
        assertEquals(listOf("source:track"), repository.providerRequests)
        assertEquals(emptyList(), repository.onlineRequests)
    }

    @Test
    fun usesOnlineLyricsOnlyWhenProviderAndEmbeddedLyricsAreMissing() = runTest {
        val onlineLyrics = lyrics(LyricsSource.Lrclib, synced = true, text = "Online")
        val repository = RecordingLyricsRepository(onlineLyrics = onlineLyrics)
        val service = service(repository = repository)

        val result = service.loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
        )

        assertSame(onlineLyrics, result.lyrics)
        assertEquals(listOf("source:track"), repository.onlineRequests)
    }

    @Test
    fun preferSyncedKeepsSearchingAndFallsBackToFirstLyricsWhenNoSyncedLyricsExist() = runTest {
        val providerLyrics = lyrics(LyricsSource.Provider, synced = false, text = "Provider")
        val embeddedLyrics = lyrics(LyricsSource.Embedded, synced = false, text = "Embedded")
        val repository = RecordingLyricsRepository(providerLyrics = providerLyrics)
        val service = service(
            repository = repository,
            audioAssets = RecordingAudioAssets(cached = localAudio("song.flac")),
            tags = listOf(AudioTag("Lyrics", embeddedLyrics.lines.single().text)),
        )

        val result = service.loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = false,
            preferSyncedLyrics = true,
        )

        assertSame(providerLyrics, result.lyrics)
        assertEquals(listOf("source:track:Embedded"), repository.embeddedStores)
    }

    @Test
    fun preferSyncedUsesLaterSyncedLyricsBeforeEarlierUnsyncedLyrics() = runTest {
        val providerLyrics = lyrics(LyricsSource.Provider, synced = false, text = "Provider")
        val onlineLyrics = lyrics(LyricsSource.Lrclib, synced = true, text = "Online")
        val repository = RecordingLyricsRepository(providerLyrics = providerLyrics, onlineLyrics = onlineLyrics)
        val service = service(repository = repository)

        val result = service.loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
            preferSyncedLyrics = true,
        )

        assertSame(onlineLyrics, result.lyrics)
        assertEquals(listOf("source:track"), repository.onlineRequests)
    }

    @Test
    fun customSearchOrderControlsFirstSourceWithoutPreferSynced() = runTest {
        val providerLyrics = lyrics(LyricsSource.Provider, synced = false, text = "Provider")
        val onlineLyrics = lyrics(LyricsSource.Lrclib, synced = true, text = "Online")
        val repository = RecordingLyricsRepository(providerLyrics = providerLyrics, onlineLyrics = onlineLyrics)
        val service = service(repository = repository)

        val result = service.loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
            searchOrder = listOf(
                LyricsSourcePreference.Download,
                LyricsSourcePreference.Provider,
                LyricsSourcePreference.Embedded,
            ),
        )

        assertSame(onlineLyrics, result.lyrics)
        assertEquals(emptyList(), repository.providerRequests)
    }

    private fun service(
        repository: RecordingLyricsRepository,
        audioAssets: PlaybackAudioAssetRepository = RecordingAudioAssets(),
        tags: List<AudioTag> = emptyList(),
    ): LyricsSidecarService =
        LyricsSidecarService(
            lyricsRepository = repository,
            playbackAudioAssets = audioAssets,
            audioMetadataSidecarService = AudioMetadataSidecarService(
                playbackAudioAssets = audioAssets,
                audioTagReader = { tags },
            ),
        )
}

private class RecordingLyricsRepository(
    private val providerLyrics: Lyrics? = null,
    private val onlineLyrics: Lyrics? = null,
    private val onlineError: Throwable? = null,
) : LyricsSidecarRepository {
    val providerRequests = mutableListOf<String>()
    val onlineRequests = mutableListOf<String>()
    val embeddedStores = mutableListOf<String>()

    override suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
    ): Lyrics? {
        providerRequests += "$sourceId:${trackId.value}"
        return providerLyrics
    }

    override suspend fun cacheEmbeddedLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ): Lyrics {
        embeddedStores += "$sourceId:${trackId.value}:${lyrics.lines.single().text}"
        return lyrics
    }

    override suspend fun lrclibLyrics(sourceId: String, track: Track): Lyrics? {
        onlineRequests += "$sourceId:${track.id.value}"
        onlineError?.let { throw it }
        return onlineLyrics
    }
}

private class RecordingAudioAssets(
    private val cached: PlaybackLocalAudio? = null,
) : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(sourceId: String, trackId: TrackId): PlaybackLocalAudio? = null

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = null

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = cached
}

private fun localAudio(path: String): PlaybackLocalAudio =
    PlaybackLocalAudio(
        path = path,
        uri = "file://$path",
    )

private fun lyrics(source: LyricsSource, synced: Boolean, text: String): Lyrics =
    Lyrics(
        source = source,
        synced = synced,
        lines = listOf(LyricLine(startMillis = if (synced) 0L else null, text = text)),
    )

private fun track(): Track =
    Track(
        id = TrackId("track"),
        title = "Track",
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )

private class FakeMediaProvider : MediaProvider {
    override val id: ProviderId = ProviderId("fake")
    override val displayName: String = "Fake"
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        supportsStreamingTranscode = true,
        supportsDownloadTranscode = true,
        supportsArtistRadio = false,
        supportsAlbumRadio = false,
        supportsTrackRadio = false,
    )

    override suspend fun validateConnection(): ConnectionValidation = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int): List<Album> = emptyList()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Not used")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Not used")
    override suspend fun artists(limit: Int): List<Artist> = emptyList()
    override suspend fun tracks(limit: Int): List<Track> = emptyList()
    override suspend fun search(query: String, limit: Int): MediaSearchResults = MediaSearchResults()
    override suspend fun lyrics(trackId: TrackId): Lyrics? = null
    override suspend fun streamUrl(request: StreamRequest): String = "https://example.test/stream/${request.trackId.value}"
    override fun coverArtUrl(coverArtId: String): String = "https://example.test/cover/$coverArtId"
}
