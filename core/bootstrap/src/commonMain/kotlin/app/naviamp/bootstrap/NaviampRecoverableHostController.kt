package app.naviamp.bootstrap

import app.naviamp.storage.StorageRecoveryException
import app.naviamp.storage.StorageRecoveryFailure
import app.naviamp.ui.NaviampRecoveryFailureUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException

data class NaviampRecoverableHostState<T>(
    val application: T? = null,
    val starting: Boolean = true,
    val failure: NaviampRecoveryFailureUi? = null,
    val confirmReset: Boolean = false,
)

/** Shared startup/retry/reset policy. Hosts implement only their resource and persistence effects. */
class NaviampRecoverableHostController<T>(
    private val open: (CoroutineScope, (Throwable) -> Unit) -> T,
    private val disposeApplication: (T) -> Unit,
    private val closeNative: () -> Unit,
    private val erase: () -> Boolean,
    private val context: CoroutineContext = Dispatchers.Main,
) {
    private val mutableState = MutableStateFlow(NaviampRecoverableHostState<T>())
    val state: StateFlow<NaviampRecoverableHostState<T>> = mutableState.asStateFlow()
    private var scope: CoroutineScope? = null
    private var closed = false
    private var generation = 0L

    fun start() {
        if (closed || mutableState.value.application != null) return
        mutableState.value = NaviampRecoverableHostState()
        val applicationScope = CoroutineScope(SupervisorJob() + context)
        val attempt = ++generation
        scope = applicationScope
        try {
            val app = open(applicationScope) { cause -> if (attempt == generation && !closed) reportFailure(cause) }
            mutableState.value = mutableState.value.copy(application = app, starting = false)
        } catch (cancelled: CancellationException) {
            release(); throw cancelled
        } catch (failure: Exception) {
            release()
            reportFailure(failure)
        }
    }

    fun retry() {
        if (closed) return
        release()
        mutableState.value = NaviampRecoverableHostState()
        start()
    }

    fun requestReset() { if (!closed) mutableState.value = mutableState.value.copy(confirmReset = true) }
    fun cancelReset() { mutableState.value = mutableState.value.copy(confirmReset = false) }
    fun confirmReset() {
        if (closed || !mutableState.value.confirmReset) return
        release()
        mutableState.value = NaviampRecoverableHostState(starting = false)
        try {
            if (erase()) start() else reportFailure(IllegalStateException())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { reportFailure(failure) }
    }

    fun dismissFailure() {
        if (mutableState.value.application != null) mutableState.value = mutableState.value.copy(failure = null)
    }

    fun close() { if (!closed) { closed = true; release() } }

    private fun release() {
        generation++
        val app = mutableState.value.application
        mutableState.value = mutableState.value.copy(application = null)
        try { if (app != null) disposeApplication(app) }
        finally {
            scope?.cancel(); scope = null
            closeNative()
        }
    }

    private fun reportFailure(cause: Throwable) {
        if ((cause as? StorageRecoveryException)?.reason == StorageRecoveryFailure.Conflict && mutableState.value.application != null) {
            // An uncertain/stale projection must be reopened; dismissing an overlay cannot repair it.
            try { release() } catch (cleanup: Throwable) { if (cleanup !== cause) cause.addSuppressed(cleanup) }
        }
        val failure = when ((cause as? StorageRecoveryException)?.reason) {
            StorageRecoveryFailure.Capacity -> NaviampRecoveryFailureUi.Capacity
            StorageRecoveryFailure.Conflict -> NaviampRecoveryFailureUi.Conflict
            StorageRecoveryFailure.Unreadable, StorageRecoveryFailure.UnsupportedVersion -> NaviampRecoveryFailureUi.Unreadable
            else -> NaviampRecoveryFailureUi.Unavailable
        }
        mutableState.value = mutableState.value.copy(starting = false, failure = failure, confirmReset = false)
    }
}

fun naviampRecoverableStoredHostController(
    open: (CoroutineScope, (Throwable) -> Unit) -> NaviampStoredApplication,
    closeNative: () -> Unit,
    erase: () -> Boolean,
): NaviampRecoverableHostController<NaviampStoredApplication> = NaviampRecoverableHostController(
    open = open, disposeApplication = { it.close() }, closeNative = closeNative, erase = erase,
)
