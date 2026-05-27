package app.naviamp.desktop

import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.radio.RadioService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class DesktopRadioRequest(
    val label: String,
    val recentRadioStream: RecentRadioStream,
    val loadTracks: suspend (RadioService) -> List<Track>,
)

data class DesktopSeededRadioRequest(
    val label: String,
    val seedTrack: Track,
    val recentRadioStream: RecentRadioStream,
    val loadRest: suspend (RadioService) -> List<Track>,
)

fun libraryRadioRequest(): DesktopRadioRequest =
    DesktopRadioRequest(
        label = "Library radio",
        recentRadioStream = libraryRecentRadioStream(),
        loadTracks = { radioService -> radioService.libraryRadio() },
    )

fun genreRadioRequest(genre: Genre): DesktopRadioRequest =
    DesktopRadioRequest(
        label = "${genre.name} radio",
        recentRadioStream = genreRecentRadioStream(genre),
        loadTracks = { radioService -> radioService.genreRadio(genre.name) },
    )

fun decadeRadioRequest(fromYear: Int, toYear: Int): DesktopRadioRequest =
    DesktopRadioRequest(
        label = "$fromYear-$toYear radio",
        recentRadioStream = decadeRecentRadioStream(fromYear, toYear),
        loadTracks = { radioService -> radioService.decadeRadio(fromYear, toYear) },
    )

fun trackRadioRequest(track: Track): DesktopSeededRadioRequest =
    DesktopSeededRadioRequest(
        label = "${track.title} radio",
        seedTrack = track,
        recentRadioStream = trackRecentRadioStream(track),
        loadRest = { radioService -> radioService.trackRadio(track.id) },
    )

fun randomAlbumSeededRadioRequest(
    album: Album,
    seedTrack: Track,
): DesktopSeededRadioRequest =
    DesktopSeededRadioRequest(
        label = "${album.title} radio",
        seedTrack = seedTrack,
        recentRadioStream = randomAlbumRecentRadioStream(album),
        loadRest = { radioService -> radioService.albumRadio(album.id) },
    )

fun artistSeededRadioRequest(
    artist: Artist,
    seedTrack: Track,
): DesktopSeededRadioRequest =
    DesktopSeededRadioRequest(
        label = "${artist.name} radio",
        seedTrack = seedTrack,
        recentRadioStream = artistRecentRadioStream(artist),
        loadRest = { radioService -> radioService.artistRadio(artist.id) },
    )

fun albumSeededRadioRequest(
    album: Album,
    seedTrack: Track,
    loadedAlbumTracks: List<Track> = emptyList(),
): DesktopSeededRadioRequest =
    DesktopSeededRadioRequest(
        label = "${album.title} radio",
        seedTrack = seedTrack,
        recentRadioStream = albumRecentRadioStream(album),
        loadRest = { radioService -> radioService.albumRadio(album.id, loadedAlbumTracks) },
    )

fun popularTracksRadioRequest(
    tracks: List<Track>,
    seedLimit: Int,
): DesktopSeededRadioRequest? {
    val seedTrack = tracks.shuffled().firstOrNull() ?: return null
    return DesktopSeededRadioRequest(
        label = "${seedTrack.artistName} popular tracks radio",
        seedTrack = seedTrack,
        recentRadioStream = popularTracksRecentRadioStream(seedTrack),
        loadRest = { radioService ->
            coroutineScope {
                tracks.take(seedLimit)
                    .map { track -> async(Dispatchers.IO) { radioService.trackRadio(track.id) } }
                    .awaitAll()
                    .flatten()
            }
        },
    )
}
