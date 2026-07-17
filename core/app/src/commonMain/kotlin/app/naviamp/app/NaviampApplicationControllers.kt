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
private data class NaviampControllerGraph(
    val navigation: NaviampNavigationController,
    val playback: NaviampLivePlaybackController,
    val connection: NaviampConnectionController,
    val providerActions: NaviampProviderActionController,
    val status: NaviampApplicationStatusController,
)

private fun createNaviampControllerGraph(
    initialNavigationState: NaviampNavigationState,
    initialPlaybackState: NaviampLivePlaybackState,
    initialConnectionState: NaviampConnectionRuntimeState,
    pendingProviderActions: PendingProviderActionRepository,
): NaviampControllerGraph {
    val status = NaviampApplicationStatusController()
    return NaviampControllerGraph(
        navigation = NaviampNavigationController(initialNavigationState),
        playback = NaviampLivePlaybackController(initialPlaybackState),
        connection = NaviampConnectionController(initialConnectionState, status),
        providerActions = NaviampProviderActionController(pendingProviderActions, status),
        status = status,
    )
}

class NaviampApplicationControllers private constructor(graph: NaviampControllerGraph) {
    val navigation = graph.navigation
    val playback = graph.playback
    val connection = graph.connection
    val providerActions = graph.providerActions
    val status = graph.status

    constructor(
        initialNavigationState: NaviampNavigationState = NaviampNavigationState(),
        initialPlaybackState: NaviampLivePlaybackState = NaviampLivePlaybackState(),
        initialConnectionState: NaviampConnectionRuntimeState = NaviampConnectionRuntimeState(),
        pendingProviderActions: PendingProviderActionRepository,
    ) : this(
        createNaviampControllerGraph(
            initialNavigationState = initialNavigationState,
            initialPlaybackState = initialPlaybackState,
            initialConnectionState = initialConnectionState,
            pendingProviderActions = pendingProviderActions,
        ),
    )

    val queue = NaviampPlaybackQueueCoordinator(playback)

    companion object {
        fun from(
            navigation: NaviampNavigationController,
            playback: NaviampLivePlaybackController,
            connection: NaviampConnectionController = NaviampConnectionController(),
            providerActions: NaviampProviderActionController,
            status: NaviampApplicationStatusController = NaviampApplicationStatusController(),
        ) = NaviampApplicationControllers(
            NaviampControllerGraph(navigation, playback, connection, providerActions, status),
        )
    }
}
