package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.media.MediaMetadataMutationController
import app.naviamp.domain.media.MediaMetadataStateUpdater
import app.naviamp.domain.media.MediaTrackMetadataStateUpdater
import app.naviamp.domain.media.MediaTrackLookupSources
import app.naviamp.domain.media.findKnownTrack
import app.naviamp.domain.media.knownAlbumsForMetadata
import app.naviamp.domain.media.knownArtistsForMetadata
import app.naviamp.domain.media.knownTracksForMediaActions
import app.naviamp.domain.media.searchOrAlbumTracksForMediaActions
import app.naviamp.domain.media.selectedTrackPlayback
import app.naviamp.domain.media.withUpdatedAlbum
import app.naviamp.domain.media.withUpdatedArtist
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.provider.addTracksToPlaylistAndRefresh
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampDownloadedTrackUi
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.SharedMediaItemUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
        fallbackTracks = searchOrAlbumTracksForMediaActions(state.searchResults, state.albumDetail),
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
    state.status = update.status
    if (!update.tracksChanged) return
    playbackQueueController.replaceQueue(state.playbackQueue)
    playbackQueueController.replaceQueue(update.queue)
    state.playbackQueue = playbackQueueController.queue
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
    state.status = update.status
    if (!update.tracksChanged) return
    playbackQueueController.replaceQueue(state.playbackQueue)
    playbackQueueController.replaceQueue(update.queue)
    state.playbackQueue = playbackQueueController.queue
}

fun updateAndroidNotificationFavoriteState(state: AndroidAppState, track: Track? = state.nowPlaying) {
    AndroidPlaybackNotificationControls.canFavorite =
        track != null && state.provider?.capabilities?.supportsTrackFavorites == true
    AndroidPlaybackNotificationControls.isFavorite = track?.favoritedAtIso8601 != null
}

fun applyAndroidTrackMetadataUpdate(
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    updatedTrack: Track,
) {
    val updatedNowPlaying = androidTrackMetadataStateUpdater(state).applyTrackUpdate(updatedTrack)
    if (updatedNowPlaying?.id == updatedTrack.id) {
        updateAndroidNotificationFavoriteState(state, updatedNowPlaying)
        playbackEngine.updateNotificationMetadata(
            title = updatedNowPlaying.title,
            subtitle = updatedNowPlaying.artistName,
            coverArtUrl = state.provider?.let { updatedNowPlaying.coverArtUrl(it) },
        )
    }
}

private fun androidKnownArtists(
    state: AndroidAppState,
): List<Artist> =
    knownArtistsForMetadata(
        homeContent = state.homeState,
        searchResults = state.searchResults,
        artistDetails = state.artistDetail,
        extraArtists = state.artistMixSelectedArtists + state.artistMixSuggestions,
    )

private fun androidKnownAlbums(
    state: AndroidAppState,
): List<Album> =
    knownAlbumsForMetadata(
        homeContent = state.homeState,
        searchResults = state.searchResults,
        albumDetails = state.albumDetail,
        artistDetails = state.artistDetail,
        extraAlbums = state.albumMixSelectedAlbums + state.albumMixSuggestions,
    )

fun applyAndroidArtistMetadataUpdate(
    state: AndroidAppState,
    updatedArtist: Artist,
) {
    androidMediaMetadataStateUpdater(state).applyArtistUpdate(updatedArtist)
}

fun applyAndroidAlbumMetadataUpdate(
    state: AndroidAppState,
    updatedAlbum: Album,
) {
    androidMediaMetadataStateUpdater(state).applyAlbumUpdate(updatedAlbum)
}

fun toggleAndroidArtistFavorite(
    scope: CoroutineScope,
    state: AndroidAppState,
    item: SharedMediaItemUi,
) {
    scope.launch {
        androidMediaMetadataMutationController(state, playbackEngine = null).toggleArtistFavoriteById(item.id)
    }
}

fun toggleAndroidAlbumFavorite(
    scope: CoroutineScope,
    state: AndroidAppState,
    item: SharedMediaItemUi,
) {
    scope.launch {
        androidMediaMetadataMutationController(state, playbackEngine = null).toggleAlbumFavoriteById(item.id)
    }
}

