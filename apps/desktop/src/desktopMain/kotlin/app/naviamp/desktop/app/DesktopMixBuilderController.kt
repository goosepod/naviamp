package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.artistmix.artistMixSelectedArtistsAfterRemove
import app.naviamp.domain.artistmix.artistMixSelectedArtistsAfterSelect
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.albummix.albumMixSelectedAlbumsAfterRemove
import app.naviamp.domain.albummix.albumMixSelectedAlbumsAfterSelect
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.genremix.genreMixSelectedGenresAfterRemove
import app.naviamp.domain.genremix.genreMixSelectedGenresAfterSelect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DesktopMixBuilderController(
    private val scope: CoroutineScope,
    private val artistMixBuilderService: () -> ArtistMixBuilderService,
    private val albumMixBuilderService: () -> AlbumMixBuilderService,
    private val genreMixBuilderService: () -> GenreMixBuilderService,
    private val artistMixQuery: () -> String,
    private val setArtistMixQuery: (String) -> Unit,
    private val artistMixSelectedArtists: () -> List<Artist>,
    private val artistMixSuggestions: () -> List<Artist>,
    private val setArtistMixSelectedArtists: (List<Artist>) -> Unit,
    private val setArtistMixSuggestions: (List<Artist>) -> Unit,
    private val artistMixPopularTracksByArtistId: () -> Map<String, List<Track>>,
    private val setArtistMixPopularTracksByArtistId: (Map<String, List<Track>>) -> Unit,
    private val setArtistMixStatus: (String?) -> Unit,
    private val setArtistMixLoading: (Boolean) -> Unit,
    private val albumMixQuery: () -> String,
    private val setAlbumMixQuery: (String) -> Unit,
    private val albumMixSelectedAlbums: () -> List<Album>,
    private val albumMixSuggestions: () -> List<Album>,
    private val setAlbumMixSelectedAlbums: (List<Album>) -> Unit,
    private val setAlbumMixSuggestions: (List<Album>) -> Unit,
    private val albumMixTracksByAlbumId: () -> Map<String, List<Track>>,
    private val setAlbumMixTracksByAlbumId: (Map<String, List<Track>>) -> Unit,
    private val setAlbumMixStatus: (String?) -> Unit,
    private val setAlbumMixLoading: (Boolean) -> Unit,
    private val genreMixQuery: () -> String,
    private val setGenreMixQuery: (String) -> Unit,
    private val genreMixSelectedGenres: () -> List<Genre>,
    private val genreMixSuggestions: () -> List<Genre>,
    private val setGenreMixSelectedGenres: (List<Genre>) -> Unit,
    private val setGenreMixSuggestions: (List<Genre>) -> Unit,
    private val setGenreMixStatus: (String?) -> Unit,
    private val setGenreMixLoading: (Boolean) -> Unit,
) {
    fun refreshArtistInitialSuggestions() {
        scope.launch {
            setArtistMixLoading(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().initialSuggestions(artistMixSelectedArtists())
                }
            }.onSuccess { suggestions ->
                setArtistMixSuggestions(suggestions)
                setArtistMixStatus(if (suggestions.isEmpty()) "No artist suggestions yet." else null)
            }.onFailure { error ->
                setArtistMixStatus(error.message ?: "Could not load artist suggestions.")
            }
            setArtistMixLoading(false)
        }
    }

    fun searchArtistSuggestions() {
        scope.launch {
            setArtistMixLoading(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().searchSuggestions(artistMixQuery(), artistMixSelectedArtists())
                }
            }.onSuccess { suggestions ->
                setArtistMixSuggestions(suggestions)
                setArtistMixStatus(if (suggestions.isEmpty()) "No artists matched." else null)
            }.onFailure { error ->
                setArtistMixStatus(error.message ?: "Could not search artists.")
            }
            setArtistMixLoading(false)
        }
    }

    fun selectArtist(artist: Artist) {
        setArtistMixSelectedArtists(artistMixSelectedArtistsAfterSelect(artistMixSelectedArtists(), artist))
        setArtistMixStatus("Loading ${artist.name} songs...")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().popularTracks(artist)
                }
            }.onSuccess { tracks ->
                setArtistMixPopularTracksByArtistId(artistMixPopularTracksByArtistId() + (artist.id.value to tracks))
                setArtistMixStatus(if (tracks.isEmpty()) "${artist.name} popular songs were not matched." else null)
            }.onFailure { error ->
                setArtistMixStatus(error.message ?: "Could not load ${artist.name} songs.")
            }
        }
        scope.launch {
            setArtistMixLoading(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().relatedSuggestions(artistMixSelectedArtists(), artist)
                }
            }.onSuccess { suggestions ->
                setArtistMixSuggestions(suggestions)
            }.onFailure { error ->
                setArtistMixStatus(error.message ?: "Could not load similar artists.")
            }
            setArtistMixLoading(false)
        }
    }

    fun removeArtist(artist: Artist) {
        setArtistMixSelectedArtists(artistMixSelectedArtistsAfterRemove(artistMixSelectedArtists(), artist))
        setArtistMixPopularTracksByArtistId(artistMixPopularTracksByArtistId() - artist.id.value)
        refreshArtistInitialSuggestions()
    }

    fun selectArtistByItemId(itemId: String) {
        artistMixSuggestions().firstOrNull { it.id.value == itemId }?.let(::selectArtist)
    }

    fun removeArtistByItemId(itemId: String) {
        artistMixSelectedArtists().firstOrNull { it.id.value == itemId }?.let(::removeArtist)
    }

    fun resetArtistBuilder() {
        setArtistMixQuery("")
        setArtistMixSelectedArtists(emptyList())
        setArtistMixSuggestions(emptyList())
        setArtistMixPopularTracksByArtistId(emptyMap())
        setArtistMixStatus(null)
        setArtistMixLoading(false)
        refreshArtistInitialSuggestions()
    }

    fun refreshAlbumInitialSuggestions() {
        scope.launch {
            setAlbumMixLoading(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().initialSuggestions(albumMixSelectedAlbums())
                }
            }.onSuccess { suggestions ->
                setAlbumMixSuggestions(suggestions)
                setAlbumMixStatus(if (suggestions.isEmpty()) "No album suggestions yet." else null)
            }.onFailure { error ->
                setAlbumMixStatus(error.message ?: "Could not load album suggestions.")
            }
            setAlbumMixLoading(false)
        }
    }

    fun searchAlbumSuggestions() {
        scope.launch {
            setAlbumMixLoading(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().searchSuggestions(albumMixQuery(), albumMixSelectedAlbums())
                }
            }.onSuccess { suggestions ->
                setAlbumMixSuggestions(suggestions)
                setAlbumMixStatus(if (suggestions.isEmpty()) "No albums matched." else null)
            }.onFailure { error ->
                setAlbumMixStatus(error.message ?: "Could not search albums.")
            }
            setAlbumMixLoading(false)
        }
    }

    fun selectAlbum(album: Album) {
        setAlbumMixSelectedAlbums(albumMixSelectedAlbumsAfterSelect(albumMixSelectedAlbums(), album))
        setAlbumMixStatus("Loading ${album.title} songs...")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().selectedTracks(album)
                }
            }.onSuccess { tracks ->
                setAlbumMixTracksByAlbumId(albumMixTracksByAlbumId() + (album.id.value to tracks))
                setAlbumMixStatus(if (tracks.isEmpty()) "${album.title} did not return tracks." else null)
            }.onFailure { error ->
                setAlbumMixStatus(error.message ?: "Could not load ${album.title} songs.")
            }
        }
        scope.launch {
            setAlbumMixLoading(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().relatedSuggestions(albumMixSelectedAlbums(), album)
                }
            }.onSuccess { suggestions ->
                setAlbumMixSuggestions(suggestions)
            }.onFailure { error ->
                setAlbumMixStatus(error.message ?: "Could not load related albums.")
            }
            setAlbumMixLoading(false)
        }
    }

    fun removeAlbum(album: Album) {
        setAlbumMixSelectedAlbums(albumMixSelectedAlbumsAfterRemove(albumMixSelectedAlbums(), album))
        setAlbumMixTracksByAlbumId(albumMixTracksByAlbumId() - album.id.value)
        refreshAlbumInitialSuggestions()
    }

    fun selectAlbumByItemId(itemId: String) {
        albumMixSuggestions().firstOrNull { it.id.value == itemId }?.let(::selectAlbum)
    }

    fun removeAlbumByItemId(itemId: String) {
        albumMixSelectedAlbums().firstOrNull { it.id.value == itemId }?.let(::removeAlbum)
    }

    fun resetAlbumBuilder() {
        setAlbumMixQuery("")
        setAlbumMixSelectedAlbums(emptyList())
        setAlbumMixSuggestions(emptyList())
        setAlbumMixTracksByAlbumId(emptyMap())
        setAlbumMixStatus(null)
        setAlbumMixLoading(false)
        refreshAlbumInitialSuggestions()
    }

    fun refreshGenreSuggestions() {
        scope.launch {
            setGenreMixLoading(true)
            runCatching {
                withContext(Dispatchers.IO) {
                    genreMixBuilderService().searchSuggestions(genreMixQuery(), genreMixSelectedGenres())
                }
            }.onSuccess { suggestions ->
                setGenreMixSuggestions(suggestions)
                setGenreMixStatus(if (suggestions.isEmpty()) "No genres matched." else null)
            }.onFailure { error ->
                setGenreMixStatus(error.message ?: "Could not load genres.")
            }
            setGenreMixLoading(false)
        }
    }

    fun selectGenre(genre: Genre) {
        setGenreMixSelectedGenres(genreMixSelectedGenresAfterSelect(genreMixSelectedGenres(), genre))
        refreshGenreSuggestions()
    }

    fun removeGenre(genre: Genre) {
        setGenreMixSelectedGenres(genreMixSelectedGenresAfterRemove(genreMixSelectedGenres(), genre))
        refreshGenreSuggestions()
    }

    fun selectGenreByItemId(itemId: String) {
        genreMixSuggestions().firstOrNull { it.name == itemId }?.let(::selectGenre)
    }

    fun removeGenreByItemId(itemId: String) {
        genreMixSelectedGenres().firstOrNull { it.name == itemId }?.let(::removeGenre)
    }

    fun resetGenreBuilder() {
        setGenreMixQuery("")
        setGenreMixSelectedGenres(emptyList())
        setGenreMixStatus(null)
        setGenreMixLoading(false)
        refreshGenreSuggestions()
    }
}
