package app.naviamp.domain.waveform

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Lyrics
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest

class AudioWaveformServiceTest {
    @Test
    fun cachedWaveformShortCircuitsAnalysis() = runTest {
        val cached = AudioWaveform(listOf(0.1f, 0.2f))
        val repository = RecordingWaveformRepository(cached = cached)
        val analyzer = RecordingWaveformAnalyzer()
        val service = service(repository = repository, analyzer = analyzer)

        val result = service.loadOrCreateWaveform(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
        )

        assertSame(cached, result.waveform)
        assertTrue(result.cachedWaveformAvailable)
        assertFalse(result.generatedWaveformAvailable)
        assertEquals("Cached", result.status(audioCachingEnabled = true))
        assertEquals(0, analyzer.analyzedUrls.size)
        assertEquals(0, repository.stored.size)
    }

    @Test
    fun analyzesLocalAudioAndStoresWaveform() = runTest {
        val generated = AudioWaveform(listOf(0.3f, 0.8f))
        val repository = RecordingWaveformRepository()
        val analyzer = RecordingWaveformAnalyzer(generated)
        val service = service(
            repository = repository,
            analyzer = analyzer,
            audioAssets = RecordingAudioAssets(cached = "cache/song.flac"),
        )

        val result = service.loadOrCreateWaveform(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
        )

        assertSame(generated, result.waveform)
        assertEquals("file://cache/song.flac", analyzer.analyzedUrls.single())
        assertEquals("cache/song.flac", result.localAudio?.path)
        assertEquals(PlaybackSource.CachedFile, result.playbackSource)
        assertEquals("Generated", result.status(audioCachingEnabled = true))
        assertEquals(listOf("source:track:cache/song.flac"), repository.stored)
    }

    @Test
    fun usesProviderStreamWhenNoLocalAudioExists() = runTest {
        val generated = AudioWaveform(listOf(0.4f, 0.9f))
        val repository = RecordingWaveformRepository()
        val analyzer = RecordingWaveformAnalyzer(generated)
        val service = service(
            repository = repository,
            analyzer = analyzer,
            audioAssets = RecordingAudioAssets(),
        )

        val result = service.loadOrCreateWaveform(
            sourceId = null,
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = false,
        )

        assertSame(generated, result.waveform)
        assertNull(result.localAudio)
        assertFalse(result.audioAvailable)
        assertEquals(PlaybackSource.ProviderStreamCacheDisabled, result.playbackSource)
        assertEquals("Generated", result.status(audioCachingEnabled = false))
        assertEquals("https://example.test/stream/track", analyzer.analyzedUrls.single())
        assertEquals(0, repository.stored.size)
    }

    @Test
    fun cachesAudioBeforeAnalyzingWhenLocalAudioIsMissing() = runTest {
        val repository = RecordingWaveformRepository()
        val analyzer = RecordingWaveformAnalyzer(AudioWaveform(listOf(1f)))
        val cachedTracks = mutableListOf<TrackId>()
        val service = service(
            repository = repository,
            analyzer = analyzer,
            audioAssets = RecordingAudioAssets(),
            cacheAudioForWaveform = { _, _, track, _ ->
                cachedTracks += track.id
                localAudio("cache/generated.flac")
            },
        )

        val result = service.loadOrCreateWaveform(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
        )

        assertEquals(listOf(TrackId("track")), cachedTracks)
        assertEquals("file://cache/generated.flac", analyzer.analyzedUrls.single())
        assertEquals(PlaybackSource.CachedFile, result.playbackSource)
        assertEquals(listOf("source:track:cache/generated.flac"), repository.stored)
    }

    @Test
    fun canAnalyzeProviderStreamBeforeAudioCaching() = runTest {
        val generated = AudioWaveform(listOf(0.4f, 0.9f))
        val repository = RecordingWaveformRepository()
        val analyzer = RecordingWaveformAnalyzer(generated)
        val service = service(
            repository = repository,
            analyzer = analyzer,
            audioAssets = RecordingAudioAssets(),
            cacheAudioBeforeAnalysis = { false },
            cacheAudioForWaveform = { _, _, _, _ -> error("Should not cache audio first") },
        )

        val result = service.loadOrCreateWaveform(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
        )

        assertSame(generated, result.waveform)
        assertNull(result.localAudio)
        assertEquals(PlaybackSource.ProviderStream, result.playbackSource)
        assertEquals("https://example.test/stream/track", analyzer.analyzedUrls.single())
        assertEquals(listOf("source:track:null"), repository.stored)
    }

    @Test
    fun doesNotAnalyzeWhenWaveformsAreDisabled() = runTest {
        val repository = RecordingWaveformRepository()
        val analyzer = RecordingWaveformAnalyzer(AudioWaveform(listOf(1f)))
        val service = service(
            repository = repository,
            analyzer = analyzer,
            waveformsEnabled = { false },
        )

        val result = service.loadOrCreateWaveform(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
        )

        assertNull(result.waveform)
        assertEquals(0, analyzer.analyzedUrls.size)
        assertEquals(0, repository.stored.size)
    }

    @Test
    fun passesConfiguredBucketCountToAnalyzerAndCacheLookup() = runTest {
        val repository = RecordingWaveformRepository()
        val analyzer = RecordingWaveformAnalyzer(AudioWaveform(List(250) { 0.5f }))
        val service = service(
            repository = repository,
            analyzer = analyzer,
            waveformBucketCount = { 250 },
            audioAssets = RecordingAudioAssets(cached = "cache/song.flac"),
        )

        service.loadOrCreateWaveform(
            sourceId = "source",
            provider = FakeMediaProvider(),
            track = track(),
            quality = StreamQuality.Original,
            audioCachingEnabled = true,
        )

        assertEquals(listOf(250, 250), repository.requestedBucketCounts)
        assertEquals(listOf(250), analyzer.analyzedBucketCounts)
    }

