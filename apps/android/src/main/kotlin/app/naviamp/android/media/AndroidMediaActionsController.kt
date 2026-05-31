package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.Track
import app.naviamp.domain.media.favoriteTrackUpdate
import app.naviamp.domain.media.ratedTrackUpdate
import app.naviamp.domain.media.withUpdatedTrack
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.provider.addToPlaylistMutationUpdate
import app.naviamp.domain.provider.createPlaylistOrAddTracks
import app.naviamp.domain.provider.queueAppendPlan
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.ui.NaviampPlaylistChoiceUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun appendAndroidTracksToQueue(
    state: AndroidAppState,
    playbackQueueController: PlaybackQueueController,
    tracksToAdd: List<Track>,
    label: String = "tracks",
) {
    val plan = queueAppendPlan(tracks = tracksToAdd, label = label)
    state.status = plan.status
    if (plan.tracks.isEmpty()) {
        return
    }
    playbackQueueController.replaceQueue(state.playbackQueue)
    if (state.playbackQueue.currentIndex < 0 && state.playbackQueue.tracks.isEmpty()) {
        playbackQueueController.replaceQueue(PlaybackQueue(plan.tracks, currentIndex = 0))
        state.playbackQueue = playbackQueueController.queue
    } else {
        playbackQueueController.appendTracks(plan.tracks)?.let { queue ->
            state.playbackQueue = queue
        }
    }
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
    val updatedNowPlaying = state.nowPlaying.withUpdatedTrack(updatedTrack)
    val currentAlbumDetail = state.albumDetail
    state.nowPlaying = updatedNowPlaying
    state.tracks = state.tracks.withUpdatedTrack(updatedTrack)
    state.contentState = state.contentState.copy(
        searchResults = state.searchResults.withUpdatedTrack(updatedTrack),
        albumDetail = currentAlbumDetail?.withUpdatedTrack(updatedTrack),
    )
    if (updatedNowPlaying?.id == updatedTrack.id) {
        updateAndroidNotificationFavoriteState(state, updatedNowPlaying)
        playbackEngine.updateNotificationMetadata(
            title = updatedNowPlaying.title,
            subtitle = updatedNowPlaying.artistName,
            coverArtUrl = state.provider?.let { updatedNowPlaying.coverArtUrl(it) },
        )
    }
}

fun toggleAndroidCurrentFavorite(
    scope: CoroutineScope,
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
) {
    val activeProvider = state.provider ?: return
    val currentTrack = state.nowPlaying ?: return
    if (!activeProvider.capabilities.supportsTrackFavorites) return
    scope.launch {
        val favorite = currentTrack.favoritedAtIso8601 == null
        AndroidPlaybackNotificationControls.isFavorite = favorite
        runCatching {
            favoriteTrackUpdate(activeProvider, currentTrack, favoritedAtIso8601 = "android-local")
        }.onSuccess {
            it?.let { updatedTrack -> applyAndroidTrackMetadataUpdate(state, playbackEngine, updatedTrack) }
        }.onFailure { error ->
            AndroidPlaybackNotificationControls.isFavorite = !favorite
            state.status = error.message ?: "Could not update favorite."
        }
    }
}

fun setAndroidCurrentTrackRating(
    scope: CoroutineScope,
    state: AndroidAppState,
    playbackEngine: AndroidPlaybackEngine,
    rating: Int?,
) {
    val activeProvider = state.provider ?: return
    val currentTrack = state.nowPlaying ?: return
    if (!activeProvider.capabilities.supportsTrackRatings) return
    scope.launch {
        runCatching {
            ratedTrackUpdate(activeProvider, currentTrack, rating)
        }.onSuccess {
            it?.let { updatedTrack -> applyAndroidTrackMetadataUpdate(state, playbackEngine, updatedTrack) }
        }.onFailure { error ->
            state.status = error.message ?: "Could not update rating."
        }
    }
}

fun addAndroidTrackToPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    track: Track,
    playlist: NaviampPlaylistChoiceUi?,
    newPlaylistName: String? = null,
) {
    addAndroidTracksToPlaylist(
        scope = scope,
        state = state,
        tracksToAdd = listOf(track),
        playlist = playlist,
        newPlaylistName = newPlaylistName,
        label = "track",
    )
}

fun addAndroidTracksToPlaylist(
    scope: CoroutineScope,
    state: AndroidAppState,
    tracksToAdd: List<Track>,
    playlist: NaviampPlaylistChoiceUi?,
    newPlaylistName: String? = null,
    label: String = "tracks",
) {
    val activeProvider = state.provider ?: return
    val uniqueTracks = tracksToAdd.distinctBy { it.id }
    if (uniqueTracks.isEmpty()) {
        state.status = "No tracks found."
        return
    }
    state.playlistActionStatus = "Adding $label to playlist..."
    scope.launch {
        with(state) {
            runCatching {
                val result = activeProvider.createPlaylistOrAddTracks(
                    playlistId = playlist?.id,
                    newPlaylistName = newPlaylistName,
                    tracks = uniqueTracks,
                )
                val update = addToPlaylistMutationUpdate(result, playlist?.name)
                val playlists = if (update.refreshPlaylists) activeProvider.playlists(limit = 500) else null
                update to playlists
            }.onSuccess { (update, playlists) ->
                if (playlists != null) {
                    homeState = homeState.copy(playlists = playlists)
                }
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
