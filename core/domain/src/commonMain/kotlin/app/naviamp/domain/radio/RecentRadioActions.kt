package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.settings.RecentRadioKind
import app.naviamp.domain.settings.RecentRadioStream

sealed class RecentRadioAction {
    object PlayLibrary : RecentRadioAction()
    object PlayRandomAlbum : RecentRadioAction()
    data class PlayGenre(val genre: Genre) : RecentRadioAction()
    data class PlayDecade(val fromYear: Int, val toYear: Int) : RecentRadioAction()
    data class PlayArtist(val artist: Artist) : RecentRadioAction()
    data class PlayAlbum(val album: Album) : RecentRadioAction()
    data class PlayTrack(val track: Track) : RecentRadioAction()
}

fun recentRadioAction(stream: RecentRadioStream): RecentRadioAction? =
    when (stream.kind) {
        RecentRadioKind.Library -> RecentRadioAction.PlayLibrary
        RecentRadioKind.RandomAlbum ->
            stream.album
                ?.toAlbum()
                ?.let { RecentRadioAction.PlayAlbum(it) }
                ?: RecentRadioAction.PlayRandomAlbum
        RecentRadioKind.Genre ->
            stream.genre?.let { RecentRadioAction.PlayGenre(Genre(it)) }
        RecentRadioKind.Decade -> {
            val fromYear = stream.fromYear ?: return null
            val toYear = stream.toYear ?: return null
            RecentRadioAction.PlayDecade(fromYear, toYear)
        }
        RecentRadioKind.Artist ->
            stream.artist?.toArtist()?.let { RecentRadioAction.PlayArtist(it) }
        RecentRadioKind.Album ->
            stream.album?.toAlbum()?.let { RecentRadioAction.PlayAlbum(it) }
        RecentRadioKind.Track ->
            stream.track?.toTrack()?.let { RecentRadioAction.PlayTrack(it) }
    }
