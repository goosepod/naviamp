package app.naviamp.desktop

import app.naviamp.desktop.settings.RecentRadioKind
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track

sealed class DesktopRecentRadioAction {
    object PlayLibrary : DesktopRecentRadioAction()
    object PlayRandomAlbum : DesktopRecentRadioAction()
    data class PlayGenre(val genre: Genre) : DesktopRecentRadioAction()
    data class PlayDecade(val fromYear: Int, val toYear: Int) : DesktopRecentRadioAction()
    data class PlayArtist(val artist: Artist) : DesktopRecentRadioAction()
    data class PlayAlbum(val album: Album) : DesktopRecentRadioAction()
    data class PlayTrack(val track: Track) : DesktopRecentRadioAction()
}

fun recentRadioAction(stream: RecentRadioStream): DesktopRecentRadioAction? =
    when (stream.kind) {
        RecentRadioKind.Library -> DesktopRecentRadioAction.PlayLibrary
        RecentRadioKind.RandomAlbum ->
            stream.album
                ?.toAlbum()
                ?.let { DesktopRecentRadioAction.PlayAlbum(it) }
                ?: DesktopRecentRadioAction.PlayRandomAlbum
        RecentRadioKind.Genre ->
            stream.genre?.let { DesktopRecentRadioAction.PlayGenre(Genre(it)) }
        RecentRadioKind.Decade -> {
            val fromYear = stream.fromYear ?: return null
            val toYear = stream.toYear ?: return null
            DesktopRecentRadioAction.PlayDecade(fromYear, toYear)
        }
        RecentRadioKind.Artist ->
            stream.artist?.toArtist()?.let { DesktopRecentRadioAction.PlayArtist(it) }
        RecentRadioKind.Album ->
            stream.album?.toAlbum()?.let { DesktopRecentRadioAction.PlayAlbum(it) }
        RecentRadioKind.Track ->
            stream.track?.toTrack()?.let { DesktopRecentRadioAction.PlayTrack(it) }
    }
