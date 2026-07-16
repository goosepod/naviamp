package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.media.MediaMetadataMutationController
import app.naviamp.domain.media.MediaTrackLookupSources
import app.naviamp.domain.media.findKnownTrack
import app.naviamp.domain.media.mediaMetadataMutationController
import app.naviamp.domain.media.searchOrAlbumTracksForMediaActions
import app.naviamp.domain.media.selectedTrackPlayback
import app.naviamp.domain.media.withUpdatedAlbum
import app.naviamp.domain.media.withUpdatedArtist
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.applyPlaybackQueueUpdate
import app.naviamp.domain.provider.addToPlaylistErrorMessage
import app.naviamp.domain.provider.addToPlaylistLoadingStatus
import app.naviamp.domain.provider.addTracksToPlaylistApplication
import app.naviamp.domain.provider.PlaylistHomeProjection
import app.naviamp.domain.provider.PendingActionTrackFavorite
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.radio.RadioService
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.DownloadedTrackAction
import app.naviamp.ui.DownloadedTrackActionRequest
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.resolveAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun findAndroidKnownTrack(
    state: AndroidAppState,
    trackId: String,
    activeQueue: List<Track>,
): Track? =
    findKnownTrack(trackId, androidTrackLookupSources(state, activeQueue))

fun selectedAndroidTrackPlayback(
    state: AndroidAppState,
    trackId: String,
    activeQueue: List<Track>,
): Pair<Track, List<Track>>? =
    selectedTrackPlayback(trackId, androidTrackLookupSources(state, activeQueue))
        ?.let { playback -> playback.track to playback.tracks }

private fun androidTrackLookupSources(
    state: AndroidAppState,
    activeQueue: List<Track>,
): MediaTrackLookupSources =
    MediaTrackLookupSources(
        primaryTracks = activeQueue,
        selectedPlaylistTracks = state.selectedPlaylistTracks,
        relatedTracks = state.relatedTracks,
        artistPopularTracks = state.artistPopularTracksByArtistId.values.flatten(),
        fallbackTracks = searchOrAlbumTracksForMediaActions(state.searchResults, state.albumDetail) +
            state.homeState.recentlyPlayedTracks,
    )

fun appendAndroidTracksToQueue(
    state: AndroidAppState,
    playbackQueueController: PlaybackQueueController,
    tracksToAdd: List<Track>,
    label: String = "tracks",
) {
    val update = PlaybackQueueManager().appendTracks(
        currentQueue = state.playbackQueue,
        tracksToAdd = tracksToAdd,
        label = label,
    )
    applyPlaybackQueueUpdate(
        update = update,
        setStatus = { status -> state.status = status },
        replaceQueue = { queue ->
            playbackQueueController.replaceQueue(state.playbackQueue, clearPreparedNext = false)
            playbackQueueController.replaceQueue(queue, clearPreparedNext = false)
            state.playbackQueue = playbackQueueController.queue
        },
    )
}

fun withAndroidKnownTrack(
    state: AndroidAppState,
    selectedTrack: SharedTrackRowUi,
    activeQueue: List<Track>,
    action: (Track) -> Unit,
) {
    val track = findAndroidKnownTrack(state, selectedTrack.id, activeQueue)
    if (track == null) {
        state.status = "Track not found."
    } else {
        action(track)
    }
}

fun withAndroidDownloadedTrack(
    state: AndroidAppState,
    download: NaviampDownloadedTrackUi,
    action: (Track) -> Unit,
) {
    val track = state.downloadedTracks.firstOrNull { it.file.absolutePath == download.id }?.track
    if (track == null) {
        state.status = "Track not found."
    } else {
        action(track)
    }
}

fun playAndroidDownloadedTrack(
    state: AndroidAppState,
    download: NaviampDownloadedTrackUi,
    playTrack: (Track, List<Track>) -> Unit,
) {
    val currentTracks = state.downloadedTracks.map { it.track }
    val track = currentTracks.firstOrNull { it.id.value == download.track.id } ?: return
    playTrack(track, currentTracks)
}

