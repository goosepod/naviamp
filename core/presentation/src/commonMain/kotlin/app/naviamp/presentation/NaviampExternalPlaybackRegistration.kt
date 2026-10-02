package app.naviamp.presentation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The native registration effect; all scheduling and publication decisions remain in Core. */
interface NaviampExternalPlaybackRegistration {
    suspend fun awaitLoss()
    fun publish(snapshot: NaviampExternalPlaybackSnapshot)
    fun close()
}

/** Maintains an optional registration without affecting playback when the native service fails. */
fun CoroutineScope.maintainExternalPlaybackRegistration(
    snapshots: Flow<NaviampExternalPlaybackSnapshot>,
    controller: NaviampExternalPlaybackRegistrationController = NaviampExternalPlaybackRegistrationController(),
    register: suspend () -> NaviampExternalPlaybackRegistration,
): Job = launch {
    try {
        while (currentCoroutineContext().isActive) {
            var registration: NaviampExternalPlaybackRegistration? = null
            try {
                registration = register()
                controller.registered()
                val active = registration
                coroutineScope {
                    val publisher = launch { snapshots.collect(active::publish) }
                    try {
                        active.awaitLoss()
                    } finally {
                        publisher.cancelAndJoin()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Optional native media controls may be unavailable; keep the player running.
            } finally {
                registration?.runCatching { close() }
            }
            delay(controller.registrationLost() ?: break)
            if (!controller.retrying()) break
        }
    } finally {
        controller.close()
    }
}

enum class NaviampExternalPlaybackRegistrationState {
    Starting,
    Registered,
    WaitingToRetry,
    Closed,
}

/** Shared retry and lifecycle policy for an optional native media-control registration. */
class NaviampExternalPlaybackRegistrationController(
    private val initialRetryDelayMillis: Long = 1_000L,
    private val maximumRetryDelayMillis: Long = 30_000L,
) {
    var state: NaviampExternalPlaybackRegistrationState = NaviampExternalPlaybackRegistrationState.Starting
        private set
    private var retryDelayMillis = initialRetryDelayMillis.coerceIn(1L, maximumRetryDelayMillis.coerceAtLeast(1L))

    fun registered() {
        if (state == NaviampExternalPlaybackRegistrationState.Closed) return
        retryDelayMillis = initialRetryDelayMillis.coerceIn(1L, maximumRetryDelayMillis.coerceAtLeast(1L))
        state = NaviampExternalPlaybackRegistrationState.Registered
    }

    fun registrationLost(): Long? {
        if (state == NaviampExternalPlaybackRegistrationState.Closed) return null
        state = NaviampExternalPlaybackRegistrationState.WaitingToRetry
        val delay = retryDelayMillis
        retryDelayMillis = if (delay >= maximumRetryDelayMillis / 2) {
            maximumRetryDelayMillis.coerceAtLeast(1L)
        } else {
            delay * 2
        }
        return delay
    }

    fun retrying(): Boolean {
        if (state == NaviampExternalPlaybackRegistrationState.Closed) return false
        state = NaviampExternalPlaybackRegistrationState.Starting
        return true
    }

    fun close() {
        state = NaviampExternalPlaybackRegistrationState.Closed
    }
}
