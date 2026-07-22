package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.TrackId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.LyricLine
import app.naviamp.domain.Lyrics
import app.naviamp.domain.LyricsSource
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.audio.AudioTag
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.lyrics.LyricsOffsetController
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveformAnalyzer
import app.naviamp.domain.waveform.AudioWaveformService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCorePlaybackEngineAdapterTest {
    @Test
    fun restoredQueueWaitsForPlayAndResumesAtTheSavedPosition() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
        )

        adapter.restoreQueue(PlaybackQueue(listOf(provider.track), 0), startPositionSeconds = 37.0)
        assertEquals(null, engine.request)

        adapter.startOrRestore()
        advanceUntilIdle()

        assertEquals(37.0, engine.request?.startPositionSeconds)
    }

    @Test
    fun internetRadioUsesTheStationStreamWithoutProviderResolution() = runTest {
        val engine = RecordingPlaybackEngine()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { null },
            settings = { PlaybackSettings() },
        )

        adapter.play(
            InternetRadioStation(
                id = "radio-1",
                name = "Radio One",
                streamUrl = "https://radio.example/live",
            ),
        )
        advanceUntilIdle()

        assertEquals("https://radio.example/live", engine.request?.url)
        assertEquals("internet-radio:radio-1", engine.request?.mediaId)
    }

    @Test
    fun coreResolvesProviderPlaybackAndPublishesNativeObservations() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine()
        val states = mutableListOf<PlaybackState>()
        val progressEvents = mutableListOf<PlaybackProgress>()
        val metadataEvents = mutableListOf<PlaybackStreamMetadata>()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
        )
        adapter.attach(object : NaviampCorePlaybackObserver {
            override fun onStateChanged(state: PlaybackState) { states += state }
            override fun onProgressChanged(progress: PlaybackProgress) { progressEvents += progress }
            override fun onMetadataChanged(metadata: PlaybackStreamMetadata) { metadataEvents += metadata }
        })

        val next = provider.track.copy(id = TrackId("next"), title = "Next")
        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track, next), 0), 0)
        advanceUntilIdle()

        assertEquals("https://example.test/core-track", engine.request?.url)
        assertEquals(listOf<PlaybackState>(PlaybackState.Playing), states)
        assertEquals(12.0, progressEvents.single().positionSeconds)
        assertEquals("Core Stream", metadataEvents.single().title)
        assertEquals(null, engine.preparedRequest)
        assertEquals(listOf("play:core-track"), engine.events)
    }

    @Test
    fun nextTrackIsPreparedOnlyInsideTheSharedTransitionWindow() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine().apply {
            emittedProgress = PlaybackProgress(173.0, 180.0)
        }
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings(gaplessEnabled = true) },
        )
        val next = provider.track.copy(id = TrackId("next"), title = "Next")

        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track, next), 0), 0)
        advanceUntilIdle()

        assertEquals("next", engine.preparedRequest?.mediaId)
        assertEquals(listOf("play:core-track", "prepare:next"), engine.events)
    }

    @Test
    fun manualNextClearsAnActiveCrossfadeAndStartsTheTrackFromItsOwnBeginning() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine().apply {
            emittedProgress = PlaybackProgress(173.0, 180.0)
        }
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings(gaplessEnabled = false, crossfadeDurationSeconds = 8) },
        )
        val next = provider.track.copy(id = TrackId("next"), title = "Next")
        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track, next), 0), 0)
        advanceUntilIdle()

        adapter.applyNavigation(PlaybackQueueNavigationCommand.Next)
        advanceUntilIdle()

        assertEquals("next", engine.request?.mediaId)
        assertEquals(null, engine.preparedRequest)
        assertEquals(
            listOf("clear", "play:core-track", "prepare:next", "clear", "play:next"),
            engine.events,
        )
    }

    @Test
    fun coreAppliesEffectiveSettingsToEveryEngine() {
        val engine = RecordingPlaybackEngine()
        val settings = NaviampCorePlaybackEngineSettings(engine)

        val effective = settings.apply(PlaybackSettings(volumePercent = 140), redownload = false)

        assertEquals(100, effective.volumePercent)
        assertEquals(100, engine.appliedVolume)
    }

    @Test
    fun sharedSidecarLoaderPublishesSonicRelatedTracksAndScores() = runTest {
        val provider = FakeCoreMediaProvider(supportsSonicSimilarity = true)

        val related = loadCoreRelatedTracks(provider, provider.track, sonicSimilarityEnabled = true)

        assertEquals(RelatedTracksSource.SonicSimilarity, related.source)
        assertEquals(listOf("sonic-related"), related.tracks.map { it.id.value })
        assertEquals(0.87, related.similarityByTrackId[TrackId("sonic-related")])
    }

    @Test
    fun sharedSidecarLoaderPublishesTagsLyricsAndPersistentOffsets() = runTest {
        val provider = FakeCoreMediaProvider()
        val localAudio = PlaybackLocalAudio("/music/core.flac", "file:///music/core.flac")
        val audioAssets = object : PlaybackAudioAssetRepository {
            override suspend fun downloadedAudio(sourceId: String, trackId: TrackId) = localAudio
            override suspend fun downloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) = localAudio
            override suspend fun cachedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) = null
        }
        val metadata = AudioMetadataSidecarService(audioAssets) {
            listOf(AudioTag("GENRE", "Electronic"), AudioTag("LABEL", "Core Records"))
        }
        val expectedLyrics = Lyrics(
            source = LyricsSource.Provider,
            synced = false,
            lines = listOf(LyricLine(null, "Core lyric")),
        )
        val lyricsRepository = object : LyricsSidecarRepository {
            override suspend fun providerLyrics(sourceId: String, provider: MediaProvider, trackId: TrackId) = expectedLyrics
            override suspend fun cacheEmbeddedLyrics(sourceId: String, trackId: TrackId, lyrics: Lyrics) = lyrics
            override suspend fun lrclibLyrics(sourceId: String, track: app.naviamp.domain.Track) = null
        }
        var savedOffset = 125
        val offsets = object : LyricsOffsetRepository {
            override fun lyricsOffsetMillis(sourceId: String, trackId: TrackId) = savedOffset
            override fun saveLyricsOffsetMillis(sourceId: String, trackId: TrackId, offsetMillis: Int) {
                savedOffset = offsetMillis
            }
        }
        val waveformRepository = object : AudioWaveformStorageRepository {
            override suspend fun cachedAudioWaveform(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
                bucketCount: Int,
            ): AudioWaveform? = null

            override suspend fun storeAudioWaveform(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
                audioFilePath: String?,
                waveform: AudioWaveform,
            ) = waveform
        }
        val sidecars = NaviampCoreProviderNowPlayingSidecars(
            providerSource = NaviampCoreMediaProviderSource { provider },
            waveformService = AudioWaveformService(
                waveformRepository = waveformRepository,
                audioAssets = audioAssets,
                analyzer = object : AudioWaveformAnalyzer {
                    override suspend fun analyze(source: AudioWaveformAnalysisSource) = null
                },
            ),
            playbackSettings = { PlaybackSettings() },
            audioCachingEnabled = { true },
            audioMetadataSidecarService = metadata,
            lyricsSidecarService = LyricsSidecarService(lyricsRepository, audioAssets, metadata),
            lyricsOffsetController = LyricsOffsetController(offsets),
        )

        sidecars.loadForTrack(provider.track)
        assertEquals(
            listOf(AudioTag("GENRE", "Electronic"), AudioTag("LABEL", "Core Records")),
            sidecars.snapshot().audioTags,
        )

        sidecars.loadLyrics(provider.track)
        assertEquals("Core lyric", sidecars.snapshot().lyrics?.lines?.single()?.text)
        assertEquals(125, sidecars.snapshot().lyrics?.offsetMillis)
        assertEquals(null, sidecars.snapshot().lyricsStatus)

        sidecars.changeLyricsOffset(provider.track, 375)
        assertEquals(375, savedOffset)
        assertEquals(375, sidecars.snapshot().lyrics?.offsetMillis)
    }
}

private class RecordingPlaybackEngine : PlaybackEngine, QueueAwarePlaybackEngine {
    override val name = "Recording"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsGapless = true
    override val supportsCrossfade = true
    override val supportsReplayGain = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = true
    var request: PlaybackRequest? = null
    var preparedRequest: PlaybackRequest? = null
    var appliedVolume = -1
    var emittedProgress = PlaybackProgress(12.0, 180.0)
    val events = mutableListOf<String>()

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        this.request = request
        events += "play:${request.mediaId}"
        onStateChanged(PlaybackState.Playing)
        onProgressChanged(emittedProgress)
        onMetadataChanged(PlaybackStreamMetadata(title = "Core Stream"))
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seek(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) { appliedVolume = percent }
    override fun stop() = Unit
    override fun setCrossfadeDuration(seconds: Int) = Unit
    override fun prepareNext(request: PlaybackRequest) {
        preparedRequest = request
        events += "prepare:${request.mediaId}"
    }
    override fun clearPreparedNext() {
        preparedRequest = null
        events += "clear"
    }
}
