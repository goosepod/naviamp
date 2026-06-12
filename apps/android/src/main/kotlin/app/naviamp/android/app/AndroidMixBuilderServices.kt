package app.naviamp.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.domain.Track
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.albummix.albumMixBuilderService
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.artistmix.artistMixBuilderService
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.genremix.genreMixBuilderService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.RecentRadioStream
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun rememberAndroidMixBuilderController(
    scope: CoroutineScope,
    state: AndroidAppState,
    queueController: PlaybackQueueController,
    storage: AndroidStorageDependencies,
    sourceId: () -> String?,
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
    popularTracksService: ArtistPopularTracksService,
    similarArtistsService: SimilarArtistsService,
    playTrack: (Track, List<Track>) -> Unit,
    rememberRecentRadioStream: (RecentRadioStream) -> Unit,
): AndroidMixBuilderController {
    val artistMixBuilderService = rememberAndroidArtistMixBuilderService(
        storage = storage,
        sourceId = sourceId,
        provider = provider,
        homeContent = homeContent,
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
    )
    val albumMixBuilderService = rememberAndroidAlbumMixBuilderService(
        storage = storage,
        sourceId = sourceId,
        provider = provider,
        homeContent = homeContent,
        similarArtistsService = similarArtistsService,
    )
    val genreMixBuilderService = rememberAndroidGenreMixBuilderService(
        provider = provider,
        homeContent = homeContent,
    )
    return remember(state, storage, artistMixBuilderService, albumMixBuilderService, genreMixBuilderService) {
        AndroidMixBuilderController(
            scope = scope,
            state = state,
            queueController = queueController,
            storage = storage,
            artistMixBuilderService = { artistMixBuilderService },
            albumMixBuilderService = { albumMixBuilderService },
            genreMixBuilderService = { genreMixBuilderService },
            playTrack = playTrack,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }
}

@Composable
internal fun rememberAndroidArtistMixBuilderService(
    storage: AndroidStorageDependencies,
    sourceId: () -> String?,
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
    popularTracksService: ArtistPopularTracksService,
    similarArtistsService: SimilarArtistsService,
): ArtistMixBuilderService =
    remember(popularTracksService, similarArtistsService) {
        artistMixBuilderService(
            sourceId = sourceId,
            provider = provider,
            homeContent = homeContent,
            localArtistSearch = { activeSourceId, query, limit ->
                storage.searchLibrary(activeSourceId, query, limit, 0).artists
            },
            popularTracksService = popularTracksService,
            similarArtistsService = similarArtistsService,
        )
    }

@Composable
internal fun rememberAndroidAlbumMixBuilderService(
    storage: AndroidStorageDependencies,
    sourceId: () -> String?,
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
    similarArtistsService: SimilarArtistsService,
): AlbumMixBuilderService =
    remember(similarArtistsService) {
        val providerResponseService = ProviderResponseService(storage)
        albumMixBuilderService(
            sourceId = sourceId,
            provider = provider,
            homeContent = homeContent,
            localAlbumSearch = { activeSourceId, query, limit ->
                storage.searchLibrary(activeSourceId, query, limit, 0).albums
            },
            localAlbumTracks = { activeSourceId, album, limit ->
                storage.libraryTracksForAlbum(activeSourceId, album.id, limit)
            },
            providerAlbumTracks = { activeProvider, album ->
                providerResponseService.album(activeProvider, album.id).tracks
            },
            similarArtistsService = similarArtistsService,
        )
    }

@Composable
internal fun rememberAndroidGenreMixBuilderService(
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
): GenreMixBuilderService =
    remember(provider()) {
        genreMixBuilderService(
            provider = provider,
            homeContent = homeContent,
        )
    }
