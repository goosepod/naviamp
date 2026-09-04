package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track

/** Provider-neutral artist catalog separated into primary releases and credited appearances. */
data class ArtistDiscography(
    val primary: ArtistDetails,
    val appearanceAlbums: List<Album> = emptyList(),
    val appearanceTracks: List<Track> = emptyList(),
)
