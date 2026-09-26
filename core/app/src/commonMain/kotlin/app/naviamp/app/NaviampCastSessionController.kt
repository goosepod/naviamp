package app.naviamp.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NaviampCastTarget(val id: String, val displayName: String) {
    init {
        require(id.isNotBlank()) { "A Cast target ID is required." }
        require(displayName.isNotBlank()) { "A Cast target name is required." }
    }
}

/** Native Cast SDK operations. The host reports SDK events through the listener without owning policy. */
interface NaviampCastSessionEffect {
    fun start(listener: NaviampCastSessionListener)
    fun stop()
    fun disconnect()
    suspend fun load(selectionId: Long, media: NaviampCastReceiverMedia): Boolean = false
    suspend fun command(selectionId: Long, command: NaviampCastReceiverCommand): Boolean = false
}

data class NaviampCastReceiverMedia(
    val mediaUrl: String,
    val contentType: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMillis: Long?,
    val positionMillis: Long,
    val autoplay: Boolean,
)

sealed interface NaviampCastReceiverCommand {
    data object Play : NaviampCastReceiverCommand
    data object Pause : NaviampCastReceiverCommand
    data object Stop : NaviampCastReceiverCommand
    data class Seek(val positionMillis: Long) : NaviampCastReceiverCommand
    data class Volume(val percent: Int) : NaviampCastReceiverCommand
}

enum class NaviampCastReceiverPlayerState { Idle, Loading, Buffering, Playing, Paused, Failed }

data class NaviampCastReceiverStatus(
    val playerState: NaviampCastReceiverPlayerState,
    val positionMillis: Long,
    val durationMillis: Long?,
    val volumePercent: Int?,
    val finished: Boolean = false,
    val mediaUrl: String? = null,
)

interface NaviampCastSessionListener {
    /** Called when a user selects a route in the native picker; returns Core's callback identity. */
    fun onTargetSelected(target: NaviampCastTarget): Long
    fun onConnecting(selectionId: Long)
    fun onConnected(selectionId: Long, displayName: String)
    fun onStopped(selectionId: Long)
    fun onDisconnected(selectionId: Long)
    fun onUnavailable(selectionId: Long)
    fun onMediaStatus(selectionId: Long, status: NaviampCastReceiverStatus) {}
}

/** Shared Cast selection and lifecycle policy; stale native callbacks cannot claim playback. */
class NaviampCastSessionController(
    private val effect: NaviampCastSessionEffect,
    private val outputs: NaviampPlaybackOutputSelectionController,
) : NaviampCastSessionListener {
    private var started = false
    private var selectionId: Long? = null
    private val mutableMediaStatus = MutableStateFlow<NaviampCastReceiverStatus?>(null)

    val mediaStatus: StateFlow<NaviampCastReceiverStatus?> = mutableMediaStatus.asStateFlow()

    fun start() {
        if (started) return
        started = true
        effect.start(this)
    }

    fun stop() {
        if (!started) return
        started = false
        effect.stop()
    }

    override fun onTargetSelected(target: NaviampCastTarget): Long {
        val id = outputs.select(NaviampRemoteOutputTarget(
            kind = NaviampRemoteOutputKind.Cast,
            id = target.id,
            displayName = target.displayName,
        ))
        selectionId = id
        mutableMediaStatus.value = null
        return id
    }

    fun selectLocal() {
        if (currentSelectionId() != null) outputs.selectLocal()
        if (selectionId != null) effect.disconnect()
        selectionId = null
        mutableMediaStatus.value = null
    }

    override fun onConnecting(selectionId: Long) {
        if (isCurrent(selectionId)) outputs.connecting(selectionId)
    }

    override fun onConnected(selectionId: Long, displayName: String) {
        if (isCurrent(selectionId)) outputs.connected(selectionId, displayName)
    }

    override fun onDisconnected(selectionId: Long) {
        if (!isCurrent(selectionId)) return
        outputs.unavailable(selectionId)
        mutableMediaStatus.value = null
    }

    override fun onStopped(selectionId: Long) {
        if (!isCurrent(selectionId)) return
        outputs.selectLocal()
        this.selectionId = null
        mutableMediaStatus.value = null
    }

    override fun onUnavailable(selectionId: Long) = onDisconnected(selectionId)

    override fun onMediaStatus(selectionId: Long, status: NaviampCastReceiverStatus) {
        if (isCurrent(selectionId)) mutableMediaStatus.value = status
    }

    suspend fun load(selectionId: Long, media: NaviampCastReceiverMedia): Boolean {
        if (!isConnected(selectionId)) return false
        return effect.load(selectionId, media) && isConnected(selectionId)
    }

    fun activatePlaybackAuthority(selectionId: Long): Boolean =
        isConnected(selectionId) && outputs.activatePlaybackAuthority(selectionId)

    suspend fun command(selectionId: Long, command: NaviampCastReceiverCommand): Boolean =
        isCurrent(selectionId) && outputs.hasRemotePlaybackAuthority() && effect.command(selectionId, command)

    fun currentConnectedSelectionId(): Long? = currentSelectionId()?.takeIf(::isConnected)

    private fun isConnected(id: Long): Boolean =
        (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.let {
            it.selectionId == id && it.phase == NaviampRemoteOutputPhase.Connected
        } == true

    private fun isCurrent(id: Long): Boolean = currentSelectionId() == id

    private fun currentSelectionId(): Long? = selectionId?.takeIf { id ->
        (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.let { output ->
            output.selectionId == id && output.target.kind == NaviampRemoteOutputKind.Cast
        } == true
    }
}
