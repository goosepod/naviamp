package app.naviamp.app

import app.naviamp.domain.app.NaviampNavigationState

/**
 * Shared stateful controller graph used by every platform host.
 *
 * Constructing the queue coordinator here guarantees that navigation, live playback, and queue
 * mutations have one owner. Hosts may observe or adapt these controllers, but should not rebuild
 * their relationships independently.
 */
class NaviampApplicationControllers private constructor(
    val navigation: NaviampNavigationController,
    val playback: NaviampLivePlaybackController,
) {
    constructor(
        initialNavigationState: NaviampNavigationState = NaviampNavigationState(),
        initialPlaybackState: NaviampLivePlaybackState = NaviampLivePlaybackState(),
    ) : this(
        navigation = NaviampNavigationController(initialNavigationState),
        playback = NaviampLivePlaybackController(initialPlaybackState),
    )

    val queue = NaviampPlaybackQueueCoordinator(playback)

    companion object {
        fun from(
            navigation: NaviampNavigationController,
            playback: NaviampLivePlaybackController,
        ) = NaviampApplicationControllers(navigation, playback)
    }
}