fun playAndroidArtistPopularTracks(
    state: AndroidAppState,
    artistId: String,
    playTrack: (Track, List<Track>) -> Unit,
) {
    val popularTracks = state.artistPopularTracksByArtistId[artistId].orEmpty()
    popularTracks.firstOrNull()?.let { playTrack(it, popularTracks) }
        ?: run { state.status = "No popular tracks matched your library." }
}

fun startAndroidArtistPopularTrackRadio(
    state: AndroidAppState,
    trackId: String,
    activeQueue: List<Track>,
    startTrackRadio: (Track) -> Unit,
) {
    val track = findAndroidKnownTrack(state, trackId, activeQueue)
    if (track == null) {
        state.status = "Track not found."
    } else {
        startTrackRadio(track)
    }
}

fun appendAndroidArtistPopularTracksToQueue(
    state: AndroidAppState,
    playbackQueueController: PlaybackQueueController,
    artistId: String,
) {
    val popularTracks = state.artistPopularTracksByArtistId[artistId].orEmpty()
    if (popularTracks.isEmpty()) {
        state.status = "No popular tracks matched your library."
        return
    }
    val update = PlaybackQueueManager().appendTracks(
        currentQueue = state.playbackQueue,
        tracksToAdd = popularTracks,
        label = "popular tracks",
        existingTracks = state.playbackQueue.tracks,
        deduplicateExisting = true,
    )
    applyPlaybackQueueUpdate(
        update = update,
        setStatus = { status -> state.status = status },
        replaceQueue = { queue ->
            playbackQueueController.replaceQueue(state.playbackQueue, clearPreparedNext = false)
            playbackQueueController.replaceQueue(queue, clearPreparedNext = false)
            state.playbackQueue = playbackQueueController.queue
        },
    )
}

fun updateAndroidNotificationFavoriteState(state: AndroidAppState, track: Track? = state.nowPlaying) {
    AndroidPlaybackNotificationControls.canFavorite =
        track != null && (state.provider?.capabilities?.supportsTrackFavorites ?: (state.activeSourceId != null))
    AndroidPlaybackNotificationControls.isFavorite = track?.favoritedAtIso8601 != null
}

fun applyAndroidTrackMetadataUpdate(
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    updatedTrack: Track,
) {
    androidMediaMetadataMutationController(state, playbackEngine).applyTrackUpdateResult(updatedTrack)
}

fun applyAndroidArtistMetadataUpdate(
    state: AndroidAppState,
    updatedArtist: Artist,
) {
    androidMediaMetadataMutationController(state, playbackEngine = null).applyArtistUpdateResult(updatedArtist)
}

fun applyAndroidAlbumMetadataUpdate(
    state: AndroidAppState,
    updatedAlbum: Album,
) {
    androidMediaMetadataMutationController(state, playbackEngine = null).applyAlbumUpdateResult(updatedAlbum)
}

fun toggleAndroidArtistFavorite(
    scope: CoroutineScope,
    state: AndroidAppState,
    item: SharedMediaItemUi,
    pendingProviderActions: PendingProviderActionRepository? = null,
) {
    scope.launch {
        androidMediaMetadataMutationController(
            state = state,
            playbackEngine = null,
            pendingProviderActions = pendingProviderActions,
        ).toggleArtistFavoriteById(item.id)
    }
}

fun toggleAndroidAlbumFavorite(
    scope: CoroutineScope,
    state: AndroidAppState,
    item: SharedMediaItemUi,
    pendingProviderActions: PendingProviderActionRepository? = null,
) {
    scope.launch {
        androidMediaMetadataMutationController(
            state = state,
            playbackEngine = null,
            pendingProviderActions = pendingProviderActions,
        ).toggleAlbumFavoriteById(item.id)
    }
}

fun toggleAndroidCurrentFavorite(
    scope: CoroutineScope,
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    pendingProviderActions: PendingProviderActionRepository? = null,
) {
    val currentTrack = state.nowPlaying ?: return
    toggleAndroidTrackFavorite(scope, state, playbackEngine, currentTrack, pendingProviderActions)
}

