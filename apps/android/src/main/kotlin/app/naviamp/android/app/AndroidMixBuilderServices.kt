package app.naviamp.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.naviamp.domain.Album
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.home.mixBuilderAlbumCandidates
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.SimilarArtistsService
import app.naviamp.domain.provider.AlbumListType
import app.naviamp.domain.provider.MediaProvider

@Composable
internal fun rememberAndroidArtistMixBuilderService(
    storage: AndroidStorageDependencies,
    sourceId: () -> String?,
    provider: () -> MediaProvider?,
    homeContent: HomeContent,
    popularTracksService: ArtistPopularTracksService,
    similarArtistsService: SimilarArtistsService,
): ArtistMixBuilderService =
    remember(popularTracksService, similarArtistsService) {
        ArtistMixBuilderService(
            sourceId = sourceId,
            artistSearch = { query, limit ->
                sourceId()
                    ?.let { storage.searchLibrary(it, query, limit, 0).artists }
                    .orEmpty()
                    .ifEmpty { provider()?.search(query, limit.toInt())?.artists.orEmpty() }
            },
            randomArtists = { limit ->
                homeContent.artists.shuffled().take(limit.toInt()).ifEmpty {
                    provider()?.artists(limit.toInt())?.shuffled().orEmpty()
                }
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
    homeContent: HomeContent,
    similarArtistsService: SimilarArtistsService,
): AlbumMixBuilderService =
    remember(similarArtistsService) {
        AlbumMixBuilderService(
            albumSearch = { query, limit ->
                sourceId()
                    ?.let { storage.searchLibrary(it, query, limit, 0).albums }
                    .orEmpty()
                    .ifEmpty { provider()?.search(query, limit.toInt())?.albums.orEmpty() }
            },
            randomAlbums = { limit ->
                homeContent.mixBuilderAlbumCandidates()
                    .shuffled()
                    .take(limit.toInt())
                    .ifEmpty {
                        provider()?.albumList(AlbumListType.Random, limit.toInt())?.shuffled().orEmpty()
                    }
            },
            albumsForArtist = { artist, limit ->
                sourceId()
                    ?.let { storage.searchLibrary(it, artist.name, limit, 0).albums }
                    .orEmpty()
                    .filter { album -> album.artistName.equals(artist.name, ignoreCase = true) }
            },
            albumTracks = { album: Album, limit ->
                val localTracks = sourceId()?.let { storage.libraryTracksForAlbum(it, album.id, limit) }.orEmpty()
                val providerTracks = provider()?.let { activeProvider ->
                    runCatching { ProviderResponseService(storage).album(activeProvider, album.id).tracks }
                        .getOrDefault(emptyList())
                }.orEmpty()
                providerTracks.ifEmpty { localTracks }.take(limit.toInt())
            },
            similarArtistsService = similarArtistsService,
        )
    }

@Composable
internal fun rememberAndroidGenreMixBuilderService(
    provider: () -> MediaProvider?,
    homeContent: HomeContent,
): GenreMixBuilderService =
    remember(provider(), homeContent.genres) {
        GenreMixBuilderService(
            genres = { limit ->
                provider()?.genres(limit.toInt()).orEmpty().ifEmpty { homeContent.genres }
            },
        )
    }
