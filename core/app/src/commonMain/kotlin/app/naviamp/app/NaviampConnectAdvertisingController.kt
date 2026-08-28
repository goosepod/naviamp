package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectDiscoveryMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface NaviampConnectAdvertisingStatus {
    data object Idle : NaviampConnectAdvertisingStatus
    data class Starting(val advertisement: NaviampConnectAdvertisement) : NaviampConnectAdvertisingStatus
    data class Advertising(
        val advertisement: NaviampConnectAdvertisement,
        val registeredServiceName: String,
    ) : NaviampConnectAdvertisingStatus
    data class Failed(val message: String) : NaviampConnectAdvertisingStatus
}

data class NaviampConnectRegistrationService(
    val serviceName: String,
    val port: Int,
    val textAttributes: Map<String, String>,
) {
    init {
        require(serviceName.isNotBlank()) { "A registration service name is required." }
        require(port in 1..65_535) { "A registration service port must be valid." }
    }
}

sealed interface NaviampConnectAdvertisingStartResult {
    data object Started : NaviampConnectAdvertisingStartResult
    data object PermissionDenied : NaviampConnectAdvertisingStartResult
    data class Unavailable(val message: String) : NaviampConnectAdvertisingStartResult
}

interface NaviampConnectAdvertisingListener {
    fun onServiceRegistered(registeredServiceName: String)
    fun onRegistrationFailed(message: String)
}

/** Narrow host effect for native DNS-SD registration and its unavoidable resource lifetime. */
interface NaviampConnectAdvertisingEffect {
    fun start(
        service: NaviampConnectRegistrationService,
        listener: NaviampConnectAdvertisingListener,
    ): NaviampConnectAdvertisingStartResult

    fun stop()
}

/** Shared target-side owner for when and what an explicit pairing mode advertises. */
class NaviampConnectAdvertisingController(
    private val effect: NaviampConnectAdvertisingEffect,
    private val nowEpochMillis: () -> Long,
) : NaviampConnectAdvertisingListener {
    private val mutableState = MutableStateFlow<NaviampConnectAdvertisingStatus>(NaviampConnectAdvertisingStatus.Idle)

    val state: StateFlow<NaviampConnectAdvertisingStatus> = mutableState.asStateFlow()

    fun start(advertisement: NaviampConnectAdvertisement) {
        require(advertisement.expiresAtEpochMillis > nowEpochMillis()) {
            "An expired pairing advertisement cannot be registered."
        }
        if (mutableState.value !is NaviampConnectAdvertisingStatus.Idle) stop()
        mutableState.value = NaviampConnectAdvertisingStatus.Starting(advertisement)
        val service = NaviampConnectRegistrationService(
            serviceName = "Naviamp ${advertisement.displayName}",
            port = advertisement.port,
            textAttributes = NaviampConnectDiscoveryMetadata.encode(advertisement),
        )
        when (val result = effect.start(service, this)) {
            NaviampConnectAdvertisingStartResult.Started -> Unit
            NaviampConnectAdvertisingStartResult.PermissionDenied -> {
                mutableState.value = NaviampConnectAdvertisingStatus.Failed(
                    "Local-network permission is required to advertise this Naviamp target.",
                )
            }
            is NaviampConnectAdvertisingStartResult.Unavailable -> {
                mutableState.value = NaviampConnectAdvertisingStatus.Failed(result.message)
            }
        }
    }

    fun stop() {
        if (mutableState.value !is NaviampConnectAdvertisingStatus.Idle) effect.stop()
        mutableState.value = NaviampConnectAdvertisingStatus.Idle
    }

    fun refreshExpiry(): Boolean {
        val advertisement = when (val current = mutableState.value) {
            is NaviampConnectAdvertisingStatus.Starting -> current.advertisement
            is NaviampConnectAdvertisingStatus.Advertising -> current.advertisement
            is NaviampConnectAdvertisingStatus.Idle,
            is NaviampConnectAdvertisingStatus.Failed,
            -> return false
        }
        if (advertisement.expiresAtEpochMillis > nowEpochMillis()) return false
        stop()
        return true
    }

    override fun onServiceRegistered(registeredServiceName: String) {
        val starting = mutableState.value as? NaviampConnectAdvertisingStatus.Starting ?: return
        if (registeredServiceName.isBlank()) {
            onRegistrationFailed("The platform registered an invalid empty service name.")
            return
        }
        mutableState.value = NaviampConnectAdvertisingStatus.Advertising(
            advertisement = starting.advertisement,
            registeredServiceName = registeredServiceName,
        )
    }

    override fun onRegistrationFailed(message: String) {
        effect.stop()
        mutableState.value = NaviampConnectAdvertisingStatus.Failed(message)
    }
}
