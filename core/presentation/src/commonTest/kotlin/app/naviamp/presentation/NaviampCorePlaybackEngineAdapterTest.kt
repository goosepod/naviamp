package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.NetworkCertificateVerificationPlaybackEngine
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.lyricsLoadingStatus
import app.naviamp.domain.TrackId
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.AudioCodec
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
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.DownloadedTrackPlayback
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveformAnalyzer
import app.naviamp.domain.waveform.AudioWaveformService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCorePlaybackEngineAdapterTest {
    @Test
    fun downloadedPlaybackPublishesItsEffectiveQualityAndPreparesDownloadedNext() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine().apply {
            emittedProgress = PlaybackProgress(173.0, 180.0)
        }
        val downloadedQuality = StreamQuality.Transcoded(AudioCodec.Opus, 128)
        val audioAssets = object : PlaybackAudioAssetRepository {
            override suspend fun downloadedAudio(sourceId: String, trackId: TrackId) =
                PlaybackLocalAudio(
                    path = "/downloads/${trackId.value}.ogg",
                    uri = "file:///downloads/${trackId.value}.ogg",
                    quality = downloadedQuality,
                )

            override suspend fun downloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) =
                downloadedAudio(sourceId, trackId)

            override suspend fun cachedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) = null
        }
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = {
                PlaybackSettings(
                    gaplessEnabled = true,
                    downloadedTrackPlayback = DownloadedTrackPlayback.PreferDownloaded,
                )
            },
            activeSourceId = { "source" },
            audioAssets = audioAssets,
        )
        val next = provider.track.copy(id = TrackId("next"), title = "Next")

        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track, next), 0), 0)
        advanceUntilIdle()

        assertEquals("file:///downloads/core-track.ogg", engine.request?.url)
        assertEquals("file:///downloads/next.ogg", engine.preparedRequest?.url)
        assertEquals(PlaybackSource.DownloadedFile, adapter.playbackSource)
        assertEquals(downloadedQuality, adapter.playbackQuality)
    }

    @Test
    fun providerCertificatePolicyIsAppliedBeforeNativeStreamPlayback() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine().apply { recordCertificatePolicy = true }
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
            verifyProviderNetworkCertificates = { false },
        )

        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track), 0), 0)
        advanceUntilIdle()

        assertEquals(listOf("verify:false", "play:core-track"), engine.events.takeLast(2))
    }

    @Test
    fun nativeVisualizerSamplingRunsOnlyWhileSharedDisplayRequestsFrames() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
        )
        val frames = mutableListOf<PlaybackVisualizerFrame?>()
        adapter.attach(object : NaviampCorePlaybackObserver {
            override fun onStateChanged(state: PlaybackState) = Unit
            override fun onProgressChanged(progress: PlaybackProgress) = Unit
            override fun onMetadataChanged(metadata: PlaybackStreamMetadata) = Unit
            override fun onVisualizerFrameChanged(frame: PlaybackVisualizerFrame?) { frames += frame }
        })

        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track), 0), 0)
        advanceUntilIdle()
        assertEquals(0, engine.visualizerReads)

        adapter.setVisualizerFramesEnabled(true)
        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track), 0), 0)
        advanceUntilIdle()
        assertEquals(1, engine.visualizerReads)
        assertEquals(listOf<PlaybackVisualizerFrame?>(PlaybackVisualizerFrame(listOf(0.5f), 1L)), frames)

        adapter.setVisualizerFramesEnabled(false)
        assertEquals(listOf(PlaybackVisualizerFrame(listOf(0.5f), 1L), null), frames)
    }

    @Test
    fun startingPlaybackPrefetchesTheConfiguredUpcomingQueueDepth() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine()
        val cachedTrackIds = mutableListOf<String>()
        val preparedSidecarIds = mutableListOf<String>()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
            activeSourceId = { "source" },
            cacheSettings = { CacheSettings(audioCachingEnabled = true, audioPrefetchDepth = 2) },
            cacheAudio = { _, _, track, _ ->
                cachedTrackIds += track.id.value
                PlaybackLocalAudio("/cache/${track.id.value}", "file:///cache/${track.id.value}")
            },
            preparePrefetchedSidecars = { _, _, track, _, _ ->
                preparedSidecarIds += track.id.value
                app.naviamp.domain.playback.PlaybackSidecarPrepResult()
            },
        )
        val nextOne = provider.track.copy(id = TrackId("next-1"), title = "Next One")
        val nextTwo = provider.track.copy(id = TrackId("next-2"), title = "Next Two")
        val later = provider.track.copy(id = TrackId("later"), title = "Later")

        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track, nextOne, nextTwo, later), 0), 0)
        advanceUntilIdle()

        assertEquals(listOf("next-1", "next-2"), cachedTrackIds)
        assertEquals(listOf("next-1", "next-2"), preparedSidecarIds)
    }

    @Test
    fun replacingThePlaybackQueueCancelsObsoletePrefetchSidecars() = runTest {
        val provider = FakeCoreMediaProvider()
        val cachedTrackIds = mutableListOf<String>()
        val preparedSidecarIds = mutableListOf<String>()
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = RecordingPlaybackEngine(),
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
            activeSourceId = { "source" },
            cacheSettings = { CacheSettings(audioCachingEnabled = true, audioPrefetchDepth = 1) },
            cacheAudio = { _, _, track, _ ->
                cachedTrackIds += track.id.value
                if (track.id.value == "obsolete-next") awaitCancellation()
                PlaybackLocalAudio("/cache/${track.id.value}", "file:///cache/${track.id.value}")
            },
            preparePrefetchedSidecars = { _, _, track, _, _ ->
                preparedSidecarIds += track.id.value
                app.naviamp.domain.playback.PlaybackSidecarPrepResult()
            },
        )
        val obsoleteNext = provider.track.copy(id = TrackId("obsolete-next"), title = "Obsolete")
        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track, obsoleteNext), 0), 0)
        runCurrent()

        val replacement = provider.track.copy(id = TrackId("replacement"), title = "Replacement")
        val replacementNext = provider.track.copy(id = TrackId("replacement-next"), title = "Replacement Next")
        adapter.playQueueSelection(PlaybackQueue(listOf(replacement, replacementNext), 0), 0)
        advanceUntilIdle()

        assertEquals(listOf("obsolete-next", "replacement-next"), cachedTrackIds)
        assertEquals(listOf("replacement-next"), preparedSidecarIds)
    }

    @Test
    fun playbackUsesAnAlreadyPrefetchedFileInsteadOfRequestingTheServerAgain() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine()
        val cached = PlaybackLocalAudio("/cache/core-track.flac", "file:///cache/core-track.flac")
        val audioAssets = object : PlaybackAudioAssetRepository {
            override suspend fun downloadedAudio(sourceId: String, trackId: TrackId) = null
            override suspend fun downloadedAudio(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
            ) = null

            override suspend fun cachedAudio(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
            ) = cached
        }
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { PlaybackSettings() },
            activeSourceId = { "source" },
            cacheSettings = { CacheSettings(audioCachingEnabled = true) },
            audioAssets = audioAssets,
        )

        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track), 0), 0)
        advanceUntilIdle()

        assertEquals("file:///cache/core-track.flac", engine.request?.url)
    }

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
    fun changingTransitionSettingsReplacesPreparedNextWithoutStartingANewQueue() = runTest {
        val provider = FakeCoreMediaProvider()
        val engine = RecordingPlaybackEngine().apply {
            emittedProgress = PlaybackProgress(173.0, 180.0)
        }
        var playbackSettings = PlaybackSettings(gaplessEnabled = true)
        val adapter = NaviampCorePlaybackEngineAdapter(
            scope = this,
            engine = engine,
            providerSource = NaviampCoreMediaProviderSource { provider },
            settings = { playbackSettings },
        )
        val next = provider.track.copy(id = TrackId("next"), title = "Next")

        adapter.playQueueSelection(PlaybackQueue(listOf(provider.track, next), 0), 0)
        advanceUntilIdle()
        assertEquals("next", engine.preparedRequest?.mediaId)

        playbackSettings = PlaybackSettings(gaplessEnabled = false, crossfadeDurationSeconds = 0)
        engine.emitProgress(PlaybackProgress(173.0, 180.0))
        advanceUntilIdle()
        assertEquals(null, engine.preparedRequest)

        playbackSettings = PlaybackSettings(gaplessEnabled = false, crossfadeDurationSeconds = 8)
        engine.emitProgress(PlaybackProgress(173.0, 180.0))
        advanceUntilIdle()

        assertEquals("next", engine.preparedRequest?.mediaId)
        assertEquals(
            listOf("play:core-track", "prepare:next", "clear", "clear", "prepare:next"),
            engine.events,
        )
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
        val lyricSourceIds = mutableListOf<String>()
        val lyricsRepository = object : LyricsSidecarRepository {
            override suspend fun providerLyrics(sourceId: String, provider: MediaProvider, trackId: TrackId): Lyrics {
                lyricSourceIds += sourceId
                kotlinx.coroutines.delay(200L)
                return expectedLyrics
            }
            override suspend fun cacheEmbeddedLyrics(sourceId: String, trackId: TrackId, lyrics: Lyrics) = lyrics
            override suspend fun lrclibLyrics(sourceId: String, track: app.naviamp.domain.Track) = null
        }
        var savedOffset = 125
        val offsetSourceIds = mutableListOf<String>()
        val offsets = object : LyricsOffsetRepository {
            override fun lyricsOffsetMillis(sourceId: String, trackId: TrackId): Int {
                offsetSourceIds += sourceId
                return savedOffset
            }
            override fun saveLyricsOffsetMillis(sourceId: String, trackId: TrackId, offsetMillis: Int) {
                offsetSourceIds += sourceId
                savedOffset = offsetMillis
            }
        }
        val waveformSourceIds = mutableListOf<String>()
        val waveformQualities = mutableListOf<StreamQuality>()
        val waveformRepository = object : AudioWaveformStorageRepository {
            override suspend fun cachedAudioWaveform(
                sourceId: String,
                trackId: TrackId,
                quality: StreamQuality,
                bucketCount: Int,
            ): AudioWaveform? {
                waveformSourceIds += sourceId
                waveformQualities += quality
                return null
            }

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
            sourceId = { "saved-source" },
            waveformService = AudioWaveformService(
                waveformRepository = waveformRepository,
                audioAssets = audioAssets,
                analyzer = object : AudioWaveformAnalyzer {
                    override suspend fun analyze(source: AudioWaveformAnalysisSource) = null
                },
            ),
            playbackSettings = { PlaybackSettings(lrclibLyricsEnabled = true) },
            audioCachingEnabled = { true },
            isMobileData = { true },
            audioMetadataSidecarService = metadata,
            lyricsSidecarService = LyricsSidecarService(lyricsRepository, audioAssets, metadata),
            lyricsOffsetController = LyricsOffsetController(offsets),
        )

        sidecars.loadForTrack(provider.track)
        assertEquals(
            listOf(AudioTag("GENRE", "Electronic"), AudioTag("LABEL", "Core Records")),
            sidecars.snapshot().audioTags,
        )
        assertEquals(listOf("saved-source", "saved-source"), waveformSourceIds)
        assertEquals(
            listOf<StreamQuality>(
                StreamQuality.Transcoded(app.naviamp.domain.AudioCodec.Opus, 192),
                StreamQuality.Transcoded(app.naviamp.domain.AudioCodec.Opus, 192),
            ),
            waveformQualities,
        )

        val lyricsLoad = launch { sidecars.loadLyrics(provider.track) }
        runCurrent()
        assertEquals(null, sidecars.snapshot().lyricsStatus)
        advanceTimeBy(151L)
        runCurrent()
        assertEquals(lyricsLoadingStatus(onlineLyricsEnabled = true), sidecars.snapshot().lyricsStatus)
        lyricsLoad.join()
        assertEquals("Core lyric", sidecars.snapshot().lyrics?.lines?.single()?.text)
        assertEquals(125, sidecars.snapshot().lyrics?.offsetMillis)
        assertEquals(null, sidecars.snapshot().lyricsStatus)
        assertEquals(listOf("saved-source"), lyricSourceIds)
        assertEquals(listOf("saved-source"), offsetSourceIds)

        sidecars.changeLyricsOffset(provider.track, 375)
        assertEquals(375, savedOffset)
        assertEquals(listOf("saved-source", "saved-source"), offsetSourceIds)
        assertEquals(375, sidecars.snapshot().lyrics?.offsetMillis)
    }
}

