package app.naviamp.android

import app.naviamp.app.NaviampProviderActionController
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PendingProviderActionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MediaProvider.withAndroidPendingActions(
    sourceId: String?,
    repository: PendingProviderActionRepository,
): MediaProvider {
    return NaviampProviderActionController(repository).offlineCapable(this, sourceId)
}

internal fun syncAndroidPendingProviderActions(
    scope: CoroutineScope,
    sourceId: String,
    provider: MediaProvider,
    repository: PendingProviderActionRepository,
    setStatus: (String) -> Unit = {},
) {
    scope.launch {
        val result = withContext(Dispatchers.IO) {
            NaviampProviderActionController(repository).replay(sourceId, provider)
        }
        when {
            result.completed > 0 && result.failed == 0 -> setStatus("Synced ${result.completed} offline actions.")
            result.completed > 0 -> setStatus("Synced ${result.completed} offline actions; ${result.failed} still pending.")
        }
    }
}
