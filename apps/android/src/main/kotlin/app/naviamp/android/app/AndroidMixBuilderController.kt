package app.naviamp.android

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
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
            state.artistMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().initialSuggestions(state.artistMixSelectedArtists)
                }
            }.onSuccess { suggestions ->
                state.artistMixSuggestions = suggestions
                state.artistMixStatus = if (suggestions.isEmpty()) "No artist suggestions yet." else null
            }.onFailure { error ->
                state.artistMixStatus = error.message ?: "Could not load artist suggestions."
            }
            state.artistMixLoading = false
        }
    }

    fun searchArtistSuggestions() {
        scope.launch {
            state.artistMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().searchSuggestions(state.artistMixQuery, state.artistMixSelectedArtists)
                }
            }.onSuccess { suggestions ->
                state.artistMixSuggestions = suggestions
                state.artistMixStatus = if (suggestions.isEmpty()) "No artists matched." else null
            }.onFailure { error ->
                state.artistMixStatus = error.message ?: "Could not search artists."
            }
            state.artistMixLoading = false
        }
    }

    fun selectArtist(artist: Artist) {
        state.artistMixSelectedArtists = artistMixSelectedArtistsAfterSelect(state.artistMixSelectedArtists, artist)
        state.artistMixStatus = "Loading ${artist.name} songs..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().popularTracks(artist)
                }
            }.onSuccess { tracks ->
                state.artistMixPopularTracksByArtistId =
                    state.artistMixPopularTracksByArtistId + (artist.id.value to tracks)
                state.artistMixStatus = if (tracks.isEmpty()) "${artist.name} popular songs were not matched." else null
            }.onFailure { error ->
                state.artistMixStatus = error.message ?: "Could not load ${artist.name} songs."
            }
        }
        scope.launch {
            state.artistMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    artistMixBuilderService().relatedSuggestions(state.artistMixSelectedArtists, artist)
                }
            }.onSuccess { suggestions ->
                state.artistMixSuggestions = suggestions
            }.onFailure { error ->
                state.artistMixStatus = error.message ?: "Could not load similar artists."
            }
            state.artistMixLoading = false
        }
    }

    fun selectArtistByItemId(itemId: String) {
        state.artistMixSuggestions.firstOrNull { it.id.value == itemId }?.let(::selectArtist)
    }

    fun removeArtistByItemId(itemId: String) {
        val artist = state.artistMixSelectedArtists.firstOrNull { it.id.value == itemId } ?: return
        state.artistMixSelectedArtists = artistMixSelectedArtistsAfterRemove(state.artistMixSelectedArtists, artist)
        state.artistMixPopularTracksByArtistId = state.artistMixPopularTracksByArtistId - artist.id.value
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
            state.albumMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().initialSuggestions(state.albumMixSelectedAlbums)
                }
            }.onSuccess { suggestions ->
                state.albumMixSuggestions = suggestions
                state.albumMixStatus = if (suggestions.isEmpty()) "No album suggestions yet." else null
            }.onFailure { error ->
                state.albumMixStatus = error.message ?: "Could not load album suggestions."
            }
            state.albumMixLoading = false
        }
    }

    fun searchAlbumSuggestions() {
        scope.launch {
            state.albumMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().searchSuggestions(state.albumMixQuery, state.albumMixSelectedAlbums)
                }
            }.onSuccess { suggestions ->
                state.albumMixSuggestions = suggestions
                state.albumMixStatus = if (suggestions.isEmpty()) "No albums matched." else null
            }.onFailure { error ->
                state.albumMixStatus = error.message ?: "Could not search albums."
            }
            state.albumMixLoading = false
        }
    }

    fun selectAlbum(album: Album) {
        state.albumMixSelectedAlbums = albumMixSelectedAlbumsAfterSelect(state.albumMixSelectedAlbums, album)
        state.albumMixStatus = "Loading ${album.title} songs..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().selectedTracks(album)
                }
            }.onSuccess { tracks ->
                state.albumMixTracksByAlbumId = state.albumMixTracksByAlbumId + (album.id.value to tracks)
                state.albumMixStatus = if (tracks.isEmpty()) "${album.title} did not return tracks." else null
            }.onFailure { error ->
                state.albumMixStatus = error.message ?: "Could not load ${album.title} songs."
            }
        }
        scope.launch {
            state.albumMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    albumMixBuilderService().relatedSuggestions(state.albumMixSelectedAlbums, album)
                }
            }.onSuccess { suggestions ->
                state.albumMixSuggestions = suggestions
            }.onFailure { error ->
                state.albumMixStatus = error.message ?: "Could not load related albums."
            }
            state.albumMixLoading = false
        }
    }

    fun selectAlbumByItemId(itemId: String) {
        state.albumMixSuggestions.firstOrNull { it.id.value == itemId }?.let(::selectAlbum)
    }

    fun removeAlbumByItemId(itemId: String) {
        val album = state.albumMixSelectedAlbums.firstOrNull { it.id.value == itemId } ?: return
        state.albumMixSelectedAlbums = albumMixSelectedAlbumsAfterRemove(state.albumMixSelectedAlbums, album)
        state.albumMixTracksByAlbumId = state.albumMixTracksByAlbumId - album.id.value
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
            state.genreMixLoading = true
            runCatching {
                withContext(Dispatchers.IO) {
                    genreMixBuilderService().searchSuggestions(state.genreMixQuery, state.genreMixSelectedGenres)
                }
            }.onSuccess { suggestions ->
                state.genreMixSuggestions = suggestions
                state.genreMixStatus = if (suggestions.isEmpty()) "No genres matched." else null
            }.onFailure { error ->
                state.genreMixStatus = error.message ?: "Could not load genres."
            }
            state.genreMixLoading = false
        }
    }

    fun selectGenreByItemId(itemId: String) {
        val genre = state.genreMixSuggestions.firstOrNull { it.name == itemId } ?: return
        state.genreMixSelectedGenres = genreMixSelectedGenresAfterSelect(state.genreMixSelectedGenres, genre)
        refreshGenreSuggestions()
    }

    fun removeGenreByItemId(itemId: String) {
        val genre = state.genreMixSelectedGenres.firstOrNull { it.name == itemId } ?: return
        state.genreMixSelectedGenres = genreMixSelectedGenresAfterRemove(state.genreMixSelectedGenres, genre)
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
        startAndroidArtistMixRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            artists = state.artistMixSelectedArtists,
            popularTracks = artistMixPopularQueue(
                state.artistMixSelectedArtists,
                state.artistMixPopularTracksByArtistId,
            ),
            playTrack = playTrack,
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }

    fun playAlbumMix() {
        startAndroidAlbumMixRadio(
            scope = scope,
            state = state,
            queueController = queueController,
            albums = state.albumMixSelectedAlbums,
            selectedTracks = albumMixTrackQueue(state.albumMixSelectedAlbums, state.albumMixTracksByAlbumId),
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
            genres = state.genreMixSelectedGenres,
            playTrack = playTrack,
            providerResponseCacheRepository = storage,
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }
}
