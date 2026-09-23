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

data class NaviampCastSessionState(
    val targets: List<NaviampCastTarget> = emptyList(),
)

/** Native Cast SDK operations. The host reports SDK events through the listener without owning policy. */
interface NaviampCastSessionEffect {
    fun start(listener: NaviampCastSessionListener)
    fun stop()
    fun connect(targetId: String, selectionId: Long)
    fun disconnect()
}

interface NaviampCastSessionListener {
    fun onTargetsChanged(targets: List<NaviampCastTarget>)
    fun onConnecting(selectionId: Long)
    fun onConnected(selectionId: Long, displayName: String)
    fun onDisconnected(selectionId: Long)
    fun onUnavailable(selectionId: Long)
}

/** Shared Cast selection and lifecycle policy; stale native callbacks cannot claim playback. */
class NaviampCastSessionController(
    private val effect: NaviampCastSessionEffect,
    private val outputs: NaviampPlaybackOutputSelectionController,
) : NaviampCastSessionListener {
    private val mutableState = MutableStateFlow(NaviampCastSessionState())
    private var started = false
    private var selectionId: Long? = null

    val state: StateFlow<NaviampCastSessionState> = mutableState.asStateFlow()

    fun start() {
        if (started) return
        started = true
        effect.start(this)
    }

    fun stop() {
        if (!started) return
        started = false
        effect.stop()
        mutableState.value = mutableState.value.copy(targets = emptyList())
    }

    fun select(target: NaviampCastTarget) {
        if (selectionId != null) effect.disconnect()
        val id = outputs.select(NaviampRemoteOutputTarget(
            kind = NaviampRemoteOutputKind.Cast,
            id = target.id,
            displayName = target.displayName,
        ))
        selectionId = id
        effect.connect(target.id, id)
    }

    fun selectLocal() {
        if (currentSelectionId() != null) outputs.selectLocal()
        if (selectionId != null) effect.disconnect()
        selectionId = null
    }

    override fun onTargetsChanged(targets: List<NaviampCastTarget>) {
        if (!started) return
        mutableState.value = mutableState.value.copy(
            targets = targets.distinctBy(NaviampCastTarget::id)
                .sortedWith(compareBy<NaviampCastTarget> { it.displayName.lowercase() }.thenBy { it.id }),
        )
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
    }

    override fun onUnavailable(selectionId: Long) = onDisconnected(selectionId)

    private fun isCurrent(id: Long): Boolean = currentSelectionId() == id

    private fun currentSelectionId(): Long? = selectionId?.takeIf { id ->
        (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.let { output ->
            output.selectionId == id && output.target.kind == NaviampRemoteOutputKind.Cast
        } == true
    }
}
