package app.naviamp.presentation

import app.naviamp.app.NaviampRemoteOutputKind
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.flow.Flow

internal interface NaviampCoreRemoteMediaPlan {
    val quality: StreamQuality
    val contentType: String
}

internal data class NaviampCoreRemoteMedia(
    val url: String, val track: Track, val artworkUrl: String?, val positionMillis: Long,
)
internal data class NaviampCoreRemoteStatus(
    val selectionId: Long, val revision: Long, val mediaUrl: String?, val state: PlaybackState,
    val positionMillis: Long, val durationMillis: Long?, val finished: Boolean = false, val failed: Boolean = false,
)
internal sealed interface NaviampCoreRemoteCommand {
    data object Play : NaviampCoreRemoteCommand
    data object Pause : NaviampCoreRemoteCommand
    data object Stop : NaviampCoreRemoteCommand
    data class Seek(val millis: Long) : NaviampCoreRemoteCommand
    data class Volume(val percent: Int) : NaviampCoreRemoteCommand
}

/** Protocol operations only; the shared controller owns queue, leases and local handoff. */
internal interface NaviampCoreRemoteSession {
    val kind: NaviampRemoteOutputKind
    val status: Flow<NaviampCoreRemoteStatus?>
    val supportsNext: Boolean get() = false
    val supportsVolume: Boolean
    val observedAuthority: Boolean get() = false
    val returnLocalOnFailure: Boolean get() = false
    val retainUntilRevoked: Boolean get() = false
    val idlePreservesPlaying: Boolean get() = false
    val observationRevision: Long get() = 0
    val selectionIdentity: Long?
    fun start(): Boolean
    fun connectedSelection(): Long?
    fun plan(track: Track, provider: MediaProvider): NaviampCoreRemoteMediaPlan?
    suspend fun load(selection: Long, media: NaviampCoreRemoteMedia, plan: NaviampCoreRemoteMediaPlan): Boolean
    suspend fun prepareNext(selection: Long, media: NaviampCoreRemoteMedia?, plan: NaviampCoreRemoteMediaPlan?): Boolean = false
    suspend fun command(selection: Long, command: NaviampCoreRemoteCommand): Boolean
    fun selectLocal()
    suspend fun disconnect(stopReceiver: Boolean = true)
    suspend fun shutdown()
    fun stop()
}
