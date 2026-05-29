package app.naviamp.desktop

import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.resolvedConnectionDisplayName
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.toNavidromeConnection

data class DesktopConnectionFormState(
    val savedConnectionForLogin: NavidromeConnection? = null,
    val serverUrl: String = "",
    val connectionName: String = "",
    val username: String = "",
    val password: String = "",
    val insecureSkipTlsVerification: Boolean = false,
    val customCertificatePath: String = "",
    val clientCertificateKeyStorePath: String = "",
    val clientCertificateKeyStorePassword: String = "",
)

fun desktopConnectionDisplayName(
    connectionName: String,
    serverUrl: String,
): String =
    resolvedConnectionDisplayName(
        displayName = connectionName,
        fallbackBaseUrl = serverUrl,
    )

fun newDesktopConnectionFormState(): DesktopConnectionFormState =
    DesktopConnectionFormState()

fun savedDesktopConnectionFormState(source: SavedMediaSource): DesktopConnectionFormState {
    val connection = source.toNavidromeConnection()
    return DesktopConnectionFormState(
        savedConnectionForLogin = connection,
        serverUrl = connection.baseUrl,
        connectionName = connection.displayName
            ?.takeUnless { it == connection.normalizedBaseUrl }
            .orEmpty(),
        username = connection.username,
        password = "",
        insecureSkipTlsVerification = connection.tlsSettings.insecureSkipTlsVerification,
        customCertificatePath = connection.tlsSettings.customCertificatePath.orEmpty(),
        clientCertificateKeyStorePath = connection.tlsSettings.clientCertificateKeyStorePath.orEmpty(),
        clientCertificateKeyStorePassword = connection.tlsSettings.clientCertificateKeyStorePassword.orEmpty(),
    )
}
