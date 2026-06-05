package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track

sealed interface AddToPlaylistTarget {
    val title: String

    data class ArtistTarget(val artist: Artist) : AddToPlaylistTarget {
        override val title: String = artist.name
    }

    data class AlbumTarget(val album: Album) : AddToPlaylistTarget {
        override val title: String = album.title
    }

    data class TrackTarget(val track: Track) : AddToPlaylistTarget {
        override val title: String = track.title
    }

    data class PlaylistTarget(val playlist: Playlist) : AddToPlaylistTarget {
        override val title: String = playlist.name
    }
}
