package app.naviamp.app

import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.provider.PendingProviderActionRepository

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
    val connection: NaviampConnectionController,
    val providerActions: NaviampProviderActionController,
) {
    constructor(
        initialNavigationState: NaviampNavigationState = NaviampNavigationState(),
        initialPlaybackState: NaviampLivePlaybackState = NaviampLivePlaybackState(),
        initialConnectionState: NaviampConnectionRuntimeState = NaviampConnectionRuntimeState(),
        pendingProviderActions: PendingProviderActionRepository,
    ) : this(
        navigation = NaviampNavigationController(initialNavigationState),
        playback = NaviampLivePlaybackController(initialPlaybackState),
        connection = NaviampConnectionController(initialConnectionState),
        providerActions = NaviampProviderActionController(pendingProviderActions),
    )

    val queue = NaviampPlaybackQueueCoordinator(playback)

    companion object {
        fun from(
            navigation: NaviampNavigationController,
            playback: NaviampLivePlaybackController,
            connection: NaviampConnectionController = NaviampConnectionController(),
            providerActions: NaviampProviderActionController,
        ) = NaviampApplicationControllers(navigation, playback, connection, providerActions)
    }
}
