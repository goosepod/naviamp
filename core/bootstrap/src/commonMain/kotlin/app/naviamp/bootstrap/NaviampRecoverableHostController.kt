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

/**
 * Shared startup/retry/reset policy. Hosts implement only their resource and persistence effects.
 * A host without a reversible-by-setup erase effect passes a null [erase]; reset is then unavailable.
 */
class NaviampRecoverableHostController<T>(
    private val open: (CoroutineScope, (Throwable) -> Unit) -> T,
    private val disposeApplication: (T) -> Unit,
    private val closeNative: () -> Unit,
    private val erase: (() -> Boolean)?,
    private val context: CoroutineContext = Dispatchers.Main,
    private val attachApplication: (T, CoroutineScope, (() -> Unit) -> Unit, (Throwable) -> Unit) -> Unit = { _, _, _, _ -> },
) {
    private val mutableState = MutableStateFlow(NaviampRecoverableHostState<T>())
    val state: StateFlow<NaviampRecoverableHostState<T>> = mutableState.asStateFlow()
    val resetAvailable: Boolean get() = erase != null
    private var scope: CoroutineScope? = null
    private var closed = false
    private var generation = 0L
    private var openingApplication: T? = null
    private var attachments: NaviampHostAttachments? = null

    fun start() {
        if (closed || mutableState.value.application != null) return
        mutableState.value = NaviampRecoverableHostState()
        val applicationScope = CoroutineScope(SupervisorJob() + context)
        val attempt = ++generation
        scope = applicationScope
        val lifetime = NaviampHostAttachments().also { attachments = it }
        val failureCallback: (Throwable) -> Unit = { cause -> if (attempt == generation && !closed) reportFailure(cause) }
        val attachmentFailure: (Throwable) -> Unit = { cause ->
            if (attempt == generation && !closed) {
                // Critical host effects cannot remain advertised after their native surface fails.
                try { release() } catch (cleanup: Throwable) { if (cleanup !== cause) cause.addSuppressed(cleanup) }
                reportFailure(cause)
            }
        }
        try {
            val app = open(applicationScope, failureCallback)
            openingApplication = app
            attachApplication(app, applicationScope, lifetime::retain, attachmentFailure)
            if (attempt != generation || closed) return
            mutableState.value = mutableState.value.copy(application = app, starting = false)
            openingApplication = null
        } catch (cancelled: CancellationException) {
            try { release() } catch (cleanup: Throwable) { if (cleanup !== cancelled) cancelled.addSuppressed(cleanup) }
            throw cancelled
        } catch (failure: Exception) {
            try { release() } catch (cleanup: Throwable) { if (cleanup !== failure) failure.addSuppressed(cleanup) }
            if (!closed) reportFailure(failure)
        }
    }

    fun retry() {
        if (closed) return
        try { release() } catch (failure: Exception) { reportFailure(failure); return }
        mutableState.value = NaviampRecoverableHostState()
        start()
    }

    fun requestReset() { if (!closed && erase != null) mutableState.value = mutableState.value.copy(confirmReset = true) }
    fun cancelReset() { mutableState.value = mutableState.value.copy(confirmReset = false) }
    fun confirmReset() {
        val erase = erase
        if (closed || erase == null || !mutableState.value.confirmReset) return
        try {
            release()
            mutableState.value = NaviampRecoverableHostState(starting = false)
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
        val app = mutableState.value.application ?: openingApplication
        openingApplication = null
        val lifetime = attachments
        attachments = null
        mutableState.value = mutableState.value.copy(application = null)
        try { lifetime?.close() }
        finally {
            try { if (app != null) disposeApplication(app) }
            finally {
                scope?.cancel(); scope = null
                closeNative()
            }
        }
    }

    private fun reportFailure(cause: Throwable) {
        if ((cause as? StorageRecoveryException)?.reason == StorageRecoveryFailure.Conflict &&
            (mutableState.value.application != null || openingApplication != null)) {
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
    erase: (() -> Boolean)?,
    attachApplication: (NaviampStoredApplication, CoroutineScope, (() -> Unit) -> Unit, (Throwable) -> Unit) -> Unit = { _, _, _, _ -> },
): NaviampRecoverableHostController<NaviampStoredApplication> = NaviampRecoverableHostController(
    open = open, disposeApplication = { it.close() }, closeNative = closeNative, erase = erase,
    attachApplication = attachApplication,
)

/** Own close operations before native registration can call back or fail during startup. */
private class NaviampHostAttachments {
    private var closed = false
    private val releases = mutableListOf<() -> Unit>()
    fun retain(close: () -> Unit) { if (closed) close() else releases += close }
    fun close() {
        if (closed) return
        closed = true
        val pending = releases.asReversed().toList()
        releases.clear()
        var failure: Throwable? = null
        pending.forEach { release ->
            try { release() } catch (cause: Throwable) {
                if (failure == null) failure = cause else if (failure !== cause) failure!!.addSuppressed(cause)
            }
        }
        failure?.let { throw it }
    }
}
