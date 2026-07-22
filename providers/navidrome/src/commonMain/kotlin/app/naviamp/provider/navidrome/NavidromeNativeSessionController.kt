package app.naviamp.provider.navidrome

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository

/**
 * Provider-common owner for Navidrome's rotating native JWT.
 *
 * Core decides when to renew. A host supplies only its active-provider slot, protected repository,
 * and any platform TLS initialization required before the password exchange.
 */
class NavidromeNativeSessionController(
    private val currentProvider: () -> NavidromeProvider?,
    private val savedConnection: () -> NavidromeConnection?,
    private val replaceProvider: (NavidromeProvider) -> Unit,
    private val repository: ProviderMediaSourceRepository?,
    private val prepareConnection: (NavidromeConnection) -> Unit = {},
    private val authenticate: suspend (NavidromeConnection, String) -> NavidromeConnection = { connection, password ->
        connection.withNativeTokenFromPassword(password, required = true)
    },
) {
    suspend fun provider(password: String?): NavidromeProvider? {
        val active = currentProvider() ?: return null
        val suppliedPassword = password?.takeIf { it.isNotBlank() } ?: return active
        val saved = savedConnection() ?: return active
        prepareConnection(saved)
        val refreshedConnection = authenticate(saved, suppliedPassword)
        return NavidromeProvider(refreshedConnection).also { refreshedProvider ->
            replaceProvider(refreshedProvider)
            persist(refreshedProvider)
        }
    }

    suspend fun refresh(): Boolean {
        val active = currentProvider() ?: return false
        val refreshed = active.refreshNativeSession()
        if (refreshed) persist(active)
        return refreshed
    }

    fun persist() {
        currentProvider()?.let(::persist)
    }

    private fun persist(active: NavidromeProvider) {
        repository?.upsertProviderMediaSource(
            connection = active.connectionWithCurrentNativeToken().toProviderMediaSourceConnection(),
            cacheNamespace = active.cacheNamespace,
            providerId = active.id.value,
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
        nativeToken = nativeToken,
        tlsSettings = tlsSettings,
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    )
