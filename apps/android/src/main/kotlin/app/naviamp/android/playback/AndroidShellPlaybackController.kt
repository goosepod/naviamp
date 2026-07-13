package app.naviamp.android

import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.resolveTrackOccurrenceIndex
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.resolveNowPlayingItemTrack
import kotlinx.coroutines.CoroutineScope

internal class AndroidShellPlaybackController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val playbackEngine: AndroidPlaybackEngine,
    private val playbackQueueController: PlaybackQueueController,
    private val activeQueue: () -> List<Track>,
    private val playTrack: (Track, List<Track>?, Boolean, Double?, Boolean) -> Unit,
    private val playInternetRadioStation: (app.naviamp.domain.InternetRadioStation) -> Unit,
    private val rememberRecentRadioStream: (RecentRadioStream) -> Unit,
) {
    fun resume() {
        when (state.playbackState) {
            PlaybackState.Idle,
            PlaybackState.Stopped,
            PlaybackState.Finished,
            is PlaybackState.Error,
            -> {
                state.nowPlaying?.let { track ->
                    playTrack(
                        track,
                        state.playbackQueue.tracks.ifEmpty { listOf(track) },
                        true,
                        state.restoredStartPositionSeconds,
                        false,
                    )
                    state.restoredStartPositionSeconds = null
                } ?: state.nowPlayingStation?.let(playInternetRadioStation)
            }
            else -> playbackEngine.resume()
        }
    }

    fun toggleShuffle() {
        val currentTrack = state.nowPlaying ?: return
        val queue = activeQueue()
        val currentIndex = resolveTrackOccurrenceIndex(
            tracks = queue,
            track = currentTrack,
            preferredIndex = state.playbackQueue.currentIndex,
        ) ?: return
        playbackQueueController.replaceQueue(
            PlaybackQueue(
                tracks = queue,
                currentIndex = currentIndex,
                playNextCount = state.playbackQueue.playNextCount.coerceIn(0, queue.size - currentIndex - 1),
            ),
        )
        val update = PlaybackQueueManager().toggleUpcomingShuffle(
            playbackQueueController.queue,
            state.shuffledUpNextSnapshot,
        )
        if (!update.changed) return
        playbackQueueController.replaceQueue(update.queue)
        state.playbackQueue = update.queue
        state.shuffledUpNextSnapshot = update.shuffledSnapshot
    }

    fun startTrackRadioQueue(track: Track, playSeed: Boolean) {
        startAndroidTrackRadioQueue(
            scope = scope,
            state = state,
            queueController = playbackQueueController,
            track = track,
            playSeed = playSeed,
            playTrack = { seedTrack, queue -> playTrack(seedTrack, queue, true, null, true) },
            rememberRecentRadioStream = rememberRecentRadioStream,
        )
    }

    fun startCurrentTrackRadio() {
        val currentTrack = state.nowPlaying ?: return
        startTrackRadioQueue(currentTrack, playSeed = false)
    }

    fun startQueueItemRadio(item: NaviampNowPlayingItemUi) {
        resolveNowPlayingItemTrack(
            item = item,
            queueTracks = state.playbackQueue.tracks,
            relatedTracks = state.relatedTracks,
            knownTracks = activeQueue(),
        )?.let { track -> startTrackRadioQueue(track, playSeed = true) }
    }
}
