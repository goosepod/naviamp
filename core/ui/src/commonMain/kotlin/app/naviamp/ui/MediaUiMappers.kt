package app.naviamp.ui

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaSearchResults

fun Artist.toSharedMediaItemUi(): SharedMediaItemUi =
    SharedMediaItemUi(id = id.value, title = name, subtitle = "Artist")

fun Album.toSharedMediaItemUi(coverArtUrl: (String?) -> String?): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id.value,
        title = title,
        subtitle = artistName,
        meta = releaseYear?.toString().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId),
    )

fun Playlist.toSharedMediaItemUi(
    coverArtUrl: (String?) -> String?,
    tracks: List<Track> = emptyList(),
): SharedMediaItemUi =
    SharedMediaItemUi(
        id = id,
        title = name,
        subtitle = "$trackCount tracks",
        meta = durationSeconds?.durationLabel().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId),
        coverArtUrls = tracks.mapNotNull { coverArtUrl(it.coverArtId) }.distinct().take(4),
    )

fun InternetRadioStation.toSharedMediaItemUi(): SharedMediaItemUi =
    SharedMediaItemUi(id = id, title = name, subtitle = homePageUrl ?: "Internet radio")

fun Track.toAndroidTrackRowUi(coverArtUrl: (String?) -> String?): AndroidTrackRowUi =
    AndroidTrackRowUi(
        id = id.value,
        title = title,
        subtitle = listOfNotNull(artistName, albumTitle).joinToString(" - "),
        coverArtUrl = coverArtUrl(coverArtId),
        meta = durationSeconds?.durationLabel().orEmpty(),
    )

fun Track.toNowPlayingItemUi(coverArtUrl: (String?) -> String?): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id.value,
        title = title,
        subtitle = listOfNotNull(artistName, albumTitle).joinToString(" - "),
        meta = durationSeconds?.durationLabel().orEmpty(),
        coverArtUrl = coverArtUrl(coverArtId),
    )

fun Playlist.toPlaylistChoiceUi(): NaviampPlaylistChoiceUi =
    NaviampPlaylistChoiceUi(
        id = id,
        name = name,
        subtitle = "$trackCount tracks",
    )

fun Playlist.toSharedPlaylistDetailUi(
    tracks: List<Track>,
    coverArtUrl: (String?) -> String?,
): SharedPlaylistDetailUi =
    SharedPlaylistDetailUi(
        playlist = toSharedMediaItemUi(coverArtUrl, tracks),
        tracks = tracks.map { it.toAndroidTrackRowUi(coverArtUrl) },
    )

fun AlbumDetails.toSharedAlbumDetailUi(coverArtUrl: (String?) -> String?): SharedAlbumDetailUi =
    SharedAlbumDetailUi(
        album = album.toSharedMediaItemUi(coverArtUrl),
        tracks = tracks.map { it.toAndroidTrackRowUi(coverArtUrl) },
        totalDurationLabel = tracks.totalDurationLabel(),
    )

fun ArtistDetails.toSharedArtistDetailUi(coverArtUrl: (String?) -> String?): SharedArtistDetailUi =
    SharedArtistDetailUi(
        artist = artist.toSharedMediaItemUi(),
        albums = albums.map { it.toSharedMediaItemUi(coverArtUrl) },
    )

fun MediaSearchResults.toSharedSearchResultsUi(coverArtUrl: (String?) -> String?): SharedSearchResultsUi =
    SharedSearchResultsUi(
        artists = artists.map { it.toSharedMediaItemUi() },
        albums = albums.map { it.toSharedMediaItemUi(coverArtUrl) },
        tracks = tracks.map { it.toAndroidTrackRowUi(coverArtUrl) },
    )

fun InternetRadioStation.toNowPlayingStationUi(): NaviampNowPlayingItemUi =
    NaviampNowPlayingItemUi(
        id = id,
        title = name,
        subtitle = homePageUrl ?: "Internet radio",
    )

private fun List<Track>.totalDurationLabel(): String {
    val totalSeconds = mapNotNull { it.durationSeconds }.sum()
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = totalSeconds / 60
    val remainingMinutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${remainingMinutes}m" else "$minutes minutes"
}

private fun Int.durationLabel(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
