package app.naviamp.android

import android.content.Context
import app.naviamp.app.NaviampCastSessionEffect
import app.naviamp.app.NaviampCastSessionListener
import app.naviamp.app.NaviampCastTarget
import app.naviamp.app.NaviampCastReceiverCommand
import app.naviamp.app.NaviampCastReceiverMedia
import app.naviamp.app.NaviampCastReceiverPlayerState
import app.naviamp.app.NaviampCastReceiverStatus
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Translates main-thread Cast SDK session callbacks into the shared session contract. */
class AndroidNaviampCastSessionEffect(context: Context) : NaviampCastSessionEffect {
    private val castContext = CastContext.getSharedInstance(context.applicationContext)
    private val sessionManager = castContext.sessionManager
    private var listener: NaviampCastSessionListener? = null
    private var activeSession: CastSession? = null
    private var activeSelectionId: Long? = null
    private var observedMediaClient: RemoteMediaClient? = null
    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = publishMediaStatus()
        override fun onMetadataUpdated() = publishMediaStatus()
        override fun onMediaError(error: com.google.android.gms.cast.MediaError) {
            activeSelectionId?.let { id ->
                listener?.onMediaStatus(id, NaviampCastReceiverStatus(
                    NaviampCastReceiverPlayerState.Failed, 0, null, null,
                    mediaUrl = observedMediaClient?.mediaInfo?.contentId,
                ))
            }
        }
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            begin(session)?.let { listener?.onConnecting(it) }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            val id = selectionFor(session) ?: begin(session) ?: return
            observeMedia(session)
            listener?.onConnected(id, session.castDevice?.friendlyName.orEmpty())
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            finish(session)
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            unobserveMedia()
            selectionFor(session)?.let { listener?.onUnavailable(it) }
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            val id = selectionFor(session) ?: begin(session) ?: return
            listener?.onConnecting(id)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            val id = selectionFor(session) ?: begin(session) ?: return
            observeMedia(session)
            listener?.onConnected(id, session.castDevice?.friendlyName.orEmpty())
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            finish(session)
        }

        override fun onSessionEnding(session: CastSession) = Unit

        override fun onSessionEnded(session: CastSession, error: Int) {
            val id = selectionFor(session) ?: return
            unobserveMedia()
            if (error == CastStatusCodes.SUCCESS) listener?.onStopped(id)
            else listener?.onDisconnected(id)
            activeSession = null
            activeSelectionId = null
        }
    }

    override fun start(listener: NaviampCastSessionListener) {
        check(this.listener == null) { "Cast session listener is already attached." }
        this.listener = listener
        sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        sessionManager.currentCastSession?.let { session ->
            val id = begin(session) ?: return@let
            if (session.isConnected) {
                observeMedia(session)
                listener.onConnected(id, session.castDevice?.friendlyName.orEmpty())
            }
            else listener.onConnecting(id)
        }
    }

    override fun stop() {
        if (listener == null) return
        sessionManager.removeSessionManagerListener(sessionListener, CastSession::class.java)
        unobserveMedia()
        listener = null
        activeSession = null
        activeSelectionId = null
    }

    override fun disconnect() {
        sessionManager.endCurrentSession(true)
    }

    override suspend fun load(selectionId: Long, media: NaviampCastReceiverMedia): Boolean {
        val client = currentMediaClient(selectionId) ?: return false
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, media.title)
            putString(MediaMetadata.KEY_ARTIST, media.artist)
            media.album?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it) }
            media.artworkUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(media.mediaUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(media.contentType)
            .setMetadata(metadata)
            .apply { media.durationMillis?.let(::setStreamDuration) }
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setCurrentTime(media.positionMillis.coerceAtLeast(0))
            .setAutoplay(media.autoplay)
            .build()
        return await(client.load(request)) && currentMediaClient(selectionId) === client
    }

    override suspend fun command(selectionId: Long, command: NaviampCastReceiverCommand): Boolean {
        val client = currentMediaClient(selectionId) ?: return false
        val result = when (command) {
            NaviampCastReceiverCommand.Play -> client.play()
            NaviampCastReceiverCommand.Pause -> client.pause()
            NaviampCastReceiverCommand.Stop -> client.stop()
            is NaviampCastReceiverCommand.Seek -> client.seek(command.positionMillis.coerceAtLeast(0))
            is NaviampCastReceiverCommand.Volume -> client.setStreamVolume(command.percent.coerceIn(0, 100) / 100.0)
        }
        return await(result) && currentMediaClient(selectionId) === client
    }

    private suspend fun await(result: PendingResult<RemoteMediaClient.MediaChannelResult>): Boolean =
        suspendCancellableCoroutine { continuation ->
            result.setResultCallback { response ->
                if (continuation.isActive) continuation.resume(response.status.isSuccess)
            }
        }

    private fun currentMediaClient(selectionId: Long): RemoteMediaClient? =
        activeSession?.takeIf { it.isConnected && activeSelectionId == selectionId }?.remoteMediaClient

    private fun observeMedia(session: CastSession) {
        val client = session.remoteMediaClient ?: return
        if (observedMediaClient === client) return
        unobserveMedia()
        observedMediaClient = client
        client.registerCallback(mediaCallback)
        publishMediaStatus()
    }

    private fun unobserveMedia() {
        observedMediaClient?.unregisterCallback(mediaCallback)
        observedMediaClient = null
    }

    private fun publishMediaStatus() {
        val id = activeSelectionId ?: return
        val client = observedMediaClient ?: return
        val status = client.mediaStatus ?: return
        val state = when (status.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> NaviampCastReceiverPlayerState.Playing
            MediaStatus.PLAYER_STATE_PAUSED -> NaviampCastReceiverPlayerState.Paused
            MediaStatus.PLAYER_STATE_BUFFERING -> NaviampCastReceiverPlayerState.Buffering
            MediaStatus.PLAYER_STATE_LOADING -> NaviampCastReceiverPlayerState.Loading
            MediaStatus.PLAYER_STATE_IDLE -> NaviampCastReceiverPlayerState.Idle
            else -> NaviampCastReceiverPlayerState.Failed
        }
        listener?.onMediaStatus(id, NaviampCastReceiverStatus(
            playerState = state,
            positionMillis = client.approximateStreamPosition.coerceAtLeast(0),
            durationMillis = client.streamDuration.takeIf { it > 0 },
            volumePercent = (status.streamVolume * 100).toInt().coerceIn(0, 100),
            finished = state == NaviampCastReceiverPlayerState.Idle &&
                status.idleReason == MediaStatus.IDLE_REASON_FINISHED,
            mediaUrl = client.mediaInfo?.contentId,
        ))
    }

    private fun begin(session: CastSession): Long? {
        val device = session.castDevice ?: return null
        unobserveMedia()
        val id = listener?.onTargetSelected(NaviampCastTarget(
            id = device.deviceId,
            displayName = device.friendlyName,
        )) ?: return null
        activeSession = session
        activeSelectionId = id
        return id
    }

    private fun selectionFor(session: CastSession): Long? =
        activeSelectionId?.takeIf { activeSession === session }

    private fun finish(session: CastSession) {
        val id = selectionFor(session) ?: return
        listener?.onDisconnected(id)
        activeSession = null
        activeSelectionId = null
    }
}
