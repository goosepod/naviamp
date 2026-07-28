package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.albummix.albumMixBuilderService
import app.naviamp.domain.artistmix.artistMixBuilderService
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.genremix.genreMixBuilderService
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.popular.ArtistPopularTracksClient
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.domain.popular.ProviderArtistPopularTracksClient
import app.naviamp.domain.popular.ProviderSimilarArtistsClient
import app.naviamp.domain.popular.SessionArtistPopularTracksRepository
import app.naviamp.domain.popular.SimilarArtistsClient
import app.naviamp.domain.popular.SimilarArtistsService

/**
 * Builds all standard mix repositories in common code. Hosts may provide durable library storage,
 * but never construct Artist, Album, or Genre product behavior themselves.
 */
fun naviampCoreStandardMixServices(
    providerSource: NaviampCoreMediaProviderSource,
    sourceId: () -> String? = { providerSource.current()?.cacheNamespace },
    libraryIndex: LocalLibraryIndexRepository? = null,
    nowEpochMillis: () -> Long,
): NaviampCoreMixServices {
    val popularTracks = ArtistPopularTracksService(
        repository = libraryIndex ?: SessionArtistPopularTracksRepository(),
        libraryTracksForArtist = { artist, limit ->
            sourceId()
                ?.let { activeSourceId -> libraryIndex?.libraryTracksForArtist(activeSourceId, artist.id, limit) }
                .orEmpty()
                .ifEmpty {
                    providerSource.current()
                        ?.search(artist.name, limit.coerceAtMost(500).toInt())
                        ?.tracks
                        .orEmpty()
                        .filter { track ->
                            track.artistId == artist.id || track.artistName.equals(artist.name, ignoreCase = true)
                        }
                }
        },
        client = ProviderArtistPopularTracksClient(
            clientProvider = { providerSource.current() as? ArtistPopularTracksClient },
        ),
        nowMillis = nowEpochMillis,
    )
    val similarArtists = SimilarArtistsService(
        libraryArtistsSearch = { query, limit ->
            sourceId()
                ?.let { activeSourceId -> libraryIndex?.searchLibrary(activeSourceId, query, limit, 0)?.artists }
                .orEmpty()
        },
        client = ProviderSimilarArtistsClient {
            providerSource.current() as? SimilarArtistsClient
        },
        fallbackArtistsSearch = { query, limit ->
            providerSource.current()?.search(query, limit.coerceAtMost(500).toInt())?.artists.orEmpty()
        },
    )
    fun recentTracks(): List<Track> = sourceId()
        ?.let { activeSourceId -> libraryIndex?.recentlyPlayedLibraryTracks(activeSourceId, StandardMixRecentTrackLimit) }
        .orEmpty()
    fun homeContent(): HomeContent {
        val recent = recentTracks()
        return HomeContent(
            recentlyPlayedTracks = recent,
        )
    }

    return NaviampCoreMixServices(
        artist = {
            artistMixBuilderService(
                sourceId = sourceId,
                provider = providerSource::current,
                homeContent = ::homeContent,
                localArtistSearch = { activeSourceId, query, limit ->
                    libraryIndex?.searchLibrary(activeSourceId, query, limit, 0)?.artists.orEmpty()
                },
                popularTracksService = popularTracks,
                similarArtistsService = similarArtists,
            )
        },
        album = {
            albumMixBuilderService(
                sourceId = sourceId,
                provider = providerSource::current,
                homeContent = ::homeContent,
                localAlbumSearch = { activeSourceId, query, limit ->
                    libraryIndex?.searchLibrary(activeSourceId, query, limit, 0)?.albums.orEmpty()
                },
                localAlbumTracks = { activeSourceId, album, limit ->
                    libraryIndex?.libraryTracksForAlbum(activeSourceId, album.id, limit).orEmpty()
                },
                providerAlbumTracks = { provider, album -> provider.album(album.id).tracks },
                similarArtistsService = similarArtists,
            )
        },
        genre = {
            genreMixBuilderService(
                provider = providerSource::current,
                homeContent = ::homeContent,
            )
        },
    )
}

private const val StandardMixRecentTrackLimit = 100L
