package app.naviamp.provider.navidrome

import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.normalizedMusicFolderIds
import app.naviamp.domain.provider.ProviderIdNavidrome
import app.naviamp.domain.provider.providerDescriptor

data class NavidromeConnectionLoginRequest(
    val providerId: String = ProviderIdNavidrome,
    val baseUrl: String,
    val secondaryUrls: List<ConnectionSecondaryUrl> = emptyList(),
    val username: String,
    val password: String,
    val displayName: String?,
    val tlsSettings: NavidromeTlsSettings,
    val customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
    val selectedMusicFolderIds: List<String> = emptyList(),
    val savedSourceId: String? = null,
    val savedConnectionForLogin: NavidromeConnection?,
    val nativeAuthEnabled: Boolean = true,
    val nativeAuthRequired: Boolean = false,
)

data class PreparedNavidromeConnection(
    val connection: NavidromeConnection,
    val nativeAuthErrorMessage: String?,
)

suspend fun prepareNavidromeConnection(
    request: NavidromeConnectionLoginRequest,
    validateConnection: suspend (NavidromeConnection) -> Unit = { connection ->
        NavidromeProvider(connection).validateConnection()
    },
    musicFolders: suspend (NavidromeConnection) -> List<NavidromeMusicFolder> = { connection ->
        NavidromeProvider(connection).musicFolders()
    },
    nativeTokenFromPassword: suspend (NavidromeConnection, String, Boolean) -> NavidromeConnection = { connection, password, required ->
        connection.withNativeTokenFromPassword(password, required = required)
    },
): PreparedNavidromeConnection {
    val normalizedSecondaryUrls = request.secondaryUrls
        .mapNotNull { it.normalized() }
        .filterNot { it.url == request.baseUrl.trim().trimEnd('/') }
        .distinctBy { it.url }
    val customHeaders = request.customHeaders.mapNotNull { it.normalized() }
    val selectedMusicFolderIds = normalizedMusicFolderIds(request.selectedMusicFolderIds)
    val reusableCredentials = request.savedConnectionForLogin?.takeIf {
        it.baseUrl == request.baseUrl &&
            it.username == request.username &&
            request.password.isBlank()
    }
    val connectionWithoutNativeRefresh = reusableCredentials?.copy(
        providerId = request.providerId,
        nativeToken = reusableCredentials.nativeToken.takeIf {
            subsonicProviderProfile(request.providerId).nativeAuthentication
        },
        displayName = request.displayName,
        tlsSettings = request.tlsSettings,
        secondaryUrls = normalizedSecondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    ) ?: NavidromeConnection.fromPassword(
        providerId = request.providerId,
        baseUrl = request.baseUrl,
        username = request.username,
        password = request.password,
        displayName = request.displayName,
        tlsSettings = request.tlsSettings,
        secondaryUrls = normalizedSecondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    )
    var nativeAuthErrorMessage: String? = null
    val connection = if (request.password.isNotBlank() && request.nativeAuthEnabled) {
        runCatching {
            nativeTokenFromPassword(
                connectionWithoutNativeRefresh,
                request.password,
                request.nativeAuthRequired,
            )
        }.getOrElse { nativeAuthError ->
            nativeAuthErrorMessage = nativeAuthError.message
                ?: "Could not authenticate with Navidrome's native API."
            connectionWithoutNativeRefresh
        }
    } else {
        connectionWithoutNativeRefresh
    }
    val activeConnection = connection.withReachableBaseUrl(validateConnection)
        .withBackfilledDefaultMusicFolder(musicFolders)
    return PreparedNavidromeConnection(
        connection = activeConnection,
        nativeAuthErrorMessage = nativeAuthErrorMessage,
    )
}

suspend fun NavidromeConnection.withBackfilledDefaultMusicFolder(
    musicFolders: suspend (NavidromeConnection) -> List<NavidromeMusicFolder> = { connection ->
        NavidromeProvider(connection).musicFolders()
    },
): NavidromeConnection {
    if (normalizedMusicFolderIds(selectedMusicFolderIds).isNotEmpty()) {
        return this
    }
    val profile = subsonicProviderProfile(providerId)
    return try {
        val defaultMusicFolderId = musicFolders(this).firstOrNull()?.id
        if (defaultMusicFolderId == null) {
            if (profile.requiresMusicFolderSelection) {
                throw NavidromeException("${profile.displayName} did not return an accessible music collection.")
            }
            return this
        }
        val selectedIds = normalizedMusicFolderIds(listOf(defaultMusicFolderId))
        if (selectedIds.isEmpty()) this else copy(selectedMusicFolderIds = selectedIds)
    } catch (cause: Throwable) {
        if (profile.requiresMusicFolderSelection) {
            throw NavidromeException(
                "Could not access the ${profile.displayName} music collection. Check the generated credentials and try again.",
            )
        }
        this
    }
}

private suspend fun NavidromeConnection.withReachableBaseUrl(
    validateConnection: suspend (NavidromeConnection) -> Unit,
): NavidromeConnection {
    val candidates = (listOf(baseUrl) + secondaryUrls.sortedBy { it.priority }.map { it.url })
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotBlank() }
        .distinct()
    var lastFailure: Throwable? = null
    candidates.forEach { candidate ->
        val candidateConnection = copy(baseUrl = candidate)
        runCatching {
            validateConnection(candidateConnection)
        }.onSuccess {
            return candidateConnection
        }.onFailure { error ->
            lastFailure = error
        }
    }
    throw lastFailure ?: NavidromeException(
        "Could not connect to ${providerDescriptor(providerId).displayName}.",
    )
}