fun toggleAndroidTrackFavorite(
    scope: CoroutineScope,
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    track: Track,
    pendingProviderActions: PendingProviderActionRepository? = null,
) {
    scope.launch {
        val favorite = track.favoritedAtIso8601 == null
        if (state.nowPlaying?.id == track.id) AndroidPlaybackNotificationControls.isFavorite = favorite
        if (state.provider == null && state.activeSourceId != null && pendingProviderActions != null) {
            pendingProviderActions.enqueuePendingProviderAction(
                sourceId = state.activeSourceId!!,
                actionType = PendingActionTrackFavorite,
                entityId = track.id.value,
                boolValue = favorite,
                replaceMatchingEntityAction = true,
            )
            val updatedTrack = track.copy(favoritedAtIso8601 = if (favorite) "local" else null)
            androidMediaMetadataMutationController(
                state = state,
                playbackEngine = playbackEngine,
            ).applyTrackUpdateResult(updatedTrack)
            state.status = if (favorite) {
                "Favorite will sync when you reconnect."
            } else {
                "Favorite removal will sync when you reconnect."
            }
            return@launch
        }
        val result = androidMediaMetadataMutationController(
            state = state,
            playbackEngine = playbackEngine,
            pendingProviderActions = pendingProviderActions,
        ).toggleTrackFavoriteResult(track)
        if (!result.shouldRunPlatformSideEffects && state.nowPlaying?.id == track.id) {
            AndroidPlaybackNotificationControls.isFavorite = !favorite
        }
    }
}

fun setAndroidCurrentTrackRating(
    scope: CoroutineScope,
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    rating: Int?,
) {
    val currentTrack = state.nowPlaying ?: return
    scope.launch {
        androidMediaMetadataMutationController(state, playbackEngine).setTrackRating(currentTrack, rating)
    }
}

private fun androidMediaMetadataMutationController(
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine?,
    pendingProviderActions: PendingProviderActionRepository? = null,
): MediaMetadataMutationController =
    mediaMetadataMutationController(
        provider = provider@{
            val activeProvider = state.provider ?: return@provider null
            val repository = pendingProviderActions ?: return@provider activeProvider
            activeProvider.withAndroidPendingActions(state.activeSourceId, repository)
        },
        favoritedAtIso8601 = { "local" },
        setStatus = { status -> state.status = status },
        trackLookupSources = {
            MediaTrackLookupSources(
                primaryTracks = state.playbackQueue.tracks,
                extraTracks = state.tracks,
                fallbackTracks = searchOrAlbumTracksForMediaActions(state.searchResults, state.albumDetail),
            )
        },
        homeContent = { state.homeState },
        setHomeContent = { content -> state.homeState = content },
        searchResults = { state.searchResults },
        setSearchResults = { results -> state.contentState = state.contentState.copy(searchResults = results) },
        albumDetails = { state.albumDetail },
        setAlbumDetails = { details -> state.contentState = state.contentState.copy(albumDetail = details) },
        artistDetails = { state.artistDetail },
        setArtistDetails = { details -> state.contentState = state.contentState.copy(artistDetail = details) },
        nowPlayingTrack = { state.nowPlaying },
        setNowPlayingTrack = { track -> state.nowPlaying = track },
        tracks = { state.tracks },
        setTracks = { tracks -> state.tracks = tracks },
        extraKnownArtists = { state.artistMixSelectedArtists + state.artistMixSuggestions },
        extraKnownAlbums = { state.albumMixSelectedAlbums + state.albumMixSuggestions },
        updateExtraArtistCollections = { artist ->
            state.artistMixSelectedArtists = state.artistMixSelectedArtists.withUpdatedArtist(artist)
            state.artistMixSuggestions = state.artistMixSuggestions.withUpdatedArtist(artist)
        },
        updateExtraAlbumCollections = { album ->
            state.albumMixSelectedAlbums = state.albumMixSelectedAlbums.withUpdatedAlbum(album)
            state.albumMixSuggestions = state.albumMixSuggestions.withUpdatedAlbum(album)
        },
        afterTrackUpdate = { updatedTrack, updatedNowPlaying ->
            if (playbackEngine != null && updatedNowPlaying?.id == updatedTrack.id) {
                updateAndroidNotificationFavoriteState(state, updatedNowPlaying)
                playbackEngine.updateNotificationMetadata(
                    title = updatedNowPlaying.title,
                    subtitle = updatedNowPlaying.artistName,
                    coverArtUrl = state.provider?.let { updatedNowPlaying.coverArtUrl(it) },
                )
            }
        },
    )

