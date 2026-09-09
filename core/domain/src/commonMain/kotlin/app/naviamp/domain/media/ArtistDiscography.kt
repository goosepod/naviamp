package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track

/** Provider-neutral artist catalog separated into primary releases and credited appearances. */
data class ArtistDiscography(
    val primary: ArtistDetails,
    val appearanceAlbums: List<Album> = emptyList(),
    val appearanceTracks: List<Track> = emptyList(),
    val appearanceLoadFailed: Boolean = false,
    val appearancesTruncated: Boolean = false,
)

data class ArtistDiscographyAppearances(
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val failed: Boolean = false,
    val truncated: Boolean = false,
)

/**
 * Applies the provider-neutral Appears On policy to reverse-credit results.
 *
 * Candidate membership has already been established by a stable-ID provider query or the
 * source-scoped credit index. Artist names and roles are therefore descriptive and never used to
 * discard aliases or credits whose mapped ID is unavailable. Primary releases win when a provider
 * returns the same release through both paths, and the first occurrence of each media identity
 * preserves the provider's deterministic order.
 */
fun ArtistDiscography.reconciledAppearances(): ArtistDiscography {
    val primaryAlbumIds = primary.albums.mapTo(mutableSetOf()) { album -> album.id }
    val tracks = appearanceTracks
        .filter { track -> track.albumId == null || track.albumId !in primaryAlbumIds }
        .distinctBy(Track::id)
    val includedAlbumIds = tracks.mapNotNullTo(mutableSetOf(), Track::albumId)
    val albums = appearanceAlbums
        .filter { album -> album.id !in primaryAlbumIds && album.id in includedAlbumIds }
        .distinctBy(Album::id)
    return copy(appearanceAlbums = albums, appearanceTracks = tracks)
}
