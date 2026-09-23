package app.naviamp.app

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
}

interface NaviampCastSessionListener {
    /** Called when a user selects a route in the native picker; returns Core's callback identity. */
    fun onTargetSelected(target: NaviampCastTarget): Long
    fun onConnecting(selectionId: Long)
    fun onConnected(selectionId: Long, displayName: String)
    fun onStopped(selectionId: Long)
    fun onDisconnected(selectionId: Long)
    fun onUnavailable(selectionId: Long)
}

/** Shared Cast selection and lifecycle policy; stale native callbacks cannot claim playback. */
class NaviampCastSessionController(
    private val effect: NaviampCastSessionEffect,
    private val outputs: NaviampPlaybackOutputSelectionController,
) : NaviampCastSessionListener {
    private var started = false
    private var selectionId: Long? = null

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
        return id
    }

    fun selectLocal() {
        if (currentSelectionId() != null) outputs.selectLocal()
        if (selectionId != null) effect.disconnect()
        selectionId = null
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

    override fun onStopped(selectionId: Long) {
        if (!isCurrent(selectionId)) return
        outputs.selectLocal()
        this.selectionId = null
    }

    override fun onUnavailable(selectionId: Long) = onDisconnected(selectionId)

    private fun isCurrent(id: Long): Boolean = currentSelectionId() == id

    private fun currentSelectionId(): Long? = selectionId?.takeIf { id ->
        (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.let { output ->
            output.selectionId == id && output.target.kind == NaviampRemoteOutputKind.Cast
        } == true
    }
}
