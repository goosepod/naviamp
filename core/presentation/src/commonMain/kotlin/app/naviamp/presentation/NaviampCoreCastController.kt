package app.naviamp.presentation

import app.naviamp.app.NaviampCastMediaEndpointController
import app.naviamp.app.NaviampCastSessionController
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampPlaybackOutputSelectionController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import kotlinx.coroutines.CoroutineScope

/** Compatibility facade; all queue, media relay and handoff behavior is protocol-neutral. */
internal class NaviampCoreCastController private constructor(
    private val shared: NaviampCoreRemotePlaybackController,
) : NaviampCoreRemotePlaybackRoute by shared {
    constructor(scope: CoroutineScope, sessions: NaviampCastSessionController,
        outputs: NaviampPlaybackOutputSelectionController, endpoint: NaviampCastMediaEndpointController,
        providers: NaviampCoreMediaProviderSource, playback: NaviampLivePlaybackController,
        queue: NaviampPlaybackQueueCoordinator, local: NaviampCorePlaybackEffectPort, publishNowPlaying: () -> Unit,
    ) : this(NaviampCoreRemotePlaybackController(scope, NaviampCoreCastRemoteSession(sessions), outputs,
        endpoint, providers, playback, queue, local, publishNowPlaying))
    fun start() = shared.start()
    fun selectLocal() = shared.selectLocal()
    suspend fun shutdown() = shared.shutdown()
    fun close() = shared.close()
}