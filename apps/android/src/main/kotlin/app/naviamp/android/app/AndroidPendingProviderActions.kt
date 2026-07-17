package app.naviamp.android

import app.naviamp.app.NaviampProviderActionController
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MediaProvider.withAndroidPendingActions(
    sourceId: String?,
    controller: NaviampProviderActionController,
): MediaProvider = controller.offlineCapable(this, sourceId)

internal fun syncAndroidPendingProviderActions(
    scope: CoroutineScope,
    sourceId: String,
    provider: MediaProvider,
    controller: NaviampProviderActionController,
) {
    scope.launch {
        withContext(Dispatchers.IO) {
            controller.replay(sourceId, provider)
        }
    }
}
