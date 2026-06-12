package app.naviamp.domain.source

import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider

data class ProviderConnectionSession<Connection, Provider : MediaProvider>(
    val connection: Connection,
    val provider: Provider,
    val sourceId: String,
    val validation: ConnectionValidation,
    val smartPlaylistAuthWarning: String? = null,
)

data class ProviderConnectionLifecycleRequest<InputConnection, Connection, PreparedConnection, Provider : MediaProvider>(
    val connection: InputConnection,
    val prepareConnection: suspend (InputConnection) -> PreparedConnection,
    val preparedConnection: (PreparedConnection) -> Connection,
    val provider: (Connection) -> Provider,
    val mediaSourceConnection: (Connection) -> ProviderMediaSourceConnection,
    val applyTlsDefaults: (Connection) -> Unit = {},
    val smartPlaylistAuthWarning: (PreparedConnection) -> String? = { null },
    val clearProviderData: Boolean = false,
)

suspend fun <InputConnection, Connection, PreparedConnection, Provider : MediaProvider> openProviderConnectionSession(
    request: ProviderConnectionLifecycleRequest<InputConnection, Connection, PreparedConnection, Provider>,
    cacheMaintenanceRepository: CacheMaintenanceRepository<*>? = null,
    providerMediaSourceRepository: ProviderMediaSourceRepository,
): ProviderConnectionSession<Connection, Provider> {
    val prepared = request.prepareConnection(request.connection)
    val connection = request.preparedConnection(prepared)
    request.applyTlsDefaults(connection)
    val provider = request.provider(connection)
    val validation = provider.validateConnection()
    if (request.clearProviderData) {
        cacheMaintenanceRepository?.clearProviderData()
    }
    val source = providerMediaSourceRepository.upsertProviderMediaSource(
        connection = request.mediaSourceConnection(connection),
        cacheNamespace = provider.cacheNamespace,
        providerId = provider.id.value,
    )
    return ProviderConnectionSession(
        connection = connection,
        provider = provider,
        sourceId = source.id,
        validation = validation,
        smartPlaylistAuthWarning = request.smartPlaylistAuthWarning(prepared),
    )
}

fun connectionFailureStatus(error: Throwable, fallback: String = "Connection failed."): String =
    error.message ?: fallback
