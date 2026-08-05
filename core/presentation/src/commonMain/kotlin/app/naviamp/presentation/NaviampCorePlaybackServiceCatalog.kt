package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.audio.AudioTagReader
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.lyrics.LyricsOffsetController
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.PlaybackSidecarService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.effectiveLyricsTimingPreference
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.waveform.AudioWaveformAnalyzer
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.ui.NaviampVisualizer
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Builds Core's complete playback and sidecar family from native audio/file effects.
 *
 * Hosts own the engine lifetime and provide native analyzers/readers. Core owns settings policy,
 * queue/prefetch orchestration, sidecar ordering, and the now-playing presentation source.
 */
fun naviampCorePlaybackServiceCatalog(
    scope: CoroutineScope,
    engine: PlaybackEngine,
    providerSource: NaviampCoreMediaProviderSource,
    initialPlaybackSettings: PlaybackSettings,
    persistPlaybackSettings: (PlaybackSettings) -> Unit,
    cacheSettings: () -> CacheSettings,
    isMobileData: () -> Boolean = { false },
    activeSourceId: () -> String?,
    verifyProviderNetworkCertificates: () -> Boolean = { true },
    audioAssets: PlaybackAudioAssetRepository,
    cacheAudio: suspend (String, MediaProvider, Track, StreamQuality) -> PlaybackLocalAudio?,
    waveformRepository: AudioWaveformStorageRepository,
    waveformAnalyzer: AudioWaveformAnalyzer,
    audioTagReader: AudioTagReader,
    lyricsRepository: LyricsSidecarRepository,
    lyricsOffsetRepository: LyricsOffsetRepository,
    sidecarStatusRepository: SidecarStatusRepository,
    playbackSessionRepository: PlaybackSessionRepository,
    saveVisualizerSettings: (VisualizerSettings) -> Unit,
    prepareWaveformAnalysis: suspend () -> Unit = {},
    waveformWorkContext: CoroutineContext = EmptyCoroutineContext,
): NaviampCorePlaybackServices {
    val settings = NaviampCorePlaybackEngineSettings(
        engine = engine,
        initial = initialPlaybackSettings,
        persist = persistPlaybackSettings,
    )
    val waveformService = AudioWaveformService(
        waveformRepository = waveformRepository,
        audioAssets = audioAssets,
        analyzer = waveformAnalyzer,
        waveformsEnabled = { cacheSettings().waveformsEnabled },
        waveformBucketCount = { cacheSettings().waveformBucketCount },
        cacheAudioBeforeAnalysis = { true },
        prepareAnalysis = prepareWaveformAnalysis,
        workContext = waveformWorkContext,
        cacheAudioForWaveform = cacheAudio,
    )
    val metadataService = AudioMetadataSidecarService(audioAssets, audioTagReader)
    val lyricsService = LyricsSidecarService(lyricsRepository, audioAssets, metadataService)
    val sidecarService = PlaybackSidecarService(
        waveformService = waveformService,
        lyricsSidecarService = lyricsService,
        sidecarStatusRepository = sidecarStatusRepository,
    )
    val effects = NaviampCorePlaybackEngineAdapter(
        scope = scope,
        engine = engine,
        providerSource = providerSource,
        settings = settings::current,
        isMobileData = isMobileData,
        activeSourceId = activeSourceId,
        verifyProviderNetworkCertificates = verifyProviderNetworkCertificates,
        cacheSettings = cacheSettings,
        audioAssets = audioAssets,
        cacheAudio = cacheAudio,
        preparePrefetchedSidecars = { sourceId, provider, track, quality, _ ->
            val playback = settings.current()
            sidecarService.prepareAll(
                sourceId = sourceId,
                provider = provider,
                track = track,
                quality = quality,
                audioCachingEnabled = cacheSettings().audioCachingEnabled,
                onlineLyricsEnabled = playback.lrclibLyricsEnabled,
                timingPreference = playback.effectiveLyricsTimingPreference(),
                lyricsSearchOrder = playback.lyricsSearchOrder,
                includeLyrics = true,
            )
        },
    )
    return NaviampCorePlaybackServices(
        effects = effects,
        settings = settings,
        sidecars = NaviampCoreProviderNowPlayingSidecars(
            providerSource = providerSource,
            sourceId = activeSourceId,
            waveformService = waveformService,
            playbackSettings = settings::current,
            audioCachingEnabled = { cacheSettings().audioCachingEnabled },
            isMobileData = isMobileData,
            audioMetadataSidecarService = metadataService,
            lyricsSidecarService = lyricsService,
            lyricsOffsetController = LyricsOffsetController(lyricsOffsetRepository),
        ),
        visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
            override fun save(visualizer: NaviampVisualizer) {
                saveVisualizerSettings(VisualizerSettings(visualizer.name))
            }
        },
        sessions = NaviampPlaybackSessionController(playbackSessionRepository),
    )
}
