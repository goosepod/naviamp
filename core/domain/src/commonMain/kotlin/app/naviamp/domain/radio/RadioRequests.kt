package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.settings.RecentRadioStream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class RadioRequest(
    val label: String,
    val recentRadioStream: RecentRadioStream,
    val loadTracks: suspend (RadioService) -> List<Track>,
)

data class SeededRadioRequest(
    val label: String,
    val seedTrack: Track,
    val recentRadioStream: RecentRadioStream,
    val loadRest: suspend (RadioService) -> List<Track>,
)

sealed interface RadioRequestStartResult {
    data class Ready(
        val firstTrack: Track,
        val queue: List<Track>,
        val recentRadioStream: RecentRadioStream?,
    ) : RadioRequestStartResult

    data object Empty : RadioRequestStartResult

    data class Failed(
        val error: Throwable,
    ) : RadioRequestStartResult
}

suspend fun radioRequestStartResult(
    request: RadioRequest,
    radioService: RadioService,
    deduplicateTracks: Boolean = false,
): RadioRequestStartResult =
    radioRequestStartResult(
        radioService = radioService,
        recentRadioStream = request.recentRadioStream,
        deduplicateTracks = deduplicateTracks,
        loadTracks = request.loadTracks,
    )

suspend fun radioRequestStartResult(
    radioService: RadioService,
    recentRadioStream: RecentRadioStream?,
    deduplicateTracks: Boolean = false,
    loadTracks: suspend (RadioService) -> List<Track>,
): RadioRequestStartResult =
    runCatching {
        val tracks = loadTracks(radioService)
            .let { loadedTracks ->
                if (deduplicateTracks) {
                    loadedTracks.distinctBy { track -> track.id }
                } else {
                    loadedTracks
                }
            }
        val firstTrack = tracks.firstOrNull() ?: return@runCatching RadioRequestStartResult.Empty
        RadioRequestStartResult.Ready(
            firstTrack = firstTrack,
            queue = tracks,
            recentRadioStream = recentRadioStream?.withRadioCoverArtIds(tracks),
        )
    }.getOrElse { error ->
        RadioRequestStartResult.Failed(error)
    }

fun libraryRadioRequest(): RadioRequest =
    RadioRequest(
        label = "Library radio",
        recentRadioStream = libraryRecentRadioStream(),
        loadTracks = { radioService -> radioService.libraryRadio() },
    )

fun genreRadioRequest(genre: Genre): RadioRequest =
    RadioRequest(
        label = "${genre.name} radio",
        recentRadioStream = genreRecentRadioStream(genre),
        loadTracks = { radioService -> radioService.genreRadio(genre.name) },
    )

fun genreMixRadioRequest(genres: List<Genre>): RadioRequest {
    val distinctGenres = genres.distinctBy { it.name.lowercase() }
    val label = genreMixRadioLabel(distinctGenres)
    val recentStream = distinctGenres.firstOrNull()
        ?.let { genreRecentRadioStream(it).copy(id = "genre-mix:${distinctGenres.joinToString(":") { genre -> genre.name }}", label = label) }
        ?: libraryRecentRadioStream()
    return RadioRequest(
        label = label,
        recentRadioStream = recentStream,
        loadTracks = { radioService ->
            coroutineScope {
                distinctGenres
                    .map { genre -> async { radioService.genreRadio(genre.name) } }
                    .awaitAll()
                    .flatten()
                    .shuffled()
                    .distinctBy { it.id }
            }
        },
    )
}

fun decadeRadioRequest(fromYear: Int, toYear: Int): RadioRequest =
    RadioRequest(
        label = "$fromYear-$toYear radio",
        recentRadioStream = decadeRecentRadioStream(fromYear, toYear),
        loadTracks = { radioService -> radioService.decadeRadio(fromYear, toYear) },
    )

fun trackRadioRequest(track: Track): SeededRadioRequest =
    SeededRadioRequest(
        label = "${track.title} radio",
        seedTrack = track,
        recentRadioStream = trackRecentRadioStream(track),
        loadRest = { radioService -> radioService.trackRadio(track.id) },
    )

fun randomAlbumSeededRadioRequest(
    album: Album,
    seedTrack: Track,
): SeededRadioRequest =
    SeededRadioRequest(
        label = "${album.title} radio",
        seedTrack = seedTrack,
        recentRadioStream = randomAlbumRecentRadioStream(album),
        loadRest = { radioService -> radioService.albumRadio(album.id) },
    )

