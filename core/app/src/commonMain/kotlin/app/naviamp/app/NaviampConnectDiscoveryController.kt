package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectDiscoveryMetadata
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.negotiateNaviampConnectProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NaviampConnectDiscoveryStatus {
    Idle,
    Discovering,
}

sealed interface NaviampConnectDiscoveryProblem {
    data object PermissionDenied : NaviampConnectDiscoveryProblem
    data class Unavailable(val message: String) : NaviampConnectDiscoveryProblem
    data class Failed(val message: String) : NaviampConnectDiscoveryProblem
    data class TargetIdentityChanged(val instanceId: String) : NaviampConnectDiscoveryProblem
}

data class NaviampConnectDiscoveryState(
    val status: NaviampConnectDiscoveryStatus = NaviampConnectDiscoveryStatus.Idle,
    val targets: List<NaviampConnectDiscoveredTarget> = emptyList(),
    val problem: NaviampConnectDiscoveryProblem? = null,
)

sealed interface NaviampConnectDiscoveryStartResult {
    data object Started : NaviampConnectDiscoveryStartResult
    data object PermissionDenied : NaviampConnectDiscoveryStartResult
    data class Unavailable(val message: String) : NaviampConnectDiscoveryStartResult
}

interface NaviampConnectDiscoveryListener {
    fun onServiceResolved(service: NaviampConnectResolvedService)
    fun onServiceLost(serviceName: String)
    fun onDiscoveryFailed(message: String)
}

data class NaviampConnectResolvedService(
    val serviceName: String,
    val addresses: List<String>,
    val port: Int,
    val textAttributes: Map<String, String>,
) {
    init {
        require(serviceName.isNotBlank()) { "A resolved service name is required." }
        require(addresses.isNotEmpty()) { "At least one resolved service address is required." }
        require(addresses.none(String::isBlank)) { "Resolved service addresses must not be blank." }
        require(port in 1..65_535) { "A resolved service port must be valid." }
    }
}

data class NaviampConnectDiscoveredTarget(
    val serviceName: String,
    val addresses: List<String>,
    val advertisement: NaviampConnectAdvertisement,
)

/**
 * Narrow host effect for DNS-SD/mDNS. Hosts translate native callbacks and do not filter, retain,
 * sort, expire, or interpret discovery results.
 */
interface NaviampConnectDiscoveryEffect {
    fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult
    fun stop()
}

/** Shared discovery lifecycle and presentation-state owner for every controller platform. */
class NaviampConnectDiscoveryController(
    private val effect: NaviampConnectDiscoveryEffect,
    private val localProtocolRange: NaviampConnectProtocolRange = NaviampConnectProtocolRange(),
    private val nowEpochMillis: () -> Long,
    private val resultLifetimeMillis: Long = 120_000,
) : NaviampConnectDiscoveryListener {
    init {
        require(resultLifetimeMillis > 0) { "The discovery result lifetime must be positive." }
    }
    private val mutableState = MutableStateFlow(NaviampConnectDiscoveryState())

    val state: StateFlow<NaviampConnectDiscoveryState> = mutableState.asStateFlow()

    fun start() {
        if (mutableState.value.status == NaviampConnectDiscoveryStatus.Discovering) return
        mutableState.value = NaviampConnectDiscoveryState(
            status = NaviampConnectDiscoveryStatus.Discovering,
        )
        when (val result = effect.start(this)) {
            NaviampConnectDiscoveryStartResult.Started -> Unit
            NaviampConnectDiscoveryStartResult.PermissionDenied -> {
                mutableState.value = NaviampConnectDiscoveryState(
                    problem = NaviampConnectDiscoveryProblem.PermissionDenied,
                )
            }
            is NaviampConnectDiscoveryStartResult.Unavailable -> {
                mutableState.value = NaviampConnectDiscoveryState(
                    problem = NaviampConnectDiscoveryProblem.Unavailable(result.message),
                )
            }
        }
    }

    fun stop() {
        if (mutableState.value.status == NaviampConnectDiscoveryStatus.Discovering) effect.stop()
        mutableState.value = NaviampConnectDiscoveryState()
    }

    /** Removes services whose DNS-SD lifetime has elapsed when no native removal callback arrived. */
    fun refreshExpiry() {
        updateTargets(mutableState.value.targets.filterNot(::isExpired))
    }

    override fun onServiceResolved(service: NaviampConnectResolvedService) {
        if (mutableState.value.status != NaviampConnectDiscoveryStatus.Discovering) return
        val advertisement = NaviampConnectDiscoveryMetadata.decode(
            attributes = service.textAttributes,
            port = service.port,
            expiresAtEpochMillis = nowEpochMillis() + resultLifetimeMillis,
        ) ?: return
        if (negotiateNaviampConnectProtocol(localProtocolRange, advertisement.protocolRange) == null) return

        val existing = mutableState.value.targets.firstOrNull {
            it.advertisement.instanceId == advertisement.instanceId
        }
        if (existing != null &&
            existing.advertisement.identityFingerprint != advertisement.identityFingerprint
        ) {
            mutableState.value = mutableState.value.copy(
                targets = mutableState.value.targets.filterNot {
                    it.advertisement.instanceId == advertisement.instanceId
                },
                problem = NaviampConnectDiscoveryProblem.TargetIdentityChanged(advertisement.instanceId),
            )
            return
        }
        val target = NaviampConnectDiscoveredTarget(
            serviceName = service.serviceName,
            addresses = service.addresses.distinct(),
            advertisement = advertisement,
        )
        updateTargets(
            mutableState.value.targets.filterNot {
                it.advertisement.instanceId == advertisement.instanceId || it.serviceName == service.serviceName
            } + target,
            clearProblem = true,
        )
    }

    override fun onServiceLost(serviceName: String) {
        updateTargets(mutableState.value.targets.filterNot { it.serviceName == serviceName })
    }

    override fun onDiscoveryFailed(message: String) {
        effect.stop()
        mutableState.value = NaviampConnectDiscoveryState(
            problem = NaviampConnectDiscoveryProblem.Failed(message),
        )
    }

    private fun isExpired(target: NaviampConnectDiscoveredTarget): Boolean =
        target.advertisement.expiresAtEpochMillis <= nowEpochMillis()

    private fun updateTargets(
        targets: List<NaviampConnectDiscoveredTarget>,
        clearProblem: Boolean = false,
    ) {
        mutableState.value = mutableState.value.copy(
            targets = targets.sortedWith(
                compareBy<NaviampConnectDiscoveredTarget> { it.advertisement.displayName.lowercase() }
                    .thenBy { it.advertisement.instanceId },
            ),
            problem = if (clearProblem) null else mutableState.value.problem,
        )
    }
}
