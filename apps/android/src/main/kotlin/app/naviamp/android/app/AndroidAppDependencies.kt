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
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.DeezerPopularTracksClient
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.provider.navidrome.NavidromeProvider

class AndroidAppDependencies(
    context: Context,
    val playbackRuntime: AndroidPlaybackRuntime = AndroidPlaybackRuntime.get(context),
    val storage: AndroidStorageDependencies = AndroidStorageDependencies(context),
    val settingsStore: AndroidSettingsStore = AndroidSettingsStore(context),
) : AutoCloseable {
    val imageCacheRepository: ImageCacheRepository = storage
    val sidecarStatusRepository: SidecarStatusRepository = storage
    val providerResponseService: ProviderResponseService = ProviderResponseService(storage)

    private val deezerDiscoveryClient: DeezerPopularTracksClient =
        DeezerPopularTracksClient(AndroidPopularTracksHttpClient())

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
            client = deezerDiscoveryClient,
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
            client = deezerDiscoveryClient,
        )

    suspend fun cacheAudioTrack(
        sourceId: String,
        provider: NavidromeProvider,
        track: Track,
        quality: StreamQuality,
    ): java.io.File =
        storage.cacheAudioTrack(sourceId, provider, track, quality).file

    override fun close() {
        storage.close()
    }
}
