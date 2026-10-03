package app.naviamp.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Raw DNS-SD result. [serviceKey] includes the native interface/service identity. */
data class NaviampCastResolvedService(
    val serviceKey: String,
    val addresses: List<String>,
    val port: Int,
    val textAttributes: Map<String, String>,
    val localAddress: String? = null,
)

data class NaviampCastDiscoveredTarget(
    val target: NaviampCastTarget,
    val endpoints: List<NaviampCastEndpoint>,
)

data class NaviampCastEndpoint(val host: String, val port: Int, val localAddress: String? = null) {
    init {
        require(host.isNotBlank())
        require(port in 1..65_535)
        require(localAddress == null || localAddress.isNotBlank())
    }
}

enum class NaviampCastDiscoveryProblem { PermissionDenied, Unavailable, Failed }

data class NaviampCastDiscoveryState(
    val discovering: Boolean = false,
    val targets: List<NaviampCastDiscoveredTarget> = emptyList(),
    val problem: NaviampCastDiscoveryProblem? = null,
)

interface NaviampCastDiscoveryListener {
    fun onServiceResolved(service: NaviampCastResolvedService)
    fun onServiceLost(serviceKey: String)
    fun onDiscoveryFailed(problem: NaviampCastDiscoveryProblem)
}

/** DNS-SD boundary only. Core interprets TXT data, merges interfaces, and owns result lifetime. */
interface NaviampCastDiscoveryEffect {
    fun start(serviceType: String, listener: NaviampCastDiscoveryListener)
    fun refresh() {}
    fun stop()
}

/** Call actions and deliver native callbacks on the owner's serialized context. */
class NaviampCastDiscoveryController(
    private val effect: NaviampCastDiscoveryEffect,
    private val nowEpochMillis: () -> Long,
    private val resultLifetimeMillis: Long = 120_000,
) {
    init { require(resultLifetimeMillis > 0) }

    private data class Service(
        val target: NaviampCastTarget,
        val endpoints: List<NaviampCastEndpoint>,
        val expiresAt: Long,
        val revision: Long,
    )

    private val services = mutableMapOf<String, Service>()
    private val mutableState = MutableStateFlow(NaviampCastDiscoveryState())
    private var generation = 0L
    private var revision = 0L
    val state: StateFlow<NaviampCastDiscoveryState> = mutableState.asStateFlow()

    fun start() {
        if (state.value.discovering) return
        val current = ++generation
        services.clear()
        mutableState.value = NaviampCastDiscoveryState(discovering = true)
        val listener = object : NaviampCastDiscoveryListener {
            override fun onServiceResolved(service: NaviampCastResolvedService) {
                if (current != generation || !state.value.discovering) return
                val id = service.textAttributes["id"]?.trim()?.takeIf(String::isNotEmpty) ?: return
                val name = service.textAttributes["fn"]?.trim()?.takeIf(String::isNotEmpty) ?: return
                if (service.serviceKey.isBlank() || service.port !in 1..65_535) return
                val endpoints = service.addresses.map(String::trim).filter(String::isNotEmpty)
                    .distinct().map { NaviampCastEndpoint(it, service.port, service.localAddress?.trim()?.takeIf(String::isNotEmpty)) }
                if (endpoints.isEmpty()) return
                services[service.serviceKey] = Service(
                    NaviampCastTarget(id, name), endpoints,
                    nowEpochMillis().let { now ->
                        if (now > Long.MAX_VALUE - resultLifetimeMillis) Long.MAX_VALUE
                        else now + resultLifetimeMillis
                    }, ++revision,
                )
                publish()
            }

            override fun onServiceLost(serviceKey: String) {
                if (current != generation || !state.value.discovering) return
                services.remove(serviceKey)
                publish()
            }

            override fun onDiscoveryFailed(problem: NaviampCastDiscoveryProblem) {
                if (current != generation || !state.value.discovering) return
                // Invalidate before cleanup: native stop can synchronously deliver callbacks.
                ++generation
                services.clear()
                mutableState.value = NaviampCastDiscoveryState(problem = problem)
                effect.stop()
            }
        }
        try {
            effect.start(ServiceType, listener)
        } catch (_: Exception) {
            listener.onDiscoveryFailed(NaviampCastDiscoveryProblem.Failed)
        }
    }

    fun stop() {
        val wasRunning = state.value.discovering
        ++generation
        services.clear()
        mutableState.value = NaviampCastDiscoveryState()
        if (wasRunning) effect.stop()
    }

    /** The shared owner schedules this while discovery is visible; adapters do not expire results. */
    fun refreshExpiry() {
        if (!state.value.discovering) return
        publish()
    }

    fun refresh() {
        if (state.value.discovering) effect.refresh()
    }

    /** Selection rechecks expiry so a stale row cannot initiate a connection. */
    fun target(id: String): NaviampCastDiscoveredTarget? {
        refreshExpiry()
        return state.value.targets.firstOrNull { it.target.id == id }
    }

    private fun publish() {
        val now = nowEpochMillis()
        services.entries.removeAll { it.value.expiresAt <= now }
        val targets = services.values.groupBy { it.target.id }.values.map { records ->
            NaviampCastDiscoveredTarget(
                target = records.maxBy { it.revision }.target,
                endpoints = records.sortedByDescending { it.revision }.flatMap { it.endpoints }.distinct(),
            )
        }.sortedWith(compareBy<NaviampCastDiscoveredTarget> { it.target.displayName.lowercase() }
            .thenBy { it.target.id })
        mutableState.value = state.value.copy(targets = targets)
    }

    companion object {
        const val ServiceType = "_googlecast._tcp.local."
    }
}
