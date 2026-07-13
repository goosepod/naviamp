package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.provider.allKnownTracks
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.applyPlaybackQueueUpdate
import app.naviamp.domain.popular.ArtistPopularTracksService
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.resolveNowPlayingItemTrack as resolveNowPlayingItemTrackUi
import kotlinx.coroutines.CoroutineScope

internal class AndroidMediaAppController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
    private val playbackEngine: AndroidPlaybackEngine,
    private val queueController: PlaybackQueueController,
    private val popularTracksService: ArtistPopularTracksService,
) {
    fun activeQueue(): List<Track> =
        state.playbackQueue.tracks.ifEmpty { allKnownTracks(state.searchResults, state.albumDetail) }

    fun findKnownTrack(trackId: String): Track? =
        findAndroidKnownTrack(state, trackId, activeQueue())

    fun resolveNowPlayingItemTrack(item: NaviampNowPlayingItemUi): Track? =
        resolveNowPlayingItemTrackUi(
            item = item,
            queueTracks = state.playbackQueue.tracks,
            relatedTracks = state.relatedTracks,
            knownTracks = activeQueue(),
        ) ?: findKnownTrack(item.id)

    fun appendTracksToQueue(tracksToAdd: List<Track>, label: String = "tracks") {
        appendAndroidTracksToQueue(state, queueController, tracksToAdd, label)
    }

    fun playNext(track: Track) {
        playNextTracks(listOf(track))
    }

    fun playNextTracks(tracksToAdd: List<Track>, label: String = "tracks") {
        val update = PlaybackQueueManager().playNextTracks(
            currentQueue = state.playbackQueue,
            tracksToAdd = tracksToAdd,
            label = label,
        )
        applyPlaybackQueueUpdate(
            update = update,
            setStatus = { status -> state.status = status },
            replaceQueue = { queue ->
                state.playbackQueue = queue
                queueController.replaceQueue(queue)
            },
        )
    }

    fun addToQueue(track: Track) {
        appendTracksToQueue(listOf(track), "track")
    }

    fun removeFromQueue(index: Int) {
        val queue = state.playbackQueue.removeAt(index)
        state.playbackQueue = queue
        queueController.replaceQueue(queue)
    }

    fun moveQueueTrackNext(index: Int) {
        val queue = state.playbackQueue.moveToNext(index)
        state.playbackQueue = queue
        queueController.replaceQueue(queue)
    }

    fun emptyQueue() {
        val queue = state.playbackQueue.clearUpcoming()
        state.playbackQueue = queue
        queueController.replaceQueue(queue)
    }

    fun loadRelatedTracks(track: Track) {
        loadAndroidRelatedTracks(scope, state, track)
    }

    fun updateNotificationFavoriteState(track: Track? = state.nowPlaying) {
        updateAndroidNotificationFavoriteState(state, track)
    }

    fun applyTrackMetadataUpdate(updatedTrack: Track) {
        applyAndroidTrackMetadataUpdate(state, playbackEngine, updatedTrack)
    }

    fun toggleCurrentFavorite() {
        toggleAndroidCurrentFavorite(scope, state, playbackEngine, storage)
    }

    fun openArtistDetails(
        artistId: ArtistId,
        fallbackName: String? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        openAndroidArtistDetails(
            scope = scope,
            state = state,
            libraryIndexRepository = storage,
            providerResponseCacheRepository = storage,
            popularTracksService = popularTracksService,
            artistId = artistId,
            fallbackName = fallbackName,
            pushCurrentArtist = pushCurrentArtist,
        )
    }
}
