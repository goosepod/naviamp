package app.naviamp.provider.navidrome

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Provider-common owner for Navidrome's rotating native JWT.
 *
 * Core decides when to renew. A host supplies only its active-provider slot, protected repository,
 * and any platform TLS initialization required before the password exchange.
 */
class NavidromeNativeSessionController(
    private val currentProvider: () -> NavidromeProvider?,
    private val currentSourceId: () -> String? = { null },
    private val savedConnection: () -> NavidromeConnection?,
    private val replaceProvider: (NavidromeProvider) -> Unit,
    private val repository: ProviderMediaSourceRepository?,
    private val prepareConnection: (NavidromeConnection) -> Unit = {},
    private val authenticate: suspend (NavidromeConnection, String) -> NavidromeConnection = { connection, password ->
        connection.withNativeTokenFromPassword(password, required = true)
    },
    private val createProvider: (NavidromeConnection) -> NavidromeProvider = { NavidromeProvider(it) },
) {
    suspend fun provider(password: String?): NavidromeProvider? {
        val active = currentProvider() ?: return null
        val suppliedPassword = password?.takeIf { it.isNotBlank() } ?: return active
        val saved = savedConnection() ?: return active
        val sourceId = currentSourceId()
        prepareConnection(saved)
        val refreshedConnection = authenticate(saved, suppliedPassword)
        val refreshedProvider = createProvider(refreshedConnection)
        // Native authentication alone does not negotiate OpenSubsonic capabilities. Publish only
        // a fully initialized provider, just as for the initial connection.
        refreshedProvider.validateConnection()
        currentCoroutineContext().ensureActive()
        if (currentProvider() !== active || currentSourceId() != sourceId) return null
        return refreshedProvider.also {
            replaceProvider(refreshedProvider)
            persist(refreshedProvider)
        }
    }

    suspend fun refresh(): Boolean {
        val active = currentProvider() ?: return false
        return try {
            active.refreshNativeSession().also { refreshed -> if (refreshed) persist(active) }
        } catch (error: Throwable) {
            // A rejected native JWT is cleared by the provider. Persist that state so background
            // refresh does not retry the same invalid credential after every app restart.
            persist(active)
            throw error
        }
    }

    fun persist() {
        currentProvider()?.let(::persist)
    }

    private fun persist(active: NavidromeProvider) {
        repository?.upsertProviderMediaSource(
            connection = active.connectionWithCurrentNativeToken().toProviderMediaSourceConnection(),
            cacheNamespace = active.cacheNamespace,
            providerId = active.id.value,
            preferredSourceId = currentSourceId(),
        )
    }
}

fun NavidromeConnection.toProviderMediaSourceConnection(): ProviderMediaSourceConnection =
    ProviderMediaSourceConnection(
        displayName = resolvedDisplayName(),
        baseUrl = baseUrl,
        username = username,
        token = token,
        salt = salt,
        authenticationMode = authenticationMode,
        nativeToken = nativeToken,
        tlsSettings = tlsSettings,
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    )
