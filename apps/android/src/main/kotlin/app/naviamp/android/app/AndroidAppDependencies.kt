package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidAudioTagReader
import app.naviamp.android.playback.AndroidPlaybackRuntime
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.ProviderArtistPopularTracksClient
import app.naviamp.domain.popular.ProviderSimilarArtistsClient
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.radio.InternetRadioStationManager
import app.naviamp.provider.navidrome.NavidromeProvider
import java.io.File

class AndroidAppDependencies(
    context: Context,
    val playbackRuntime: AndroidPlaybackRuntime = AndroidPlaybackRuntime.get(context),
    val storage: AndroidStorageDependencies = AndroidStorageDependencies(context),
    val settingsStore: AndroidSettingsStore = AndroidSettingsStore(context),
) : AutoCloseable {
    init {
        settingsStore.loadCacheSettings().let { cacheSettings ->
            cacheSettings.customDownloadDirectory?.let { storage.updateDownloadDirectory(File(it)) }
            cacheSettings.customAudioCacheDirectory?.let { storage.updateAudioCacheDirectory(File(it)) }
        }
    }
    val imageCacheRepository: ImageCacheRepository = storage
    val sidecarStatusRepository: SidecarStatusRepository = storage
    val providerResponseService: ProviderResponseService = ProviderResponseService(storage)
    val internetRadioStationManager: InternetRadioStationManager =
        InternetRadioStationManager(providerResponseService)

    val playbackAudioAssets: AndroidPlaybackAudioAssets =
        AndroidPlaybackAudioAssets(storage, storage)

    val audioMetadataSidecarService: AudioMetadataSidecarService =
        AudioMetadataSidecarService(
            playbackAudioAssets = playbackAudioAssets,
            audioTagReader = AndroidAudioTagReader(),
        )

    val lyricsSidecarService: LyricsSidecarService =
        LyricsSidecarService(
            lyricsRepository = storage,
            playbackAudioAssets = playbackAudioAssets,
            audioMetadataSidecarService = audioMetadataSidecarService,
        )

    fun popularTracksService(
        activeSourceIdProvider: () -> String?,
        providerProvider: () -> NavidromeProvider?,
    ): ArtistPopularTracksService =
        ArtistPopularTracksService(
            repository = storage,
            libraryTracksForArtist = { artist, limit ->
                val sourceId = activeSourceIdProvider()
                val indexedTracks = sourceId
                    ?.let { storage.libraryTracksForArtist(it, artist.id, limit) }
                    .orEmpty()
                    .ifEmpty {
                        sourceId
                            ?.let { storage.libraryTracksForArtistName(it, artist.name, limit) }
                            .orEmpty()
                    }
                indexedTracks.ifEmpty {
                    providerProvider()
                        ?.tracksForArtist(artist.id, limit.coerceAtMost(AndroidPopularTrackFallbackLimit))
                        .orEmpty()
                        .also { fetchedTracks ->
                            if (sourceId != null && fetchedTracks.isNotEmpty()) {
                                storage.upsertLibraryTracks(sourceId, fetchedTracks)
                            }
                        }
                }
            },
            client = ProviderArtistPopularTracksClient(providerProvider),
        )

    fun similarArtistsService(
        activeSourceIdProvider: () -> String?,
        providerProvider: () -> NavidromeProvider?,
    ): SimilarArtistsService =
        SimilarArtistsService(
            libraryArtistsSearch = { artistName, limit ->
                val sourceId = activeSourceIdProvider()
                val indexedArtists = sourceId
                    ?.let { storage.searchLibrary(it, artistName, limit, 0).artists }
                    .orEmpty()
                indexedArtists.ifEmpty {
                    providerProvider()?.search(artistName, limit.toInt())?.artists.orEmpty()
                }
            },
            client = ProviderSimilarArtistsClient(providerProvider),
        )

    suspend fun cacheAudioTrack(
        sourceId: String,
        provider: NavidromeProvider,
        track: Track,
        quality: StreamQuality,
    ): java.io.File {
        val file = storage.cacheAudioTrack(sourceId, provider, track, quality).file
        warmCoverArt(provider, track)
        return file
    }

    fun playlistEngine(
        state: AndroidAppState,
        playbackQueueController: PlaybackQueueController,
        activeQueue: () -> List<Track>,
        currentStreamQuality: () -> StreamQuality,
    ): AndroidPlaylistEngine =
        AndroidPlaylistEngine(
            scope = playbackRuntime.scope,
            state = state,
            waveformRepository = storage,
            warmCoverArt = ::warmCoverArt,
            cacheAudioTrack = ::cacheAudioTrack,
            playbackAudioAssets = playbackAudioAssets,
            playbackEngine = playbackRuntime.playbackEngine,
            playbackQueueController = playbackQueueController,
            waveformAnalyzer = playbackRuntime.waveformAnalyzer,
            audioMetadataSidecarService = audioMetadataSidecarService,
            lyricsSidecarService = lyricsSidecarService,
            sidecarStatusRepository = sidecarStatusRepository,
            activeQueue = activeQueue,
            currentStreamQuality = currentStreamQuality,
        )

    private suspend fun warmCoverArt(
        provider: NavidromeProvider,
        track: Track,
    ) {
        val coverArtId = track.coverArtId ?: track.albumId?.value ?: return
        val url = provider.coverArtUrl(coverArtId)
        storage.imageBytes(url) {
            provider.bytes(url) ?: throw IllegalStateException("Could not download cover art.")
        }
    }

    override fun close() {
        storage.close()
    }
}
