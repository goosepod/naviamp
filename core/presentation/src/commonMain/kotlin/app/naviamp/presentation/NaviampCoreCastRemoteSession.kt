package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.flow.map

/** Cast translation and its existing MP3 compatibility policy; no queue or relay ownership. */
internal class NaviampCoreCastRemoteSession(private val sessions: NaviampCastSessionController) : NaviampCoreRemoteSession {
    override val kind = NaviampRemoteOutputKind.Cast
    override val supportsVolume = true
    override val returnLocalOnFailure = true
    override val idlePreservesPlaying = true
    override val selectionIdentity get() = sessions.receiverSelectionId
    private var observation = 0L
    override val status = sessions.mediaStatus.map { value ->
        value?.let {
            NaviampCoreRemoteStatus(sessions.currentConnectedSelectionId() ?: -1, ++observation, it.mediaUrl,
                when (it.playerState) {
                    NaviampCastReceiverPlayerState.Playing -> PlaybackState.Playing
                    NaviampCastReceiverPlayerState.Paused -> PlaybackState.Paused
                    NaviampCastReceiverPlayerState.Loading, NaviampCastReceiverPlayerState.Buffering -> PlaybackState.Loading
                    NaviampCastReceiverPlayerState.Idle -> if (it.finished) PlaybackState.Finished else PlaybackState.Stopped
                    NaviampCastReceiverPlayerState.Failed -> PlaybackState.Stopped
                }, it.positionMillis, it.durationMillis, it.finished, it.playerState == NaviampCastReceiverPlayerState.Failed)
        }
    }
    private data class Plan(override val quality: StreamQuality) : NaviampCoreRemoteMediaPlan { override val contentType = "audio/mpeg" }
    override fun plan(track: Track, provider: MediaProvider): NaviampCoreRemoteMediaPlan? = when {
        track.audioInfo?.contentType == "audio/mpeg" -> Plan(StreamQuality.Original)
        provider.capabilities.supportsStreamingTranscode -> Plan(StreamQuality.Transcoded(AudioCodec.Mp3, 320))
        else -> null
    }
    override fun start() = sessions.start()
    override fun connectedSelection() = sessions.currentConnectedSelectionId()
    override suspend fun load(selection: Long, media: NaviampCoreRemoteMedia, plan: NaviampCoreRemoteMediaPlan) = sessions.load(selection,
        NaviampCastReceiverMedia(media.url, plan.contentType, media.track.title, media.track.artistName, media.track.albumTitle,
            media.artworkUrl, media.track.durationSeconds?.toLong()?.times(1_000), media.positionMillis, false))
    override suspend fun command(selection: Long, command: NaviampCoreRemoteCommand) = sessions.command(selection, when (command) {
        NaviampCoreRemoteCommand.Play -> NaviampCastReceiverCommand.Play
        NaviampCoreRemoteCommand.Pause -> NaviampCastReceiverCommand.Pause
        NaviampCoreRemoteCommand.Stop -> NaviampCastReceiverCommand.Stop
        is NaviampCoreRemoteCommand.Seek -> NaviampCastReceiverCommand.Seek(command.millis)
        is NaviampCoreRemoteCommand.Volume -> NaviampCastReceiverCommand.Volume(command.percent)
    })
    override fun selectLocal() = sessions.selectLocal()
    override suspend fun disconnect(stopReceiver: Boolean) = sessions.selectLocal()
    override suspend fun shutdown() = sessions.shutdown()
    override fun stop() = sessions.stop()
}
