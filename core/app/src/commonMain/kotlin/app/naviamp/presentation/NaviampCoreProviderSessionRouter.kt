package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionAttemptPlan
import app.naviamp.domain.provider.MediaProvider

data class NaviampCoreProviderSessionRoute(
    val providerIds: Set<String>,
    val sessionPort: NaviampCoreProviderSessionPort,
) {
    init {
        require(providerIds.isNotEmpty()) { "A provider-session route needs at least one provider ID." }
        require(providerIds.all { it.isNotBlank() }) { "Provider-session route IDs cannot be blank." }
    }
}

/** Shared provider-kind router. Hosts register compiled adapters; Core owns every routing decision. */
class NaviampCoreProviderSessionRouter(
    routes: List<NaviampCoreProviderSessionRoute>,
) : NaviampCoreProviderSessionPort {
    private val routes = routes.toList().also { configured ->
        require(configured.isNotEmpty()) { "At least one provider-session route is required." }
        val ids = configured.flatMap { it.providerIds }.map(String::lowercase)
        require(ids.size == ids.distinct().size) { "Provider-session route IDs must be unique." }
    }
    private val routesByProviderId = this.routes
        .flatMap { route -> route.providerIds.map { id -> id.lowercase() to route } }
        .toMap()
    private val restoredRoute = this.routes.firstOrNull { route ->
        route.sessionPort.currentSourceId() != null
    }
    private var inventory = (restoredRoute ?: this.routes.first()).sessionPort.initialInventory()
    private var activeRoute: NaviampCoreProviderSessionRoute? = restoredRoute
        ?: inventory.currentSourceId
            ?.let(::savedConnection)
            ?.let { saved -> routeForProvider(saved.providerId) }
        ?: this.routes.firstOrNull { route -> route.sessionPort.currentProvider() != null }

    override val providerSource = NaviampCoreMediaProviderSource(::currentProvider)

    override fun currentProvider(): MediaProvider? = activeRoute?.sessionPort?.currentProvider()

    override fun currentSourceId(): String? =
        activeRoute?.sessionPort?.currentSourceId() ?: inventory.currentSourceId

    override fun initialInventory(): NaviampCoreConnectionInventory = inventory

    override suspend fun connect(
        request: NaviampCoreConnectionRequest,
        plan: NaviampConnectionAttemptPlan,
    ): NaviampCoreConnectedSession {
        val route = when (request) {
            is NaviampCoreConnectionRequest.Form -> routeForProvider(request.form.providerId)
            is NaviampCoreConnectionRequest.Saved -> routeForSaved(request.id)
        } ?: unsupportedProvider(request)
        val connected = route.sessionPort.connect(request, plan)
        activeRoute = route
        inventory = connected.inventory
        return connected
    }

    override suspend fun editableConnection(id: String): NaviampCoreEditableConnection =
        (routeForSaved(id) ?: unsupportedSaved(id)).sessionPort.editableConnection(id)

    override suspend fun deleteConnection(id: String): NaviampCoreConnectionInventory {
        val route = routeForSaved(id) ?: routes.first()
        val deletedActiveSource = inventory.currentSourceId == id
        inventory = route.sessionPort.deleteConnection(id)
        if (deletedActiveSource) activeRoute = null
        return inventory
    }

    override suspend fun smartPlaylistProvider(password: String?): MediaProvider? =
        activeRoute?.sessionPort?.smartPlaylistProvider(password)

    override suspend fun refreshActiveSession(): Boolean =
        activeRoute?.sessionPort?.refreshActiveSession() ?: false

    override suspend fun persistActiveSession() {
        activeRoute?.sessionPort?.persistActiveSession()
    }

    override suspend fun clearActiveSession() {
        routes.forEach { route -> route.sessionPort.clearActiveSession() }
        activeRoute = null
        inventory = inventory.copy(currentSourceId = null)
    }

    private fun routeForSaved(id: String): NaviampCoreProviderSessionRoute? =
        savedConnection(id)?.let { saved -> routeForProvider(saved.providerId) }

    private fun savedConnection(id: String): NaviampCoreSavedConnectionRecord? =
        inventory.connections.firstOrNull { it.id == id }

    private fun routeForProvider(providerId: String): NaviampCoreProviderSessionRoute? =
        routesByProviderId[providerId.trim().lowercase()]

    private fun unsupportedProvider(request: NaviampCoreConnectionRequest): Nothing {
        val providerId = when (request) {
            is NaviampCoreConnectionRequest.Form -> request.form.providerId
            is NaviampCoreConnectionRequest.Saved -> savedConnection(request.id)?.providerId
        }.orEmpty().ifBlank { "unknown" }
        error("$providerId support is not available yet.")
    }

    private fun unsupportedSaved(id: String): Nothing {
        val providerId = savedConnection(id)?.providerId ?: "unknown"
        error("$providerId support is not available yet.")
    }
}
