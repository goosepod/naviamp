package app.naviamp.presentation

import app.naviamp.app.NaviampCastMediaEndpointController
import app.naviamp.app.NaviampCastMediaKind
import app.naviamp.app.NaviampCastMediaResource
import app.naviamp.app.NaviampCastReceiverCommand
import app.naviamp.app.NaviampCastReceiverMedia
import app.naviamp.app.NaviampCastReceiverPlayerState
import app.naviamp.app.NaviampCastReceiverStatus
import app.naviamp.app.NaviampCastSessionController
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackOutputSelection
import app.naviamp.app.NaviampPlaybackOutputSelectionController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampRemoteOutputKind
import app.naviamp.app.NaviampRemoteOutputPhase
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueFinishedCommand
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Shared queue, handoff, receiver status, and return-to-local policy for Cast playback. */
internal class NaviampCoreCastController(
    private val scope: CoroutineScope,
    private val sessions: NaviampCastSessionController,
    private val outputs: NaviampPlaybackOutputSelectionController,
    private val endpoint: NaviampCastMediaEndpointController,
    private val providers: NaviampCoreMediaProviderSource,
    private val playback: NaviampLivePlaybackController,
    private val queue: NaviampPlaybackQueueCoordinator,
    private val local: NaviampCorePlaybackEffectPort,
    private val publishNowPlaying: () -> Unit,
) : NaviampCoreCastPlaybackRoute {
    private var loadJob: Job? = null
    private var outputJob: Job? = null
    private var statusJob: Job? = null
    private var requestRevision = 0L
    private var activeMediaUrl: String? = null
    private var remoteWasPlaying = false
    private var finishedMediaUrl: String? = null
    private val commandMutex = Mutex()
    override val suppressLocalObservations: Boolean get() = activeMediaUrl != null

    fun start() {
        if (outputJob != null) return
        sessions.start()
        outputJob = scope.launch {
            outputs.state.collect { output ->
                when (output) {
                    is NaviampPlaybackOutputSelection.Remote -> {
                        if (output.target.kind == NaviampRemoteOutputKind.Cast &&
                            output.phase == NaviampRemoteOutputPhase.Connected &&
                            !output.playbackAuthorityActive && playback.state.value.queue.current != null
                        ) loadCurrent()
                        if (output.target.kind == NaviampRemoteOutputKind.Cast &&
                            output.phase == NaviampRemoteOutputPhase.Unavailable && activeMediaUrl != null
                        ) {
                            playback.updatePlaybackState(PlaybackState.Paused)
                            publishNowPlaying()
                        }
                        if (output.target.kind != NaviampRemoteOutputKind.Cast && activeMediaUrl != null) {
                            sessions.selectLocal()
                            release(resumeLocal = false)
                        }
                    }
                    NaviampPlaybackOutputSelection.Local -> if (activeMediaUrl != null) {
                        release(resumeLocal = true)
                    }
                }
            }
        }
        statusJob = scope.launch {
            sessions.mediaStatus.collect { status ->
                if (status != null) receiverStatus(status)
            }
        }
    }

    fun selectLocal() = sessions.selectLocal()

    fun close() {
        loadJob?.cancel()
        outputJob?.cancel()
        statusJob?.cancel()
        outputJob = null
        statusJob = null
        if (activeMediaUrl != null) sessions.selectLocal()
        sessions.stop()
        scope.launch { endpoint.stop() }
    }

    override fun loadCurrent(positionSeconds: Double?): Boolean {
        val selected = sessions.currentConnectedSelectionId() ?: return false
        val track = playback.state.value.queue.current ?: return false
        val provider = providers.current() ?: return false
        val quality = when {
            provider.capabilities.supportsStreamingTranscode -> StreamQuality.Transcoded(AudioCodec.Mp3, 320)
            track.audioInfo?.contentType == "audio/mpeg" -> StreamQuality.Original
            else -> return false
        }
        val revision = ++requestRevision
        loadJob?.cancel()
        loadJob = scope.launch {
            val wasPlaying = playback.state.value.playbackState in setOf(PlaybackState.Playing, PlaybackState.Loading) ||
                (activeMediaUrl != null && remoteWasPlaying)
            val startPosition = positionSeconds ?: playback.state.value.progress.positionSeconds ?: 0.0
            val media = try {
                endpoint.start()
                val url = endpoint.issue(NaviampCastMediaResource(
                    kind = NaviampCastMediaKind.Track,
                    sourceId = provider.cacheNamespace,
                    id = track.id.value,
                    quality = quality,
                ))
                val artwork = track.coverArtId?.let { artworkId ->
                    endpoint.issue(NaviampCastMediaResource(
                        kind = NaviampCastMediaKind.Artwork,
                        sourceId = provider.cacheNamespace,
                        id = artworkId,
                    ))
                }
                NaviampCastReceiverMedia(
                    mediaUrl = url,
                    contentType = "audio/mpeg",
                    title = track.title,
                    artist = track.artistName,
                    album = track.albumTitle,
                    artworkUrl = artwork,
                    durationMillis = track.durationSeconds?.toLong()?.times(1_000),
                    positionMillis = (startPosition.coerceAtLeast(0.0) * 1_000).toLong(),
                    autoplay = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return@launch
            }
            val loaded = try {
                withTimeoutOrNull(15_000) { sessions.load(selected, media) } == true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (!loaded || revision != requestRevision ||
                providers.current()?.cacheNamespace != provider.cacheNamespace ||
                playback.state.value.queue.current?.id != track.id
            ) return@launch
            if (!sessions.activatePlaybackAuthority(selected)) return@launch
            activeMediaUrl = media.mediaUrl
            finishedMediaUrl = null
            remoteWasPlaying = wasPlaying
            local.stop()
            local.setVisualizerFramesEnabled(false)
            playback.replace(playback.state.value.copy(
                playbackState = if (wasPlaying) PlaybackState.Loading else PlaybackState.Paused,
                progress = PlaybackProgress(startPosition, track.durationSeconds?.toDouble()),
            ))
            publishNowPlaying()
            if (wasPlaying && !commandMutex.withLock {
                    withTimeoutOrNull(8_000) {
                        sessions.command(selected, NaviampCastReceiverCommand.Play)
                    } == true
                }) {
                playback.updatePlaybackState(PlaybackState.Paused)
                publishNowPlaying()
            }
        }
        return true
    }

    override fun navigate(command: PlaybackQueueNavigationCommand) {
        if (command != PlaybackQueueNavigationCommand.None) loadCurrent(0.0)
    }

    override fun pause() = send(NaviampCastReceiverCommand.Pause)
    override fun resume() = send(NaviampCastReceiverCommand.Play)
    override fun seek(positionSeconds: Double) = send(
        NaviampCastReceiverCommand.Seek((positionSeconds.coerceAtLeast(0.0) * 1_000).toLong()),
    )
    override fun stop() = send(NaviampCastReceiverCommand.Stop)
    override fun setVolume(percent: Int) = send(NaviampCastReceiverCommand.Volume(percent))

    private fun send(command: NaviampCastReceiverCommand) {
        scope.launch {
            commandMutex.withLock {
                sessions.currentConnectedSelectionId()?.let { selected ->
                    withTimeoutOrNull(8_000) { sessions.command(selected, command) }
                }
            }
        }
    }

    private fun receiverStatus(status: NaviampCastReceiverStatus) {
        if (!outputs.hasRemotePlaybackAuthority() || status.mediaUrl != activeMediaUrl) return
        remoteWasPlaying = status.finished || status.playerState in setOf(
            NaviampCastReceiverPlayerState.Playing,
            NaviampCastReceiverPlayerState.Buffering,
            NaviampCastReceiverPlayerState.Loading,
        )
        playback.replace(playback.state.value.copy(
            playbackState = when (status.playerState) {
                NaviampCastReceiverPlayerState.Playing -> PlaybackState.Playing
                NaviampCastReceiverPlayerState.Paused -> PlaybackState.Paused
                NaviampCastReceiverPlayerState.Loading,
                NaviampCastReceiverPlayerState.Buffering -> PlaybackState.Loading
                NaviampCastReceiverPlayerState.Idle -> if (status.finished) PlaybackState.Finished else PlaybackState.Stopped
                NaviampCastReceiverPlayerState.Failed -> PlaybackState.Stopped
            },
            progress = PlaybackProgress(
                status.positionMillis.coerceAtLeast(0) / 1_000.0,
                status.durationMillis?.div(1_000.0)
                    ?: playback.state.value.queue.current?.durationSeconds?.toDouble(),
            ),
        ))
        publishNowPlaying()
        if (status.finished && finishedMediaUrl != status.mediaUrl) {
            finishedMediaUrl = status.mediaUrl
            val next = queue.finishCurrentTrack()
            when (next.command) {
                PlaybackQueueFinishedCommand.ReplayCurrent -> loadCurrent(0.0)
                PlaybackQueueFinishedCommand.PlayNext -> {
                    playback.updateCurrentTrack(next.queue.current)
                    loadCurrent(0.0)
                }
                PlaybackQueueFinishedCommand.None -> Unit
            }
        }
    }

    private fun release(resumeLocal: Boolean) {
        ++requestRevision
        loadJob?.cancel()
        activeMediaUrl = null
        finishedMediaUrl = null
        scope.launch { endpoint.stop() }
        if (resumeLocal && playback.state.value.queue.current != null) {
            val position = playback.state.value.progress.positionSeconds
            local.restoreQueue(playback.state.value.queue, position)
            local.applyRepeatMode(playback.state.value.repeatMode)
            if (remoteWasPlaying) local.startOrRestore()
            else playback.updatePlaybackState(PlaybackState.Paused)
        }
        remoteWasPlaying = false
        publishNowPlaying()
    }
}
