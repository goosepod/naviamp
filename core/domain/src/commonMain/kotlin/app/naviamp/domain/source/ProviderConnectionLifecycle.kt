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
    val pruneUnusedSourceScopesBeforeEpochMillis: Long? = null,
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
    request.pruneUnusedSourceScopesBeforeEpochMillis?.let { cutoff ->
        cacheMaintenanceRepository?.pruneUnusedSourceScopes(
            activeSourceIds = setOf(source.id),
            lastConnectedBeforeEpochMillis = cutoff,
        )
    }
    return ProviderConnectionSession(
        connection = connection,
        provider = provider,
        sourceId = source.id,
        validation = validation,
        smartPlaylistAuthWarning = request.smartPlaylistAuthWarning(prepared),
    )
}

fun connectionFailureStatus(error: Throwable, fallback: String = "Connection failed."): String {
    val message = error.message?.trim().orEmpty()
    if (message.isEmpty()) return fallback

    val normalized = message.lowercase()
    if (
        "tls" in normalized ||
        "ssl" in normalized ||
        "certificate" in normalized ||
        "nsurlerrordomain code=-1200" in normalized
    ) {
        return "TLS certificate verification failed. Enable \"Skip TLS certificate verification\" " +
            "under Show Advanced only if you trust this server."
    }
    if ("timed out" in normalized || "timeout" in normalized) {
        return "The server did not respond in time."
    }
    if (
        "unknown host" in normalized ||
        "unknownhost" in normalized ||
        "unable to resolve" in normalized ||
        "could not resolve" in normalized
    ) {
        return "The server address could not be resolved."
    }

    // Native networking failures can include full URLs, credentials, certificates, and internal
    // object descriptions. Only pass through a short, plain provider-authored message.
    val containsSensitiveStructure = listOf(
        "://",
        "userinfo=",
        "nsunderlyingerror",
        "nsurlerror",
        "peertrust",
        "<sectrustref",
        "\n",
        "\r",
    ).any { marker -> message.contains(marker, ignoreCase = true) }
    return if (message.length <= 240 && !containsSensitiveStructure) message else fallback
}

const val UnusedSourceScopeRetentionMillis: Long = 30L * 24L * 60L * 60L * 1_000L

fun unusedSourceScopeCleanupCutoff(nowEpochMillis: Long): Long =
    nowEpochMillis - UnusedSourceScopeRetentionMillis
