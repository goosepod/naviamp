package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.albummix.albumMixBuilderService
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.artistmix.artistMixBuilderService
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.genremix.genreMixBuilderService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.provider.MediaProvider

@Composable
internal fun rememberDesktopMixBuilderController(
    scope: CoroutineScope,
    storage: DesktopStorageDependencies,
    sourceId: () -> String?,
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
    popularTracksService: ArtistPopularTracksService,
    similarArtistsService: SimilarArtistsService,
): DesktopMixBuilderController {
    val artistMixBuilderService = rememberDesktopArtistMixBuilderService(
        storage = storage,
        sourceId = sourceId,
        provider = provider,
        homeContent = homeContent,
        popularTracksService = popularTracksService,
        similarArtistsService = similarArtistsService,
    )
    val albumMixBuilderService = rememberDesktopAlbumMixBuilderService(
        storage = storage,
        sourceId = sourceId,
        provider = provider,
        homeContent = homeContent,
        similarArtistsService = similarArtistsService,
    )
    val genreMixBuilderService = rememberDesktopGenreMixBuilderService(
        provider = provider,
        homeContent = homeContent,
    )
    return remember(artistMixBuilderService, albumMixBuilderService, genreMixBuilderService) {
        DesktopMixBuilderController(
            scope = scope,
            artistMixBuilderService = { artistMixBuilderService },
            albumMixBuilderService = { albumMixBuilderService },
            genreMixBuilderService = { genreMixBuilderService },
        )
    }
}

@Composable
internal fun rememberDesktopArtistMixBuilderService(
    storage: DesktopStorageDependencies,
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
                storage.searchLibrary(activeSourceId, query, limit).artists
            },
            popularTracksService = popularTracksService,
            similarArtistsService = similarArtistsService,
        )
    }

@Composable
internal fun rememberDesktopAlbumMixBuilderService(
    storage: DesktopStorageDependencies,
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
                storage.searchLibrary(activeSourceId, query, limit).albums
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
internal fun rememberDesktopGenreMixBuilderService(
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
): GenreMixBuilderService =
    remember(provider()) {
        genreMixBuilderService(
            provider = provider,
            homeContent = homeContent,
        )
    }
