package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.albummix.albumMixTrackQueue
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.artistmix.artistMixPopularQueue
import app.naviamp.domain.artistmix.artistMixSelectedArtistsAfterRemove
import app.naviamp.domain.artistmix.artistMixSelectedArtistsAfterSelect
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.albummix.albumMixSelectedAlbumsAfterRemove
import app.naviamp.domain.albummix.albumMixSelectedAlbumsAfterSelect
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.genremix.genreMixSelectedGenresAfterRemove
import app.naviamp.domain.genremix.genreMixSelectedGenresAfterSelect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.media.withUpdatedAlbum
import app.naviamp.domain.media.withUpdatedArtist
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedGenreMixItemUi
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.toSharedGenreMixItemUi
import app.naviamp.ui.toSharedMediaItemUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun desktopArtistMixItem(
    artist: Artist,
    coverArtUrl: (String?) -> String?,
): SharedMediaItemUi = artist.toSharedMediaItemUi(coverArtUrl = coverArtUrl)

internal fun desktopArtistMixItems(
    artists: List<Artist>,
    coverArtUrl: (String?) -> String?,
): List<SharedMediaItemUi> = artists.map { artist -> desktopArtistMixItem(artist, coverArtUrl) }

internal fun desktopAlbumMixItem(
    album: Album,
    coverArtUrl: (String?) -> String?,
): SharedMediaItemUi = album.toSharedMediaItemUi(coverArtUrl = coverArtUrl)

internal fun desktopAlbumMixItems(
    albums: List<Album>,
    coverArtUrl: (String?) -> String?,
): List<SharedMediaItemUi> = albums.map { album -> desktopAlbumMixItem(album, coverArtUrl) }

internal fun desktopGenreMixItem(genre: Genre): SharedGenreMixItemUi = genre.toSharedGenreMixItemUi()

internal fun desktopGenreMixItems(genres: List<Genre>): List<SharedGenreMixItemUi> =
    genres.map(::desktopGenreMixItem)

