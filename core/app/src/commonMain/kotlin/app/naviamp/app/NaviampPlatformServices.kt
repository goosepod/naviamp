package app.naviamp.app

import app.naviamp.domain.app.PlatformCapabilities

/** The current network view supplied by a host without exposing an operating-system API. */
data class NaviampConnectivitySnapshot(
    val available: Boolean,
    val mobileData: Boolean = false,
)

fun interface NaviampConnectivityMonitor {
    fun currentSnapshot(): NaviampConnectivitySnapshot
}

/**
 * Session work that must be coordinated with the host lifecycle.
 *
 * Android implementations may delegate playback work to the foreground service so an Activity
 * never becomes the owner of a long-lived playback session.
 */
interface NaviampApplicationSession {
    suspend fun restore()

    suspend fun enterForeground() = Unit

    suspend fun enterBackground() = Unit

    suspend fun shutdown() = Unit
}

enum class NaviampRuntimeOperation {
    Restore,
    EnterForeground,
    EnterBackground,
    Shutdown,
}

data class NaviampRuntimeError(
    val operation: NaviampRuntimeOperation,
    val message: String,
)

fun interface NaviampRuntimeErrorReporter {
    fun report(error: NaviampRuntimeError, cause: Throwable?)
}

/**
 * The first shared composition boundary. Additional narrow services are added as their owners move
 * out of the Android and Desktop composition roots.
 */
data class NaviampPlatformServices(
    val capabilities: PlatformCapabilities,
    val session: NaviampApplicationSession,
    val connectivity: NaviampConnectivityMonitor,
    val errorReporter: NaviampRuntimeErrorReporter,
)
