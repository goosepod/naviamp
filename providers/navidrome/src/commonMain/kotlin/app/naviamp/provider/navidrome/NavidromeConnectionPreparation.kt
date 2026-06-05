package app.naviamp.provider.navidrome

data class NavidromeConnectionLoginRequest(
    val baseUrl: String,
    val username: String,
    val password: String,
    val displayName: String?,
    val tlsSettings: NavidromeTlsSettings,
    val savedConnectionForLogin: NavidromeConnection?,
    val nativeAuthRequired: Boolean = false,
)

data class PreparedNavidromeConnection(
    val connection: NavidromeConnection,
    val nativeAuthErrorMessage: String?,
)

suspend fun prepareNavidromeConnection(
    request: NavidromeConnectionLoginRequest,
    nativeTokenFromPassword: suspend (NavidromeConnection, String, Boolean) -> NavidromeConnection = { connection, password, required ->
        connection.withNativeTokenFromPassword(password, required = required)
    },
): PreparedNavidromeConnection {
    val reusableCredentials = request.savedConnectionForLogin?.takeIf {
        it.baseUrl == request.baseUrl && it.username == request.username && request.password.isBlank()
    }
    val connectionWithoutNativeRefresh = reusableCredentials?.copy(
        displayName = request.displayName,
        tlsSettings = request.tlsSettings,
    ) ?: NavidromeConnection.fromPassword(
        baseUrl = request.baseUrl,
        username = request.username,
        password = request.password,
        displayName = request.displayName,
        tlsSettings = request.tlsSettings,
    )
    var nativeAuthErrorMessage: String? = null
    val connection = if (request.password.isNotBlank()) {
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
    return PreparedNavidromeConnection(
        connection = connection,
        nativeAuthErrorMessage = nativeAuthErrorMessage,
    )
}
