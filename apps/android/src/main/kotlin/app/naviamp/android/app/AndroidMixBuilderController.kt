package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.genremix.GenreMixBuilderService
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
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.settings.RecentRadioStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidMixBuilderController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val queueController: PlaybackQueueController,
    private val storage: AndroidStorageDependencies,
    private val artistMixBuilderService: () -> ArtistMixBuilderService,
    private val albumMixBuilderService: () -> AlbumMixBuilderService,
    private val genreMixBuilderService: () -> GenreMixBuilderService,
    private val playTrack: (Track, List<Track>) -> Unit,
    private val rememberRecentRadioStream: (RecentRadioStream) -> Unit,
) {
    fun refreshArtistInitialSuggestions() {
        scope.launch {
            artistSuggestionCoordinator().load(
                emptyStatus = artistMixLoadSuggestionsEmptyStatus(initial = true),
                failureStatus = { error -> artistMixLoadSuggestionsFailureStatus(initial = true, error) },
                suggestions = {
                    withContext(Dispatchers.IO) {
                        artistMixBuilderService().initialSuggestions(state.artistMixSelectedArtists)
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
                        artistMixBuilderService().searchSuggestions(state.artistMixQuery, state.artistMixSelectedArtists)
                    }
                },
            )
        }
    }

    fun selectArtist(artist: Artist) {
        state.artistMixSelectedArtists = artistMixSelectedAfterSelect(state.artistMixSelectedArtists, artist)
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
                        artistMixBuilderService().relatedSuggestions(state.artistMixSelectedArtists, artist)
                    }
                },
            )
        }
    }

    fun selectArtistByItemId(itemId: String) {
        state.artistMixSuggestions.firstOrNull { it.id.value == itemId }?.let(::selectArtist)
    }

    fun removeArtistByItemId(itemId: String) {
        val artist = state.artistMixSelectedArtists.firstOrNull { it.id.value == itemId } ?: return
        state.artistMixSelectedArtists = artistMixSelectedAfterRemove(state.artistMixSelectedArtists, artist)
        state.artistMixPopularTracksByArtistId = artistMixPopularTracksAfterRemove(state.artistMixPopularTracksByArtistId, artist)
        refreshArtistInitialSuggestions()
    }

    fun resetArtistBuilder() {
        state.artistMixQuery = ""
        state.artistMixSelectedArtists = emptyList()
        state.artistMixSuggestions = emptyList()
        state.artistMixPopularTracksByArtistId = emptyMap()
        state.artistMixStatus = null
        state.artistMixLoading = false
        refreshArtistInitialSuggestions()
    }

    fun refreshAlbumInitialSuggestions() {
        scope.launch {
            albumSuggestionCoordinator().load(
                emptyStatus = albumMixLoadSuggestionsEmptyStatus(initial = true),
                failureStatus = { error -> albumMixLoadSuggestionsFailureStatus(initial = true, error) },
                suggestions = {
                    withContext(Dispatchers.IO) {
                        albumMixBuilderService().initialSuggestions(state.albumMixSelectedAlbums)
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
                        albumMixBuilderService().searchSuggestions(state.albumMixQuery, state.albumMixSelectedAlbums)
                    }
                },
            )
        }
    }

    fun selectAlbum(album: Album) {
        state.albumMixSelectedAlbums = albumMixSelectedAfterSelect(state.albumMixSelectedAlbums, album)
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
                        albumMixBuilderService().relatedSuggestions(state.albumMixSelectedAlbums, album)
                    }
                },
            )
        }
    }

    fun selectAlbumByItemId(itemId: String) {
        state.albumMixSuggestions.firstOrNull { it.id.value == itemId }?.let(::selectAlbum)
    }

    fun removeAlbumByItemId(itemId: String) {
        val album = state.albumMixSelectedAlbums.firstOrNull { it.id.value == itemId } ?: return
        state.albumMixSelectedAlbums = albumMixSelectedAfterRemove(state.albumMixSelectedAlbums, album)
        state.albumMixTracksByAlbumId = albumMixTracksAfterRemove(state.albumMixTracksByAlbumId, album)
        refreshAlbumInitialSuggestions()
    }

    fun resetAlbumBuilder() {
        state.albumMixQuery = ""
        state.albumMixSelectedAlbums = emptyList()
        state.albumMixSuggestions = emptyList()
        state.albumMixTracksByAlbumId = emptyMap()
        state.albumMixStatus = null
        state.albumMixLoading = false
        refreshAlbumInitialSuggestions()
    }

    fun refreshGenreSuggestions() {
        scope.launch {
            genreSuggestionCoordinator().load(
                emptyStatus = genreMixLoadSuggestionsEmptyStatus(),
                failureStatus = ::genreMixLoadSuggestionsFailureStatus,
                suggestions = {
                    withContext(Dispatchers.IO) {
                        genreMixBuilderService().searchSuggestions(state.genreMixQuery, state.genreMixSelectedGenres)
                    }
                },
            )
        }
    }

    fun selectGenreByItemId(itemId: String) {
        val genre = state.genreMixSuggestions.firstOrNull { it.name == itemId } ?: return
        state.genreMixSelectedGenres = genreMixSelectedAfterSelect(state.genreMixSelectedGenres, genre)
        refreshGenreSuggestions()
    }

    fun removeGenreByItemId(itemId: String) {
        val genre = state.genreMixSelectedGenres.firstOrNull { it.name == itemId } ?: return
        state.genreMixSelectedGenres = genreMixSelectedAfterRemove(state.genreMixSelectedGenres, genre)
        refreshGenreSuggestions()
    }

    fun resetGenreBuilder() {
        state.genreMixQuery = ""
        state.genreMixSelectedGenres = emptyList()
        state.genreMixStatus = null
        state.genreMixLoading = false
        refreshGenreSuggestions()
    }

    fun playArtistMix() {
        val queue = artistMixGeneratedQueue(
            state.artistMixSelectedArtists,
            state.artistMixPopularTracksByArtistId,
        )
        startAndroidArtistMixRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            artists = queue.artists,
            popularTracks = queue.popularTracks,
            playTrack = playTrack,
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }

    fun playAlbumMix() {
        val queue = albumMixGeneratedQueue(
            state.albumMixSelectedAlbums,
            state.albumMixTracksByAlbumId,
        )
        startAndroidAlbumMixRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            albums = queue.albums,
            selectedTracks = queue.selectedTracks,
            playTrack = playTrack,
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }

    fun playGenreMix() {
        startAndroidGenreMixRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            genres = genreMixGeneratedQueue(state.genreMixSelectedGenres).genres,
            playTrack = playTrack,
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }

    private fun artistSuggestionCoordinator(): MixSuggestionsCoordinator<Artist> =
        MixSuggestionsCoordinator(
            setLoading = { loading -> state.artistMixLoading = loading },
            setSuggestions = { suggestions -> state.artistMixSuggestions = suggestions },
            setStatus = { status -> state.artistMixStatus = status },
        )

    private fun artistTracksCoordinator(): MixItemTracksCoordinator<Artist> =
        MixItemTracksCoordinator(
            setStatus = { status -> state.artistMixStatus = status },
            setTracks = { artist, tracks ->
                state.artistMixPopularTracksByArtistId = artistMixPopularTracksAfterLoad(
                    state.artistMixPopularTracksByArtistId,
                    artist,
                    tracks,
                )
            },
        )

    private fun albumSuggestionCoordinator(): MixSuggestionsCoordinator<Album> =
        MixSuggestionsCoordinator(
            setLoading = { loading -> state.albumMixLoading = loading },
            setSuggestions = { suggestions -> state.albumMixSuggestions = suggestions },
            setStatus = { status -> state.albumMixStatus = status },
        )

    private fun albumTracksCoordinator(): MixItemTracksCoordinator<Album> =
        MixItemTracksCoordinator(
            setStatus = { status -> state.albumMixStatus = status },
            setTracks = { album, tracks ->
                state.albumMixTracksByAlbumId = albumMixTracksAfterLoad(
                    state.albumMixTracksByAlbumId,
                    album,
                    tracks,
                )
            },
        )

    private fun genreSuggestionCoordinator(): MixSuggestionsCoordinator<Genre> =
        MixSuggestionsCoordinator(
            setLoading = { loading -> state.genreMixLoading = loading },
            setSuggestions = { suggestions -> state.genreMixSuggestions = suggestions },
            setStatus = { status -> state.genreMixStatus = status },
        )
}
