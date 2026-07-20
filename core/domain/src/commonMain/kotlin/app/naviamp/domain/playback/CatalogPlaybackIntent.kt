package app.naviamp.domain.playback

/** Product-level playback selection produced by a host entry point such as Android Auto or iOS media intents. */
sealed interface CatalogPlaybackIntent {
    data object Resume : CatalogPlaybackIntent
    data class QueueItem(val index: Int) : CatalogPlaybackIntent
    data object LibraryRadio : CatalogPlaybackIntent
    data class RadioDj(val id: String) : CatalogPlaybackIntent
    data class Playlist(val id: String, val shuffle: Boolean) : CatalogPlaybackIntent
    data class PlaylistTrack(val playlistId: String, val trackId: String) : CatalogPlaybackIntent
    data class InternetRadio(
        val id: String,
        val name: String,
        val streamUrl: String,
        val homePageUrl: String?,
    ) : CatalogPlaybackIntent
    data class RecentRadio(val id: String) : CatalogPlaybackIntent
    data class Track(
        val id: String,
        val title: String? = null,
        val artistId: String? = null,
        val artistName: String? = null,
        val albumId: String? = null,
        val albumTitle: String? = null,
        val durationSeconds: Int? = null,
        val coverArtId: String? = null,
    ) : CatalogPlaybackIntent
    data class ArtistTrack(val artistId: String, val artistName: String?, val trackId: String) : CatalogPlaybackIntent
    data class AlbumTrack(val albumId: String, val trackId: String) : CatalogPlaybackIntent
    data class Artist(val id: String, val name: String?, val shuffle: Boolean) : CatalogPlaybackIntent
    data class Album(val id: String, val title: String?, val artist: String?, val shuffle: Boolean) : CatalogPlaybackIntent
    data class Download(val trackId: String) : CatalogPlaybackIntent
}