private class RecordingPlaybackEngine :
    PlaybackEngine,
    QueueAwarePlaybackEngine,
    VisualizerPlaybackEngine,
    NetworkCertificateVerificationPlaybackEngine {
    override val name = "Recording"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsGapless = true
    override val supportsCrossfade = true
    override val supportsReplayGain = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = true
    override val supportsVisualizer = true
    var request: PlaybackRequest? = null
    var preparedRequest: PlaybackRequest? = null
    var appliedVolume = -1
    var emittedProgress = PlaybackProgress(12.0, 180.0)
    private var progressCallback: ((PlaybackProgress) -> Unit)? = null
    var visualizerReads = 0
    var recordCertificatePolicy = false
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
        progressCallback = onProgressChanged
        onProgressChanged(emittedProgress)
        onMetadataChanged(PlaybackStreamMetadata(title = "Core Stream"))
    }

    override fun pause() = Unit
    override fun resume() = Unit
    override fun seek(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) { appliedVolume = percent }
    override fun stop() = Unit
    override fun setNetworkCertificateVerification(enabled: Boolean) {
        if (recordCertificatePolicy) events += "verify:$enabled"
    }
    override fun visualizerFrame(): PlaybackVisualizerFrame {
        visualizerReads += 1
        return PlaybackVisualizerFrame(listOf(0.5f), 1L)
    }
    override fun setCrossfadeDuration(seconds: Int) = Unit
    override fun prepareNext(request: PlaybackRequest) {
        preparedRequest = request
        events += "prepare:${request.mediaId}"
    }
    override fun clearPreparedNext() {
        preparedRequest = null
        events += "clear"
    }

    fun emitProgress(progress: PlaybackProgress) {
        progressCallback?.invoke(progress)
    }
}