fun addAndroidTrackToPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    track: Track,
    playlist: NaviampPlaylistChoiceUi?,
    newPlaylistName: String? = null,
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    addAndroidTracksToPlaylist(
        scope = scope,
        state = state,
        tracksToAdd = listOf(track),
        playlist = playlist,
        newPlaylistName = newPlaylistName,
        label = "track",
        providerResponseCacheRepository = providerResponseCacheRepository,
    )
}

fun addAndroidTracksToPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    tracksToAdd: List<Track>,
    playlist: NaviampPlaylistChoiceUi?,
    newPlaylistName: String? = null,
    label: String = "tracks",
    providerResponseCacheRepository: ProviderResponseCacheRepository? = null,
) {
    val activeProvider = state.provider ?: return
    val providerResponseService = providerResponseCacheRepository?.let { ProviderResponseService(it) }
    state.playlistActionStatus = addToPlaylistLoadingStatus(label)
    scope.launch {
        with(state) {
            runCatching {
                activeProvider.addTracksToPlaylistApplication(
                    playlistId = playlist?.id,
                    playlistName = playlist?.name,
                    newPlaylistName = newPlaylistName,
                    tracks = tracksToAdd,
                    currentHomeContent = homeState,
                    recentPlaylistIds = recentPlaylistIds,
                    projection = PlaylistHomeProjection.All,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { application ->
                application.playlistListApplication?.let { update ->
                    homeState = update.homeContent
                }
                playlistActionStatus = application.addToPlaylistStatus
                application.connectionStatus?.let { status = it }
                    ?: application.addToPlaylistStatus?.let { status = it }
            }.onFailure { error ->
                playlistActionStatus = addToPlaylistErrorMessage(error, label)
                status = playlistActionStatus.orEmpty()
            }
        }
    }
}

internal class AndroidTrackActionController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val activeQueue: () -> List<Track>,
    private val findKnownTrack: (String) -> Track?,
    private val playTrack: (Track, List<Track>?) -> Unit,
    private val playNextTracks: (List<Track>, String) -> Unit,
    private val appendTracksToQueue: (List<Track>, String) -> Unit,
    private val downloadTrack: (Track) -> Unit,
    private val addTrackToPlaylist: (Track, NaviampPlaylistChoiceUi?, String?) -> Unit,
    private val removeDownload: (NaviampDownloadedTrackUi) -> Unit,
    private val toggleTrackFavorite: (Track) -> Unit,
    private val openTrackAlbum: (Track) -> Unit,
    private val openTrackArtist: (Track, String?, String?) -> Unit,
) {
    fun handleDownloadedTrackSelected(download: NaviampDownloadedTrackUi) {
        playAndroidDownloadedTrack(state, download) { track, queue -> playTrack(track, queue) }
    }

    fun handleDownloadedTrackAddToPlaylist(download: NaviampDownloadedTrackUi, playlist: NaviampPlaylistChoiceUi?) {
        withAndroidDownloadedTrack(state, download) { track -> addTrackToPlaylist(track, playlist, null) }
    }

    fun handleDownloadedTrackCreatePlaylistAndAdd(download: NaviampDownloadedTrackUi, name: String) {
        withAndroidDownloadedTrack(state, download) { track -> addTrackToPlaylist(track, null, name) }
    }

    fun handleDownloadedTrackAction(request: DownloadedTrackActionRequest) {
        when (request.action) {
            DownloadedTrackAction.Select -> handleDownloadedTrackSelected(request.download)
            DownloadedTrackAction.AddToPlaylist ->
                handleDownloadedTrackAddToPlaylist(request.download, request.playlistChoice)
            DownloadedTrackAction.CreatePlaylistAndAdd ->
                request.playlistName?.let { name -> handleDownloadedTrackCreatePlaylistAndAdd(request.download, name) }
            DownloadedTrackAction.Remove -> removeDownload(request.download)
        }
    }

    fun handleTrackAction(request: SharedTrackRowActionRequest) {
        val resolved = request.resolveAction(
            knownTracks = activeQueue(),
            fallbackTrack = findKnownTrack(request.track.id),
        )
        val track = resolved.track
        if (track == null) {
            state.status = "Track not found."
            return
        }
        when (resolved.action) {
            SharedTrackRowAction.Select,
            SharedTrackRowAction.StartRadio,
            -> Unit
            SharedTrackRowAction.PlayNext -> playNextTracks(listOf(track), "track")
            SharedTrackRowAction.PlayTrackRadioNext -> playTrackRadioNext(track)
            SharedTrackRowAction.AddTrackRadioToQueue -> addTrackRadioToQueue(track)
            SharedTrackRowAction.AddToQueue -> appendTracksToQueue(listOf(track), "track")
            SharedTrackRowAction.Download -> downloadTrack(track)
            SharedTrackRowAction.AddToPlaylist -> addTrackToPlaylist(track, resolved.playlistChoice, null)
            SharedTrackRowAction.CreatePlaylistAndAdd ->
                resolved.playlistName?.let { name -> addTrackToPlaylist(track, null, name) }
            SharedTrackRowAction.ToggleFavorite -> toggleTrackFavorite(track)
            SharedTrackRowAction.GoToAlbum -> openTrackAlbum(track)
            SharedTrackRowAction.GoToArtist -> openTrackArtist(track, resolved.artistId, resolved.artistName)
        }
    }

    fun playTrackRadioNext(track: Track) {
        enqueueTrackRadio(track, insertNext = true)
    }

    fun addTrackRadioToQueue(track: Track) {
        enqueueTrackRadio(track, insertNext = false)
    }

    private fun enqueueTrackRadio(
        track: Track,
        insertNext: Boolean,
    ) {
        val provider = state.provider ?: return
        state.status = "Loading track radio..."
        scope.launch {
            try {
                val tracks = withContext(Dispatchers.IO) {
                    RadioService(
                        provider = provider,
                        count = AndroidInitialSimilarRadioCount,
                        tuning = state.playbackSettings.radioTuning,
                    )
                        .trackRadio(track, state.playbackSettings.sonicSimilarityEnabled)
                }.filterNot { radioTrack -> radioTrack.id == track.id }
                if (tracks.isEmpty()) {
                    state.status = "Track radio did not return any tracks."
                    return@launch
                }
                if (insertNext) {
                    playNextTracks(tracks, "track radio")
                } else {
                    appendTracksToQueue(tracks, "track radio")
                }
            } catch (error: Exception) {
                state.status = error.message ?: "Could not load track radio."
            }
        }
    }

    fun handlePlaylistTrackSelected(selectedTrack: SharedTrackRowUi) {
        val track = state.selectedPlaylistTracks.firstOrNull { it.id.value == selectedTrack.id }
            ?: findKnownTrack(selectedTrack.id)
        if (track == null) {
            state.status = "Track not found."
            return
        }
        playTrack(track, state.selectedPlaylistTracks.ifEmpty { listOf(track) })
    }

    fun handleNowPlayingAddToPlaylist(playlist: NaviampPlaylistChoiceUi?) {
        val currentTrack = state.nowPlaying ?: return
        addTrackToPlaylist(currentTrack, playlist, null)
    }

    fun handleNowPlayingCreatePlaylistAndAdd(name: String) {
        val currentTrack = state.nowPlaying ?: return
        addTrackToPlaylist(currentTrack, null, name)
    }
}
