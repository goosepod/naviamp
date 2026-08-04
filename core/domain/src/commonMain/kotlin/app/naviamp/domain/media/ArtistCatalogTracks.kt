package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider

fun orderedArtistCatalogAlbums(
    detail: ArtistDetails,
    albumIdsInDisplayOrder: List<String>? = null,
): List<Album> = albumIdsInDisplayOrder
    ?.mapNotNull { albumId -> detail.albums.firstOrNull { album -> album.id.value == albumId } }
    ?: detail.albums

suspend fun loadArtistCatalogTracks(
    detail: ArtistDetails,
    providerResponseService: ProviderResponseService,
    provider: MediaProvider,
    albumIdsInDisplayOrder: List<String>? = null,
): List<Track> = orderedArtistCatalogAlbums(detail, albumIdsInDisplayOrder).flatMap { album ->
    runCatching { providerResponseService.album(provider, album.id).tracks }.getOrDefault(emptyList())
}
