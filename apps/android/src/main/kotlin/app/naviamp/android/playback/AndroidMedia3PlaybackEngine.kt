package app.naviamp.android.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidMedia3PlaybackEngine(
    context: Context,
) : PlaybackEngine {
    private val appContext = context.applicationContext
    private val player = ExoPlayer.Builder(appContext).build()
    private val mediaSession = MediaSession.Builder(appContext, player).build()
    private var progressJob: Job? = null
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var onProgressChanged: ((PlaybackProgress) -> Unit)? = null
    private var onMetadataChanged: ((PlaybackStreamMetadata) -> Unit)? = null

    override val name: String = "Media3"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = true
    override val supportsCrossfade: Boolean = false
    override val supportsReplayGain: Boolean = false
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = false

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    publishPlayerState()
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publishPlayerState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    onStateChanged?.invoke(PlaybackState.Error(error.message ?: "Playback failed."))
                }

                override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                    onMetadataChanged?.invoke(
                        PlaybackStreamMetadata(
                            title = mediaMetadata.title?.toString(),
                        ),
                    )
                }
            },
        )
    }

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        this.onStateChanged = onStateChanged
        this.onProgressChanged = onProgressChanged
        this.onMetadataChanged = onMetadataChanged
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)

        player.setMediaItem(MediaItem.fromUri(request.url))
        player.prepare()
        request.startPositionSeconds?.let { player.seekTo((it * 1000).toLong().coerceAtLeast(0L)) }
        player.play()
        startProgressPolling(scope)
    }

    override fun pause() {
        player.pause()
    }

    override fun resume() {
        player.play()
    }

    override fun seek(positionSeconds: Double) {
        player.seekTo((positionSeconds * 1000).toLong().coerceAtLeast(0L))
    }

    override fun setVolume(percent: Int) {
        player.volume = percent.coerceIn(0, 100) / 100f
    }

    override fun stop() {
        progressJob?.cancel()
        progressJob = null
        player.stop()
        player.clearMediaItems()
        onProgressChanged?.invoke(PlaybackProgress.Unknown)
        onStateChanged?.invoke(PlaybackState.Stopped)
    }

    fun release() {
        progressJob?.cancel()
        progressJob = null
        mediaSession.release()
        player.release()
        onStateChanged = null
        onProgressChanged = null
        onMetadataChanged = null
    }

    private fun startProgressPolling(scope: CoroutineScope) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                onProgressChanged?.invoke(player.toPlaybackProgress())
                delay(500)
            }
        }
    }

    private fun publishPlayerState() {
        val state = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.Loading
            Player.STATE_READY -> if (player.isPlaying) PlaybackState.Playing else PlaybackState.Paused
            Player.STATE_ENDED -> PlaybackState.Finished
            Player.STATE_IDLE -> PlaybackState.Idle
            else -> PlaybackState.Idle
        }
        onStateChanged?.invoke(state)
    }

    private fun ExoPlayer.toPlaybackProgress(): PlaybackProgress {
        val knownDuration = duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET }
        return PlaybackProgress(
            positionSeconds = currentPosition.coerceAtLeast(0L) / 1000.0,
            durationSeconds = knownDuration?.let { it / 1000.0 },
        )
    }
}

