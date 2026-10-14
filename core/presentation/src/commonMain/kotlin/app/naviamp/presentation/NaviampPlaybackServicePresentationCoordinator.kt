package app.naviamp.presentation

/**
 * Coordinates the acknowledgment of a requested native playback surface separately from retention.
 *
 * A start request was accepted using an earlier playback snapshot. It must be acknowledged even if
 * playback has failed, stopped, or lost its current item before the native surface receives it.
 * Only the shared grace-period retention decision releases the surface; a transient idle
 * publication must neither cancel the acknowledgment nor release it between tracks.
 * Callbacks execute synchronously and must complete the corresponding native operation.
 */
class NaviampPlaybackServicePresentationCoordinator(
    private val establish: (NaviampExternalPlaybackSnapshot) -> Unit,
    private val update: (NaviampExternalPlaybackSnapshot) -> Unit,
    private val remove: () -> Unit,
) {
    private var established = false

    fun startRequested(snapshot: NaviampExternalPlaybackSnapshot) {
        establish(snapshot)
        established = true
    }

    fun publish(snapshot: NaviampExternalPlaybackSnapshot, notificationChanged: Boolean) {
        if (!snapshot.shouldRetainPlaybackService) return
        if (!established) {
            startRequested(snapshot)
        } else if (notificationChanged) {
            update(snapshot)
        }
    }

    fun release() {
        if (!established) return
        remove()
        established = false
    }
}