internal class DesktopMixBuilderController(
    private val scope: CoroutineScope,
    private val artistMixBuilderService: () -> ArtistMixBuilderService,
    private val albumMixBuilderService: () -> AlbumMixBuilderService,
    private val genreMixBuilderService: () -> GenreMixBuilderService,
) {
    var artistMixQuery by mutableStateOf("")
        private set
    var artistMixSelectedArtists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var artistMixSuggestions by mutableStateOf<List<Artist>>(emptyList())
        private set
    private var artistMixPopularTracksByArtistId by mutableStateOf<Map<String, List<Track>>>(emptyMap())
    private var artistMixStatus by mutableStateOf<String?>(null)
    private var artistMixLoading by mutableStateOf(false)

    var albumMixQuery by mutableStateOf("")
        private set
    var albumMixSelectedAlbums by mutableStateOf<List<Album>>(emptyList())
        private set
    var albumMixSuggestions by mutableStateOf<List<Album>>(emptyList())
        private set
    private var albumMixTracksByAlbumId by mutableStateOf<Map<String, List<Track>>>(emptyMap())
    private var albumMixStatus by mutableStateOf<String?>(null)
    private var albumMixLoading by mutableStateOf(false)

    var genreMixQuery by mutableStateOf("")
        private set
    var genreMixSelectedGenres by mutableStateOf<List<Genre>>(emptyList())
        private set
    var genreMixSuggestions by mutableStateOf<List<Genre>>(emptyList())
        private set
    private var genreMixStatus by mutableStateOf<String?>(null)
    private var genreMixLoading by mutableStateOf(false)

    val artistSuggestionsEmpty: Boolean get() = artistMixSuggestions.isEmpty()
    val albumSuggestionsEmpty: Boolean get() = albumMixSuggestions.isEmpty()
    val genreSuggestionsEmpty: Boolean get() = genreMixSuggestions.isEmpty()

    fun artistUi(coverArtUrl: (String?) -> String?) = SharedArtistMixBuilderUi(
        query = artistMixQuery,
        selectedArtists = desktopArtistMixItems(artistMixSelectedArtists, coverArtUrl),
        suggestedArtists = desktopArtistMixItems(artistMixSuggestions, coverArtUrl),
        status = artistMixStatus,
        loading = artistMixLoading,
    )

    fun albumUi(coverArtUrl: (String?) -> String?) = SharedAlbumMixBuilderUi(
        query = albumMixQuery,
        selectedAlbums = desktopAlbumMixItems(albumMixSelectedAlbums, coverArtUrl),
        suggestedAlbums = desktopAlbumMixItems(albumMixSuggestions, coverArtUrl),
        status = albumMixStatus,
        loading = albumMixLoading,
    )

    fun genreUi() = SharedGenreMixBuilderUi(
        query = genreMixQuery,
        selectedGenres = desktopGenreMixItems(genreMixSelectedGenres),
        suggestedGenres = desktopGenreMixItems(genreMixSuggestions),
        status = genreMixStatus,
        loading = genreMixLoading,
    )

    fun setArtistQuery(query: String) {
        artistMixQuery = query
    }

    fun setAlbumQuery(query: String) {
        albumMixQuery = query
    }

    fun setGenreQuery(query: String) {
        genreMixQuery = query
    }

    fun updateSelectedArtist(artist: Artist) {
        artistMixSelectedArtists = artistMixSelectedArtists.withUpdatedArtist(artist)
    }

    fun updateSuggestedArtist(artist: Artist) {
        artistMixSuggestions = artistMixSuggestions.withUpdatedArtist(artist)
    }

    fun updateSelectedAlbum(album: Album) {
        albumMixSelectedAlbums = albumMixSelectedAlbums.withUpdatedAlbum(album)
    }

    fun updateSuggestedAlbum(album: Album) {
        albumMixSuggestions = albumMixSuggestions.withUpdatedAlbum(album)
    }

    fun playArtistMix(radioController: DesktopRadioController) {
        radioController.playArtistMix(
            artistMixSelectedArtists,
            artistMixPopularQueue(artistMixSelectedArtists, artistMixPopularTracksByArtistId),
        )
    }

    fun playAlbumMix(radioController: DesktopRadioController) {
        radioController.playAlbumMix(
            albumMixSelectedAlbums,
            albumMixTrackQueue(albumMixSelectedAlbums, albumMixTracksByAlbumId),
        )
    }

    fun playGenreMix(radioController: DesktopRadioController) {
        radioController.playGenreMix(genreMixSelectedGenres)
    }

    fun refreshArtistInitialSuggestions() {
        scope.launch {
            artistMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().initialSuggestions(artistMixSelectedArtists)
                }
            }.onSuccess { suggestions ->
                artistMixSuggestions = suggestions
                artistMixStatus = if (suggestions.isEmpty()) "No artist suggestions yet." else null
            }.onFailure { error ->
                artistMixStatus = error.message ?: "Could not load artist suggestions."
            }
            artistMixLoading = false
        }
    }

    fun searchArtistSuggestions() {
        scope.launch {
            artistMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().searchSuggestions(artistMixQuery, artistMixSelectedArtists)
                }
            }.onSuccess { suggestions ->
                artistMixSuggestions = suggestions
                artistMixStatus = if (suggestions.isEmpty()) "No artists matched." else null
            }.onFailure { error ->
                artistMixStatus = error.message ?: "Could not search artists."
            }
            artistMixLoading = false
        }
    }

    fun selectArtist(artist: Artist) {
        artistMixSelectedArtists = artistMixSelectedArtistsAfterSelect(artistMixSelectedArtists, artist)
        artistMixStatus = "Loading ${artist.name} songs..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().popularTracks(artist)
                }
            }.onSuccess { tracks ->
                artistMixPopularTracksByArtistId = artistMixPopularTracksByArtistId + (artist.id.value to tracks)
                artistMixStatus = if (tracks.isEmpty()) "${artist.name} popular songs were not matched." else null
            }.onFailure { error ->
                artistMixStatus = error.message ?: "Could not load ${artist.name} songs."
            }
        }
        scope.launch {
            artistMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().relatedSuggestions(artistMixSelectedArtists, artist)
                }
            }.onSuccess { suggestions ->
                artistMixSuggestions = suggestions
            }.onFailure { error ->
                artistMixStatus = error.message ?: "Could not load similar artists."
            }
            artistMixLoading = false
        }
    }

    fun removeArtist(artist: Artist) {
        artistMixSelectedArtists = artistMixSelectedArtistsAfterRemove(artistMixSelectedArtists, artist)
        artistMixPopularTracksByArtistId = artistMixPopularTracksByArtistId - artist.id.value
        refreshArtistInitialSuggestions()
    }

    fun selectArtistByItemId(itemId: String) {
        artistMixSuggestions.firstOrNull { it.id.value == itemId }?.let(::selectArtist)
    }

    fun removeArtistByItemId(itemId: String) {
        artistMixSelectedArtists.firstOrNull { it.id.value == itemId }?.let(::removeArtist)
    }

    fun resetArtistBuilder() {
        artistMixQuery = ""
        artistMixSelectedArtists = emptyList()
        artistMixSuggestions = emptyList()
        artistMixPopularTracksByArtistId = emptyMap()
        artistMixStatus = null
        artistMixLoading = false
        refreshArtistInitialSuggestions()
    }

    fun refreshAlbumInitialSuggestions() {
        scope.launch {
            albumMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().initialSuggestions(albumMixSelectedAlbums)
                }
            }.onSuccess { suggestions ->
                albumMixSuggestions = suggestions
                albumMixStatus = if (suggestions.isEmpty()) "No album suggestions yet." else null
            }.onFailure { error ->
                albumMixStatus = error.message ?: "Could not load album suggestions."
            }
            albumMixLoading = false
        }
    }

    fun searchAlbumSuggestions() {
        scope.launch {
            albumMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().searchSuggestions(albumMixQuery, albumMixSelectedAlbums)
                }
            }.onSuccess { suggestions ->
                albumMixSuggestions = suggestions
                albumMixStatus = if (suggestions.isEmpty()) "No albums matched." else null
            }.onFailure { error ->
                albumMixStatus = error.message ?: "Could not search albums."
            }
            albumMixLoading = false
        }
    }

    fun selectAlbum(album: Album) {
        albumMixSelectedAlbums = albumMixSelectedAlbumsAfterSelect(albumMixSelectedAlbums, album)
        albumMixStatus = "Loading ${album.title} songs..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().selectedTracks(album)
                }
            }.onSuccess { tracks ->
                albumMixTracksByAlbumId = albumMixTracksByAlbumId + (album.id.value to tracks)
                albumMixStatus = if (tracks.isEmpty()) "${album.title} did not return tracks." else null
            }.onFailure { error ->
                albumMixStatus = error.message ?: "Could not load ${album.title} songs."
            }
        }
        scope.launch {
            albumMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().relatedSuggestions(albumMixSelectedAlbums, album)
                }
            }.onSuccess { suggestions ->
                albumMixSuggestions = suggestions
            }.onFailure { error ->
                albumMixStatus = error.message ?: "Could not load related albums."
            }
            albumMixLoading = false
        }
    }

    fun removeAlbum(album: Album) {
        albumMixSelectedAlbums = albumMixSelectedAlbumsAfterRemove(albumMixSelectedAlbums, album)
        albumMixTracksByAlbumId = albumMixTracksByAlbumId - album.id.value
        refreshAlbumInitialSuggestions()
    }

    fun selectAlbumByItemId(itemId: String) {
        albumMixSuggestions.firstOrNull { it.id.value == itemId }?.let(::selectAlbum)
    }

    fun removeAlbumByItemId(itemId: String) {
        albumMixSelectedAlbums.firstOrNull { it.id.value == itemId }?.let(::removeAlbum)
    }

    fun resetAlbumBuilder() {
        albumMixQuery = ""
        albumMixSelectedAlbums = emptyList()
        albumMixSuggestions = emptyList()
        albumMixTracksByAlbumId = emptyMap()
        albumMixStatus = null
        albumMixLoading = false
        refreshAlbumInitialSuggestions()
    }

    fun refreshGenreSuggestions() {
        scope.launch {
            genreMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    genreMixBuilderService().searchSuggestions(genreMixQuery, genreMixSelectedGenres)
                }
            }.onSuccess { suggestions ->
                genreMixSuggestions = suggestions
                genreMixStatus = if (suggestions.isEmpty()) "No genres matched." else null
            }.onFailure { error ->
                genreMixStatus = error.message ?: "Could not load genres."
            }
            genreMixLoading = false
        }
    }

    fun selectGenre(genre: Genre) {
        genreMixSelectedGenres = genreMixSelectedGenresAfterSelect(genreMixSelectedGenres, genre)
        refreshGenreSuggestions()
    }

    fun removeGenre(genre: Genre) {
        genreMixSelectedGenres = genreMixSelectedGenresAfterRemove(genreMixSelectedGenres, genre)
        refreshGenreSuggestions()
    }

    fun selectGenreByItemId(itemId: String) {
        genreMixSuggestions.firstOrNull { it.name == itemId }?.let(::selectGenre)
    }

    fun removeGenreByItemId(itemId: String) {
        genreMixSelectedGenres.firstOrNull { it.name == itemId }?.let(::removeGenre)
    }

    fun resetGenreBuilder() {
        genreMixQuery = ""
        genreMixSelectedGenres = emptyList()
        genreMixStatus = null
        genreMixLoading = false
        refreshGenreSuggestions()
    }
}