    @Test
    fun concurrentRequestsShareSingleWaveformAnalysis() = runTest {
        val generated = AudioWaveform(listOf(0.3f, 0.8f))
        val repository = RecordingWaveformRepository()
        val analysisStarted = CompletableDeferred<Unit>()
        val releaseAnalysis = CompletableDeferred<Unit>()
        val analyzer = RecordingWaveformAnalyzer(generated) {
            analysisStarted.complete(Unit)
            releaseAnalysis.await()
        }
        val service = service(
            repository = repository,
            analyzer = analyzer,
            audioAssets = RecordingAudioAssets(cached = "cache/song.flac"),
        )

        val first = async {
            service.loadOrCreateWaveform(
                sourceId = "source",
                provider = FakeMediaProvider(),
                track = track(),
                quality = StreamQuality.Original,
                audioCachingEnabled = true,
            )
        }
        analysisStarted.await()
        val second = async {
            service.loadOrCreateWaveform(
                sourceId = "source",
                provider = FakeMediaProvider(),
                track = track(),
                quality = StreamQuality.Original,
                audioCachingEnabled = true,
            )
        }

        releaseAnalysis.complete(Unit)
        val results = awaitAll(first, second)

        assertEquals(1, analyzer.analyzedUrls.size)
        assertEquals(1, repository.stored.size)
        assertSame(generated, results[0].waveform)
        assertSame(generated, results[1].waveform)
    }

    @Test
    fun activeWaiterRetriesWhenWaveformAnalysisOwnerIsCancelled() = runTest {
        val generated = AudioWaveform(listOf(0.2f, 0.7f))
        val repository = RecordingWaveformRepository()
        val firstAnalysisStarted = CompletableDeferred<Unit>()
        var analysisCount = 0
        val analyzer = RecordingWaveformAnalyzer(generated) {
            analysisCount += 1
            if (analysisCount == 1) {
                firstAnalysisStarted.complete(Unit)
                awaitCancellation()
            }
        }
        val service = service(
            repository = repository,
            analyzer = analyzer,
            audioAssets = RecordingAudioAssets(cached = "cache/song.flac"),
        )

        val owner = async {
            service.loadOrCreateWaveform(
                sourceId = "source",
                provider = FakeMediaProvider(),
                track = track(),
                quality = StreamQuality.Original,
                audioCachingEnabled = true,
            )
        }
        firstAnalysisStarted.await()
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            service.loadOrCreateWaveform(
                sourceId = "source",
                provider = FakeMediaProvider(),
                track = track(),
                quality = StreamQuality.Original,
                audioCachingEnabled = true,
            )
        }

        owner.cancelAndJoin()
        val result = waiter.await()

        assertEquals(2, analysisCount)
        assertSame(generated, result.waveform)
        assertEquals(1, repository.stored.size)
    }

    private fun service(
        repository: RecordingWaveformRepository,
        analyzer: RecordingWaveformAnalyzer,
        audioAssets: PlaybackAudioAssetRepository = RecordingAudioAssets(),
        waveformsEnabled: () -> Boolean = { true },
        waveformBucketCount: () -> Int = { 180 },
        cacheAudioBeforeAnalysis: () -> Boolean = { true },
        cacheAudioForWaveform: suspend (
            sourceId: String,
            provider: MediaProvider,
            track: Track,
            quality: StreamQuality,
        ) -> PlaybackLocalAudio? = { _, _, _, _ -> null },
    ): AudioWaveformService =
        AudioWaveformService(
            waveformRepository = repository,
            audioAssets = audioAssets,
            analyzer = analyzer,
            waveformsEnabled = waveformsEnabled,
            waveformBucketCount = waveformBucketCount,
            cacheAudioBeforeAnalysis = cacheAudioBeforeAnalysis,
            cacheAudioForWaveform = cacheAudioForWaveform,
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
}

private fun localAudio(path: String): PlaybackLocalAudio =
    PlaybackLocalAudio(
        path = path,
        uri = "file://$path",
    )

private class RecordingWaveformRepository(
    private val cached: AudioWaveform? = null,
) : AudioWaveformStorageRepository {
    val stored = mutableListOf<String>()
    val requestedBucketCounts = mutableListOf<Int>()

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        bucketCount: Int,
    ): AudioWaveform? {
        requestedBucketCounts += bucketCount
        return cached
    }

    override suspend fun storeAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFilePath: String?,
        waveform: AudioWaveform,
    ): AudioWaveform {
        stored += "$sourceId:${trackId.value}:$audioFilePath"
        return waveform
    }
}

private class RecordingAudioAssets(
    private val downloaded: String? = null,
    private val cached: String? = null,
) : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(sourceId: String, trackId: TrackId): PlaybackLocalAudio? =
        downloaded?.let(::localAudio)

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = downloaded?.let(::localAudio)

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = cached?.let(::localAudio)
}

private class RecordingWaveformAnalyzer(
    private val waveform: AudioWaveform? = null,
    private val beforeAnalyze: suspend () -> Unit = {},
) : AudioWaveformAnalyzer {
    val analyzedUrls = mutableListOf<String>()
    val analyzedBucketCounts = mutableListOf<Int>()

    override suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform? {
        beforeAnalyze()
        analyzedUrls += source.streamUrl
        analyzedBucketCounts += source.bucketCount
        return waveform
    }
}

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
