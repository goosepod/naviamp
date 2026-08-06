package app.naviamp.domain.lyrics

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.LyricLine
import app.naviamp.domain.LyricCue
import app.naviamp.domain.LyricCueLine
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
import app.naviamp.domain.settings.LyricsTimingPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
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
            timingPreference = LyricsTimingPreference.LineSynced,
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
            timingPreference = LyricsTimingPreference.LineSynced,
        )

        assertSame(onlineLyrics, result.lyrics)
        assertEquals(listOf("source:track"), repository.onlineRequests)
    }

    @Test
    fun completedPreferSyncedFallbackSatisfiesForegroundWithoutRepeatingOnlineLookup() = runTest {
        val providerLyrics = lyrics(LyricsSource.Provider, synced = false, text = "Provider")
        val repository = RecordingLyricsRepository(providerLyrics = providerLyrics)
        val service = service(repository = repository)

        val prefetched = service.loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
            timingPreference = LyricsTimingPreference.LineSynced,
            searchOrder = listOf(
                LyricsSourcePreference.Provider,
                LyricsSourcePreference.Online,
            ),
        )
        val foreground = service.loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
            timingPreference = LyricsTimingPreference.LineSynced,
            searchOrder = listOf(
                LyricsSourcePreference.Provider,
                LyricsSourcePreference.Online,
            ),
        )

        assertSame(providerLyrics, prefetched.lyrics)
        assertSame(prefetched, foreground)
        assertEquals(listOf("source:track"), repository.providerRequests)
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
                LyricsSourcePreference.Online,
                LyricsSourcePreference.Provider,
                LyricsSourcePreference.Embedded,
            ),
        )

        assertSame(onlineLyrics, result.lyrics)
        assertEquals(emptyList(), repository.providerRequests)
    }

    @Test
    fun cachedWordSyncedLyricsSatisfyLinePreferenceBeforeAnyLookupAndAreProjected() = runTest {
        val cached = Lyrics(
            source = LyricsSource.Musixmatch,
            synced = true,
            lines = listOf(LyricLine(1_000, "Two words")),
            cueLines = listOf(
                LyricCueLine(
                    lineIndex = 0,
                    startMillis = 1_000,
                    endMillis = 2_000,
                    text = "Two words",
                    cues = listOf(
                        LyricCue(1_000, 1_500, "Two", 0, 2),
                        LyricCue(1_500, 2_000, " words", 3, 8),
                    ),
                ),
            ),
        )
        val onlineProvider = StubOnlineLyricsProvider(null, null)
        val repository = RecordingLyricsRepository(
            providers = listOf(onlineProvider),
            cachedOnlineLyrics = mapOf(onlineProvider.id to cached),
        )

        val result = service(repository).loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
            timingPreference = LyricsTimingPreference.LineSynced,
        )

        assertEquals(LyricsTiming.LineSynced, result.lyrics?.timing)
        assertEquals(emptyList(), result.lyrics?.cueLines)
        assertTrue(cached.hasKaraokeCues)
        assertEquals(emptyList(), repository.providerRequests)
        assertEquals(emptyList(), repository.onlineRequests)
    }

    @Test
    fun plainPreferenceRemovesAllTimingFromRicherCachedLyrics() = runTest {
        val cached = Lyrics(
            source = LyricsSource.Musixmatch,
            synced = true,
            lines = listOf(LyricLine(1_000, "Two words")),
            cueLines = listOf(
                LyricCueLine(
                    lineIndex = 0,
                    startMillis = 1_000,
                    endMillis = 2_000,
                    text = "Two words",
                    cues = listOf(LyricCue(1_000, 2_000, "Two words", 0, 8)),
                ),
            ),
        )
        val onlineProvider = StubOnlineLyricsProvider(null, null)
        val repository = RecordingLyricsRepository(
            providers = listOf(onlineProvider),
            cachedOnlineLyrics = mapOf(onlineProvider.id to cached),
        )

        val result = service(repository).loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
            timingPreference = LyricsTimingPreference.Plain,
        )

        assertEquals(LyricsTiming.Plain, result.lyrics?.timing)
        assertEquals(null, result.lyrics?.lines?.single()?.startMillis)
        assertEquals(emptyList(), result.lyrics?.cueLines)
        assertEquals(emptyList(), repository.onlineRequests)
        assertTrue(cached.hasKaraokeCues)
    }

    @Test
    fun onlineCatalogTriesWordCapableProviderFirstForWordPreference() = runTest {
        val requests = mutableListOf<String>()
        val lineProvider = RecordingOnlineProvider(
            id = "line",
            capabilities = setOf(LyricsTiming.Plain, LyricsTiming.LineSynced),
            result = lyrics(LyricsSource.Lrclib, synced = true, text = "Line"),
            requests = requests,
        )
        val wordLyrics = Lyrics(
            source = LyricsSource.Musixmatch,
            synced = true,
            lines = listOf(LyricLine(0, "Word")),
            cueLines = listOf(LyricCueLine(0, 0, 500, "Word", cues = listOf(LyricCue(0, 500, "Word", 0, 3)))),
        )
        val wordProvider = RecordingOnlineProvider(
            id = "word",
            capabilities = LyricsTiming.entries.toSet(),
            result = wordLyrics,
            requests = requests,
        )
        val repository = RecordingLyricsRepository(providers = listOf(lineProvider, wordProvider))

        val result = service(repository).loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
            timingPreference = LyricsTimingPreference.WordSynced,
            searchOrder = listOf(LyricsSourcePreference.Online),
        )

        assertSame(wordLyrics, result.lyrics)
        assertEquals(listOf("word"), requests)
    }

    @Test
    fun wordDownloadPreferenceCanBeDisplayedAsLineSyncedLyrics() = runTest {
        val requests = mutableListOf<String>()
        val wordLyrics = Lyrics(
            source = LyricsSource.Musixmatch,
            synced = true,
            lines = listOf(LyricLine(0, "Two words")),
            cueLines = listOf(
                LyricCueLine(
                    lineIndex = 0,
                    startMillis = 0,
                    endMillis = 1_000,
                    text = "Two words",
                    cues = listOf(
                        LyricCue(0, 500, "Two", 0, 2),
                        LyricCue(500, 1_000, " words", 3, 8),
                    ),
                ),
            ),
        )
        val repository = RecordingLyricsRepository(
            providers = listOf(
                RecordingOnlineProvider(
                    id = "word",
                    capabilities = LyricsTiming.entries.toSet(),
                    result = wordLyrics,
                    requests = requests,
                ),
            ),
        )

        val result = service(repository).loadLyrics(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
            onlineLyricsEnabled = true,
            timingPreference = LyricsTimingPreference.WordSynced,
            displayTimingPreference = LyricsTimingPreference.LineSynced,
            searchOrder = listOf(LyricsSourcePreference.Online),
        )

        assertEquals(listOf("word"), requests)
        assertEquals(LyricsTiming.LineSynced, result.lyrics?.timing)
        assertEquals(LyricsTiming.WordSynced, result.availableTiming)
        assertEquals(emptyList(), result.lyrics?.cueLines)
        assertTrue(wordLyrics.hasKaraokeCues)
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
    providers: List<LyricsProvider>? = null,
    private val cachedLyrics: Lyrics? = null,
    private val cachedOnlineLyrics: Map<String, Lyrics> = emptyMap(),
) : LyricsSidecarRepository {
    override val onlineProviders: List<LyricsProvider> = providers ?: listOf(
        StubOnlineLyricsProvider(onlineLyrics, onlineError),
    )
    val providerRequests = mutableListOf<String>()
    val onlineRequests = mutableListOf<String>()
    val embeddedStores = mutableListOf<String>()

    override suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
        acceptedTimings: Set<LyricsTiming>,
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

    override suspend fun cachedLyrics(sourceId: String, trackId: TrackId): Lyrics? = cachedLyrics

    override suspend fun cachedOnlineLyrics(
        sourceId: String,
        trackId: TrackId,
        providerId: String,
    ): Lyrics? = cachedOnlineLyrics[providerId]

    override suspend fun onlineLyrics(
        sourceId: String,
        track: Track,
        provider: LyricsProvider,
        acceptedTimings: Set<LyricsTiming>,
    ): Lyrics? {
        onlineRequests += "$sourceId:${track.id.value}"
        return provider.lyrics(track)
    }
}

private class RecordingOnlineProvider(
    override val id: String,
    override val capabilities: Set<LyricsTiming>,
    private val result: Lyrics?,
    private val requests: MutableList<String>,
) : LyricsProvider {
    override suspend fun lyrics(track: Track): Lyrics? {
        requests += id
        return result
    }
}

private class StubOnlineLyricsProvider(
    private val result: Lyrics?,
    private val error: Throwable?,
) : LyricsProvider {
    override val id: String = "online"
    override val capabilities: Set<LyricsTiming> = LyricsTiming.entries.toSet()

    override suspend fun lyrics(track: Track): Lyrics? {
        error?.let { throw it }
        return result
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