fun artistSeededRadioRequest(
    artist: Artist,
    seedTrack: Track,
): SeededRadioRequest =
    SeededRadioRequest(
        label = "${artist.name} radio",
        seedTrack = seedTrack,
        recentRadioStream = artistRecentRadioStream(artist),
        loadRest = { radioService -> radioService.artistRadio(artist.id) },
    )

fun artistMixSeededRadioRequest(
    artists: List<Artist>,
    seedTrack: Track,
    preferredTracks: List<Track> = emptyList(),
): SeededRadioRequest {
    val distinctArtists = artists.distinctBy { it.id }
    val label = artistMixRadioLabel(distinctArtists)
    val recentStream = distinctArtists.firstOrNull()
        ?.let { artistRecentRadioStream(it).copy(id = "artist-mix:${distinctArtists.joinToString(":") { artist -> artist.id.value }}", label = label) }
        ?: libraryRecentRadioStream()
    return SeededRadioRequest(
        label = label,
        seedTrack = seedTrack,
        recentRadioStream = recentStream,
        loadRest = { radioService ->
            coroutineScope {
                val popularQueue = preferredTracks.filterNot { it.id == seedTrack.id }
                val radioTracks = distinctArtists
                    .map { artist -> async { radioService.artistRadio(artist.id) } }
                    .awaitAll()
                    .flatten()
                (popularQueue + radioTracks).distinctBy { it.id }
            }
        },
    )
}

fun albumSeededRadioRequest(
    album: Album,
    seedTrack: Track,
    loadedAlbumTracks: List<Track> = emptyList(),
): SeededRadioRequest =
    SeededRadioRequest(
        label = "${album.title} radio",
        seedTrack = seedTrack,
        recentRadioStream = albumRecentRadioStream(album),
        loadRest = { radioService -> radioService.albumRadio(album.id, loadedAlbumTracks) },
    )

fun albumMixSeededRadioRequest(
    albums: List<Album>,
    seedTrack: Track,
    preferredTracks: List<Track> = emptyList(),
): SeededRadioRequest {
    val distinctAlbums = albums.distinctBy { it.id }
    val label = albumMixRadioLabel(distinctAlbums)
    val recentStream = distinctAlbums.firstOrNull()
        ?.let { albumRecentRadioStream(it).copy(id = "album-mix:${distinctAlbums.joinToString(":") { album -> album.id.value }}", label = label) }
        ?: libraryRecentRadioStream()
    return SeededRadioRequest(
        label = label,
        seedTrack = seedTrack,
        recentRadioStream = recentStream,
        loadRest = { radioService ->
            coroutineScope {
                val mixedQueue = preferredTracks.filterNot { it.id == seedTrack.id }
                val radioTracks = distinctAlbums
                    .map { album -> async { radioService.albumRadio(album.id) } }
                    .awaitAll()
                    .flatten()
                (mixedQueue + radioTracks).distinctBy { it.id }
            }
        },
    )
}

fun popularTracksRadioRequest(
    tracks: List<Track>,
    seedLimit: Int,
): SeededRadioRequest? {
    val seedTrack = tracks.shuffled().firstOrNull() ?: return null
    return SeededRadioRequest(
        label = "${seedTrack.artistName} popular tracks radio",
        seedTrack = seedTrack,
        recentRadioStream = popularTracksRecentRadioStream(seedTrack),
        loadRest = { radioService ->
            coroutineScope {
                tracks.take(seedLimit)
                    .map { track -> async { radioService.trackRadio(track.id) } }
                    .awaitAll()
                    .flatten()
            }
        },
    )
}

fun artistMixRadioLabel(artists: List<Artist>): String =
    when (artists.size) {
        0 -> "Artist mix"
        1 -> "${artists.first().name} mix"
        2 -> "${artists[0].name} + ${artists[1].name} mix"
        else -> "${artists[0].name} + ${artists.size - 1} more mix"
    }

fun albumMixRadioLabel(albums: List<Album>): String =
    when (albums.size) {
        0 -> "Album mix"
        1 -> "${albums.first().title} mix"
        2 -> "${albums[0].title} + ${albums[1].title} mix"
        else -> "${albums[0].title} + ${albums.size - 1} more mix"
    }

fun genreMixRadioLabel(genres: List<Genre>): String =
    when (genres.size) {
        0 -> "Genre mix"
        1 -> "${genres.first().name} mix"
        2 -> "${genres[0].name} + ${genres[1].name} mix"
        else -> "${genres[0].name} + ${genres.size - 1} more mix"
    }
