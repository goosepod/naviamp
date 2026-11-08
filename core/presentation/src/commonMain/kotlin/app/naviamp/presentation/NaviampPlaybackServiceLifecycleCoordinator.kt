package app.naviamp.presentation

/** Shared retention execution and event-driven retry when a host temporarily rejects a start. */
class NaviampPlaybackServiceLifecycleCoordinator(
    private val start: () -> Boolean,
    private val stop: () -> Unit,
) {
    private var retained = false
    private var started = false
    private var visible = false

    fun retentionChanged(shouldRetain: Boolean) {
        if (retained == shouldRetain) return
        retained = shouldRetain
        if (retained) {
            started = start()
        } else if (started) {
            stop()
            started = false
        }
    }

    /** A visibility transition provides a new opportunity for a previously rejected native start. */
    fun visibilityChanged(isVisible: Boolean) {
        if (visible == isVisible) return
        visible = isVisible
        if (visible && retained && !started) started = start()
    }
}
