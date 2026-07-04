package app.naviamp.desktop

import app.naviamp.desktop.playback.DesktopPlaybackEngineFactory
import app.naviamp.desktop.playback.DesktopPlaylistEngine
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackSidecarService
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.ProviderArtistPopularTracksClient
import app.naviamp.domain.popular.ProviderSimilarArtistsClient
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.provider.navidrome.NavidromeProvider
import java.nio.file.Path

class DesktopAppDependencies(
    val settingsStore: DesktopSettingsStore = DesktopSettingsStore(),
    val playbackEngine: PlaybackEngine = DesktopPlaybackEngineFactory.createDefault(),
    val storage: DesktopStorageDependencies = DesktopStorageDependencies(),
) {
    var waveformsEnabledProvider: () -> Boolean = { true }
    var waveformBucketCountProvider: () -> Int = { app.naviamp.domain.settings.DefaultWaveformBucketCount }

    val imageCacheRepository: ImageCacheRepository = storage

    val playbackAudioAssets: DesktopPlaybackAudioAssets =
        DesktopPlaybackAudioAssets(storage, storage)

    val audioMetadataSidecarService: AudioMetadataSidecarService =
        AudioMetadataSidecarService(
            playbackAudioAssets = playbackAudioAssets,
            audioTagReader = { localAudio -> DesktopAudioTagReader().read(Path.of(localAudio.path)) },
        )

    val lyricsSidecarService: LyricsSidecarService =
        LyricsSidecarService(
            lyricsRepository = storage,
            playbackAudioAssets = playbackAudioAssets,
            audioMetadataSidecarService = audioMetadataSidecarService,
        )

    val audioWaveformService: AudioWaveformService =
        AudioWaveformService(
            waveformRepository = storage,
            audioAssets = playbackAudioAssets,
            analyzer = DesktopAudioWaveformAnalyzer(),
            waveformsEnabled = { waveformsEnabledProvider() },
            waveformBucketCount = { waveformBucketCountProvider() },
            cacheAudioBeforeAnalysis = { false },
            cacheAudioForWaveform = { sourceId, provider, track, quality ->
                storage.cacheAudioTrack(sourceId, provider, track, quality).path.toPlaybackLocalAudio()
            },
        )

    val playbackSidecarService: PlaybackSidecarService =
        PlaybackSidecarService(
            waveformService = audioWaveformService,
            lyricsSidecarService = lyricsSidecarService,
            sidecarStatusRepository = storage,
        )

    fun popularTracksService(
        sourceIdProvider: () -> String?,
        providerProvider: () -> NavidromeProvider?,
    ): ArtistPopularTracksService =
        ArtistPopularTracksService(
            repository = storage,
            libraryTracksForArtist = { artist, limit ->
                val sourceId = sourceIdProvider().orEmpty()
                storage.libraryTracksForArtist(sourceId, artist.id, limit)
                    .ifEmpty { storage.libraryTracksForArtistName(sourceId, artist.name, limit) }
            },
            client = ProviderArtistPopularTracksClient(providerProvider),
        )

    fun similarArtistsService(
        sourceIdProvider: () -> String?,
        providerProvider: () -> NavidromeProvider?,
    ): SimilarArtistsService =
        SimilarArtistsService(
            libraryArtistsSearch = { query, limit ->
                storage.searchLibrary(sourceIdProvider().orEmpty(), query, limit).artists
            },
            client = ProviderSimilarArtistsClient(providerProvider),
        )

    fun playlistEngine(
        sourceIdProvider: () -> String?,
        audioCachingEnabledProvider: () -> Boolean,
        audioPrefetchDepthProvider: () -> Int,
    ): DesktopPlaylistEngine =
        DesktopPlaylistEngine(
            playbackEngine = playbackEngine,
            sourceIdProvider = sourceIdProvider,
            audioCachingEnabledProvider = audioCachingEnabledProvider,
            audioPrefetchDepthProvider = audioPrefetchDepthProvider,
            audioCacheRepository = storage,
            sidecarService = playbackSidecarService,
            audioMetadataSidecarService = audioMetadataSidecarService,
            playbackAudioAssets = playbackAudioAssets,
        )

    fun librarySync(): DesktopLibrarySync =
        DesktopLibrarySync(
            libraryIndexRepository = storage,
            providerResponseService = ProviderResponseService(storage),
        )
}
