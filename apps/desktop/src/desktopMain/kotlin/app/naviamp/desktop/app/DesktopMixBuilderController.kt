package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.genremix.GenreMixBuilderService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.media.withUpdatedAlbum
import app.naviamp.domain.media.withUpdatedArtist
import app.naviamp.domain.mixbuilder.MixItemTracksCoordinator
import app.naviamp.domain.mixbuilder.MixSuggestionsCoordinator
import app.naviamp.domain.mixbuilder.albumMixEmptyTracksStatus
import app.naviamp.domain.mixbuilder.albumMixGeneratedQueue
import app.naviamp.domain.mixbuilder.albumMixLoadSuggestionsEmptyStatus
import app.naviamp.domain.mixbuilder.albumMixLoadSuggestionsFailureStatus
import app.naviamp.domain.mixbuilder.albumMixLoadTracksFailureStatus
import app.naviamp.domain.mixbuilder.albumMixLoadTracksStatus
import app.naviamp.domain.mixbuilder.albumMixRelatedSuggestionsFailureStatus
import app.naviamp.domain.mixbuilder.albumMixSelectedAfterRemove
import app.naviamp.domain.mixbuilder.albumMixSelectedAfterSelect
import app.naviamp.domain.mixbuilder.albumMixTracksAfterLoad
import app.naviamp.domain.mixbuilder.albumMixTracksAfterRemove
import app.naviamp.domain.mixbuilder.artistMixEmptyTracksStatus
import app.naviamp.domain.mixbuilder.artistMixGeneratedQueue
import app.naviamp.domain.mixbuilder.artistMixLoadSuggestionsEmptyStatus
import app.naviamp.domain.mixbuilder.artistMixLoadSuggestionsFailureStatus
import app.naviamp.domain.mixbuilder.artistMixLoadTracksFailureStatus
import app.naviamp.domain.mixbuilder.artistMixLoadTracksStatus
import app.naviamp.domain.mixbuilder.artistMixPopularTracksAfterLoad
import app.naviamp.domain.mixbuilder.artistMixPopularTracksAfterRemove
import app.naviamp.domain.mixbuilder.artistMixRelatedSuggestionsFailureStatus
import app.naviamp.domain.mixbuilder.artistMixSelectedAfterRemove
import app.naviamp.domain.mixbuilder.artistMixSelectedAfterSelect
import app.naviamp.domain.mixbuilder.genreMixGeneratedQueue
import app.naviamp.domain.mixbuilder.genreMixLoadSuggestionsEmptyStatus
import app.naviamp.domain.mixbuilder.genreMixLoadSuggestionsFailureStatus
import app.naviamp.domain.mixbuilder.genreMixSelectedAfterRemove
import app.naviamp.domain.mixbuilder.genreMixSelectedAfterSelect
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
        val queue = artistMixGeneratedQueue(artistMixSelectedArtists, artistMixPopularTracksByArtistId)
        radioController.playArtistMix(
            queue.artists,
            queue.popularTracks,
        )
    }

    fun playAlbumMix(radioController: DesktopRadioController) {
        val queue = albumMixGeneratedQueue(albumMixSelectedAlbums, albumMixTracksByAlbumId)
        radioController.playAlbumMix(
            queue.albums,
            queue.selectedTracks,
        )
    }

    fun playGenreMix(radioController: DesktopRadioController) {
        radioController.playGenreMix(genreMixGeneratedQueue(genreMixSelectedGenres).genres)
    }

    fun refreshArtistInitialSuggestions() {
        scope.launch {
            artistSuggestionCoordinator().load(
                emptyStatus = artistMixLoadSuggestionsEmptyStatus(initial = true),
                failureStatus = { error -> artistMixLoadSuggestionsFailureStatus(initial = true, error) },
                suggestions = {
                    withContext(Dispatchers.IO) {
                        artistMixBuilderService().initialSuggestions(artistMixSelectedArtists)
                    }
                },
            )
        }
    }

    fun searchArtistSuggestions() {
        scope.launch {
            artistSuggestionCoordinator().load(
                emptyStatus = artistMixLoadSuggestionsEmptyStatus(initial = false),
                failureStatus = { error -> artistMixLoadSuggestionsFailureStatus(initial = false, error) },
                suggestions = {
                    withContext(Dispatchers.IO) {
                        artistMixBuilderService().searchSuggestions(artistMixQuery, artistMixSelectedArtists)
                    }
                },
            )
        }
    }

    fun selectArtist(artist: Artist) {
        artistMixSelectedArtists = artistMixSelectedAfterSelect(artistMixSelectedArtists, artist)
        scope.launch {
            artistTracksCoordinator().load(
                item = artist,
                loadingStatus = artistMixLoadTracksStatus(artist),
                emptyStatus = artistMixEmptyTracksStatus(artist),
                failureStatus = { error -> artistMixLoadTracksFailureStatus(artist, error) },
                tracks = {
                    withContext(Dispatchers.IO) {
                        artistMixBuilderService().popularTracks(artist)
                    }
                },
            )
        }
        scope.launch {
            artistSuggestionCoordinator().load(
                emptyStatus = artistMixLoadSuggestionsEmptyStatus(initial = true),
                failureStatus = ::artistMixRelatedSuggestionsFailureStatus,
                suggestions = {
                    withContext(Dispatchers.IO) {
                        artistMixBuilderService().relatedSuggestions(artistMixSelectedArtists, artist)
                    }
                },
            )
        }
    }

    fun removeArtist(artist: Artist) {
        artistMixSelectedArtists = artistMixSelectedAfterRemove(artistMixSelectedArtists, artist)
        artistMixPopularTracksByArtistId = artistMixPopularTracksAfterRemove(artistMixPopularTracksByArtistId, artist)
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
            albumSuggestionCoordinator().load(
                emptyStatus = albumMixLoadSuggestionsEmptyStatus(initial = true),
                failureStatus = { error -> albumMixLoadSuggestionsFailureStatus(initial = true, error) },
                suggestions = {
                    withContext(Dispatchers.IO) {
                        albumMixBuilderService().initialSuggestions(albumMixSelectedAlbums)
                    }
                },
            )
        }
    }

    fun searchAlbumSuggestions() {
        scope.launch {
            albumSuggestionCoordinator().load(
                emptyStatus = albumMixLoadSuggestionsEmptyStatus(initial = false),
                failureStatus = { error -> albumMixLoadSuggestionsFailureStatus(initial = false, error) },
                suggestions = {
                    withContext(Dispatchers.IO) {
                        albumMixBuilderService().searchSuggestions(albumMixQuery, albumMixSelectedAlbums)
                    }
                },
            )
        }
    }

    fun selectAlbum(album: Album) {
        albumMixSelectedAlbums = albumMixSelectedAfterSelect(albumMixSelectedAlbums, album)
        scope.launch {
            albumTracksCoordinator().load(
                item = album,
                loadingStatus = albumMixLoadTracksStatus(album),
                emptyStatus = albumMixEmptyTracksStatus(album),
                failureStatus = { error -> albumMixLoadTracksFailureStatus(album, error) },
                tracks = {
                    withContext(Dispatchers.IO) {
                        albumMixBuilderService().selectedTracks(album)
                    }
                },
            )
        }
        scope.launch {
            albumSuggestionCoordinator().load(
                emptyStatus = albumMixLoadSuggestionsEmptyStatus(initial = true),
                failureStatus = ::albumMixRelatedSuggestionsFailureStatus,
                suggestions = {
                    withContext(Dispatchers.IO) {
                        albumMixBuilderService().relatedSuggestions(albumMixSelectedAlbums, album)
                    }
                },
            )
        }
    }

    fun removeAlbum(album: Album) {
        albumMixSelectedAlbums = albumMixSelectedAfterRemove(albumMixSelectedAlbums, album)
        albumMixTracksByAlbumId = albumMixTracksAfterRemove(albumMixTracksByAlbumId, album)
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
            genreSuggestionCoordinator().load(
                emptyStatus = genreMixLoadSuggestionsEmptyStatus(),
                failureStatus = ::genreMixLoadSuggestionsFailureStatus,
                suggestions = {
                    withContext(Dispatchers.IO) {
                        genreMixBuilderService().searchSuggestions(genreMixQuery, genreMixSelectedGenres)
                    }
                },
            )
        }
    }

    fun selectGenre(genre: Genre) {
        genreMixSelectedGenres = genreMixSelectedAfterSelect(genreMixSelectedGenres, genre)
        refreshGenreSuggestions()
    }

    fun removeGenre(genre: Genre) {
        genreMixSelectedGenres = genreMixSelectedAfterRemove(genreMixSelectedGenres, genre)
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

    private fun artistSuggestionCoordinator(): MixSuggestionsCoordinator<Artist> =
        MixSuggestionsCoordinator(
            setLoading = { loading -> artistMixLoading = loading },
            setSuggestions = { suggestions -> artistMixSuggestions = suggestions },
            setStatus = { status -> artistMixStatus = status },
        )

    private fun artistTracksCoordinator(): MixItemTracksCoordinator<Artist> =
        MixItemTracksCoordinator(
            setStatus = { status -> artistMixStatus = status },
            setTracks = { artist, tracks ->
                artistMixPopularTracksByArtistId = artistMixPopularTracksAfterLoad(
                    artistMixPopularTracksByArtistId,
                    artist,
                    tracks,
                )
            },
        )

    private fun albumSuggestionCoordinator(): MixSuggestionsCoordinator<Album> =
        MixSuggestionsCoordinator(
            setLoading = { loading -> albumMixLoading = loading },
            setSuggestions = { suggestions -> albumMixSuggestions = suggestions },
            setStatus = { status -> albumMixStatus = status },
        )

    private fun albumTracksCoordinator(): MixItemTracksCoordinator<Album> =
        MixItemTracksCoordinator(
            setStatus = { status -> albumMixStatus = status },
            setTracks = { album, tracks ->
                albumMixTracksByAlbumId = albumMixTracksAfterLoad(
                    albumMixTracksByAlbumId,
                    album,
                    tracks,
                )
            },
        )

    private fun genreSuggestionCoordinator(): MixSuggestionsCoordinator<Genre> =
        MixSuggestionsCoordinator(
            setLoading = { loading -> genreMixLoading = loading },
            setSuggestions = { suggestions -> genreMixSuggestions = suggestions },
            setStatus = { status -> genreMixStatus = status },
        )
}
