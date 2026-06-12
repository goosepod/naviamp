package app.naviamp.domain.mixbuilder

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.albummix.albumMixSelectedAlbumsAfterRemove
import app.naviamp.domain.albummix.albumMixSelectedAlbumsAfterSelect
import app.naviamp.domain.albummix.albumMixTrackQueue
import app.naviamp.domain.artistmix.artistMixPopularQueue
import app.naviamp.domain.artistmix.artistMixSelectedArtistsAfterRemove
import app.naviamp.domain.artistmix.artistMixSelectedArtistsAfterSelect
import app.naviamp.domain.genremix.genreMixSelectedGenresAfterRemove
import app.naviamp.domain.genremix.genreMixSelectedGenresAfterSelect

class MixSuggestionsCoordinator<Item>(
    private val setLoading: (Boolean) -> Unit,
    private val setSuggestions: (List<Item>) -> Unit,
    private val setStatus: (String?) -> Unit,
) {
    suspend fun load(
        emptyStatus: String,
        failureStatus: (Throwable) -> String,
        suggestions: suspend () -> List<Item>,
    ) {
        setLoading(true)
        runCatching { suggestions() }
            .onSuccess { nextSuggestions ->
                setSuggestions(nextSuggestions)
                setStatus(if (nextSuggestions.isEmpty()) emptyStatus else null)
            }
            .onFailure { error ->
                setStatus(failureStatus(error))
            }
        setLoading(false)
    }
}

class MixItemTracksCoordinator<Item>(
    private val setStatus: (String?) -> Unit,
    private val setTracks: (Item, List<Track>) -> Unit,
) {
    suspend fun load(
        item: Item,
        loadingStatus: String,
        emptyStatus: String,
        failureStatus: (Throwable) -> String,
        tracks: suspend () -> List<Track>,
    ) {
        setStatus(loadingStatus)
        runCatching { tracks() }
            .onSuccess { nextTracks ->
                setTracks(item, nextTracks)
                setStatus(if (nextTracks.isEmpty()) emptyStatus else null)
            }
            .onFailure { error ->
                setStatus(failureStatus(error))
            }
    }
}

fun artistMixLoadSuggestionsEmptyStatus(initial: Boolean): String =
    if (initial) "No artist suggestions yet." else "No artists matched."

fun artistMixLoadSuggestionsFailureStatus(initial: Boolean, error: Throwable): String =
    error.message ?: if (initial) "Could not load artist suggestions." else "Could not search artists."

fun artistMixLoadTracksStatus(artist: Artist): String =
    "Loading ${artist.name} songs..."

fun artistMixEmptyTracksStatus(artist: Artist): String =
    "${artist.name} popular songs were not matched."

fun artistMixLoadTracksFailureStatus(artist: Artist, error: Throwable): String =
    error.message ?: "Could not load ${artist.name} songs."

fun artistMixRelatedSuggestionsFailureStatus(error: Throwable): String =
    error.message ?: "Could not load similar artists."

fun artistMixSelectedAfterSelect(selectedArtists: List<Artist>, artist: Artist): List<Artist> =
    artistMixSelectedArtistsAfterSelect(selectedArtists, artist)

fun artistMixSelectedAfterRemove(selectedArtists: List<Artist>, artist: Artist): List<Artist> =
    artistMixSelectedArtistsAfterRemove(selectedArtists, artist)

fun artistMixPopularTracksAfterLoad(
    popularTracksByArtistId: Map<String, List<Track>>,
    artist: Artist,
    tracks: List<Track>,
): Map<String, List<Track>> =
    popularTracksByArtistId + (artist.id.value to tracks)

fun artistMixPopularTracksAfterRemove(
    popularTracksByArtistId: Map<String, List<Track>>,
    artist: Artist,
): Map<String, List<Track>> =
    popularTracksByArtistId - artist.id.value

data class ArtistMixGeneratedQueue(
    val artists: List<Artist>,
    val popularTracks: List<Track>,
)

fun artistMixGeneratedQueue(
    selectedArtists: List<Artist>,
    popularTracksByArtistId: Map<String, List<Track>>,
): ArtistMixGeneratedQueue =
    ArtistMixGeneratedQueue(
        artists = selectedArtists.distinctBy { it.id },
        popularTracks = artistMixPopularQueue(selectedArtists, popularTracksByArtistId),
    )

fun albumMixLoadSuggestionsEmptyStatus(initial: Boolean): String =
    if (initial) "No album suggestions yet." else "No albums matched."

fun albumMixLoadSuggestionsFailureStatus(initial: Boolean, error: Throwable): String =
    error.message ?: if (initial) "Could not load album suggestions." else "Could not search albums."

fun albumMixLoadTracksStatus(album: Album): String =
    "Loading ${album.title} songs..."

fun albumMixEmptyTracksStatus(album: Album): String =
    "${album.title} did not return tracks."

fun albumMixLoadTracksFailureStatus(album: Album, error: Throwable): String =
    error.message ?: "Could not load ${album.title} songs."

fun albumMixRelatedSuggestionsFailureStatus(error: Throwable): String =
    error.message ?: "Could not load related albums."

fun albumMixSelectedAfterSelect(selectedAlbums: List<Album>, album: Album): List<Album> =
    albumMixSelectedAlbumsAfterSelect(selectedAlbums, album)

fun albumMixSelectedAfterRemove(selectedAlbums: List<Album>, album: Album): List<Album> =
    albumMixSelectedAlbumsAfterRemove(selectedAlbums, album)

fun albumMixTracksAfterLoad(
    tracksByAlbumId: Map<String, List<Track>>,
    album: Album,
    tracks: List<Track>,
): Map<String, List<Track>> =
    tracksByAlbumId + (album.id.value to tracks)

fun albumMixTracksAfterRemove(
    tracksByAlbumId: Map<String, List<Track>>,
    album: Album,
): Map<String, List<Track>> =
    tracksByAlbumId - album.id.value

data class AlbumMixGeneratedQueue(
    val albums: List<Album>,
    val selectedTracks: List<Track>,
)

fun albumMixGeneratedQueue(
    selectedAlbums: List<Album>,
    tracksByAlbumId: Map<String, List<Track>>,
): AlbumMixGeneratedQueue =
    AlbumMixGeneratedQueue(
        albums = selectedAlbums.distinctBy { it.id },
        selectedTracks = albumMixTrackQueue(selectedAlbums, tracksByAlbumId),
    )

fun genreMixLoadSuggestionsEmptyStatus(): String =
    "No genres matched."

fun genreMixLoadSuggestionsFailureStatus(error: Throwable): String =
    error.message ?: "Could not load genres."

fun genreMixSelectedAfterSelect(selectedGenres: List<Genre>, genre: Genre): List<Genre> =
    genreMixSelectedGenresAfterSelect(selectedGenres, genre)

fun genreMixSelectedAfterRemove(selectedGenres: List<Genre>, genre: Genre): List<Genre> =
    genreMixSelectedGenresAfterRemove(selectedGenres, genre)

data class GenreMixGeneratedQueue(
    val genres: List<Genre>,
)

fun genreMixGeneratedQueue(selectedGenres: List<Genre>): GenreMixGeneratedQueue =
    GenreMixGeneratedQueue(
        genres = selectedGenres.distinctBy { it.name.lowercase() },
    )