fun toggleAndroidCurrentFavorite(
    scope: CoroutineScope,
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
) {
    val currentTrack = state.nowPlaying ?: return
    scope.launch {
        val favorite = currentTrack.favoritedAtIso8601 == null
        AndroidPlaybackNotificationControls.isFavorite = favorite
        val result = androidMediaMetadataMutationController(state, playbackEngine).toggleTrackFavoriteResult(currentTrack)
        if (!result.shouldRunPlatformSideEffects) {
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
): MediaMetadataMutationController =
    MediaMetadataMutationController(
        provider = { state.provider },
        favoritedAtIso8601 = { "local" },
        setStatus = { status -> state.status = status },
        knownTracks = {
            knownTracksForMediaActions(
                MediaTrackLookupSources(
                    primaryTracks = state.playbackQueue.tracks,
                    extraTracks = state.tracks,
                    fallbackTracks = searchOrAlbumTracksForMediaActions(state.searchResults, state.albumDetail),
                ),
            )
        },
        knownArtists = { androidKnownArtists(state) },
        knownAlbums = { androidKnownAlbums(state) },
        applyTrackUpdate = { updatedTrack ->
            if (playbackEngine != null) {
                applyAndroidTrackMetadataUpdate(state, playbackEngine, updatedTrack)
            } else {
                androidTrackMetadataStateUpdater(state).applyTrackUpdate(updatedTrack)
            }
        },
        applyArtistUpdate = { updatedArtist -> applyAndroidArtistMetadataUpdate(state, updatedArtist) },
        applyAlbumUpdate = { updatedAlbum -> applyAndroidAlbumMetadataUpdate(state, updatedAlbum) },
    )

private fun androidTrackMetadataStateUpdater(
    state: AndroidAppState,
): MediaTrackMetadataStateUpdater =
    MediaTrackMetadataStateUpdater(
        nowPlayingTrack = { state.nowPlaying },
        setNowPlayingTrack = { track -> state.nowPlaying = track },
        searchResults = { state.searchResults },
        setSearchResults = { results -> state.contentState = state.contentState.copy(searchResults = results) },
        albumDetails = { state.albumDetail },
        setAlbumDetails = { details -> state.contentState = state.contentState.copy(albumDetail = details) },
        tracks = { state.tracks },
        setTracks = { tracks -> state.tracks = tracks },
    )

private fun androidMediaMetadataStateUpdater(
    state: AndroidAppState,
): MediaMetadataStateUpdater =
    MediaMetadataStateUpdater(
        homeContent = { state.homeState },
        setHomeContent = { content -> state.homeState = content },
        searchResults = { state.searchResults },
        setSearchResults = { results -> state.contentState = state.contentState.copy(searchResults = results) },
        albumDetails = { state.albumDetail },
        setAlbumDetails = { details -> state.contentState = state.contentState.copy(albumDetail = details) },
        artistDetails = { state.artistDetail },
        setArtistDetails = { details -> state.contentState = state.contentState.copy(artistDetail = details) },
        updateExtraArtistCollections = { artist ->
            state.artistMixSelectedArtists = state.artistMixSelectedArtists.withUpdatedArtist(artist)
            state.artistMixSuggestions = state.artistMixSuggestions.withUpdatedArtist(artist)
        },
        updateExtraAlbumCollections = { album ->
            state.albumMixSelectedAlbums = state.albumMixSelectedAlbums.withUpdatedAlbum(album)
            state.albumMixSuggestions = state.albumMixSuggestions.withUpdatedAlbum(album)
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
    val uniqueTracks = tracksToAdd.distinctBy { it.id }
    if (uniqueTracks.isEmpty()) {
        state.status = "No tracks found."
        return
    }
    state.playlistActionStatus = "Adding $label to playlist..."
    scope.launch {
        with(state) {
            runCatching {
                activeProvider.addTracksToPlaylistAndRefresh(
                    playlistId = playlist?.id,
                    playlistName = playlist?.name,
                    newPlaylistName = newPlaylistName,
                    tracks = uniqueTracks,
                    providerResponseService = providerResponseService,
                )
            }.onSuccess { refresh ->
                refresh.playlists?.let { playlists ->
                    homeState = homeState.copy(playlists = playlists)
                }
                val update = refresh.update
                playlistActionStatus = update.addToPlaylistStatus
                update.connectionStatus?.let { status = it }
                    ?: update.addToPlaylistStatus?.let { status = it }
            }.onFailure { error ->
                playlistActionStatus = error.message ?: "Could not add $label to playlist."
                status = playlistActionStatus.orEmpty()
            }
        }
    }
}
