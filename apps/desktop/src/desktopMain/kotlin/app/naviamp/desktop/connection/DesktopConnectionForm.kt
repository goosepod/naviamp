package app.naviamp.desktop

import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.source.resolvedConnectionDisplayName
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeTlsSettings
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

data class DesktopDeletedConnectionUpdate(
    val clearConnectedSource: Boolean,
    val clearSavedConnectionForLogin: Boolean,
    val status: String,
)

fun desktopConnectionFormError(
    serverUrl: String,
    username: String,
    password: String,
    savedConnectionForLogin: NavidromeConnection?,
): String? =
    when {
        serverUrl.isBlank() || username.isBlank() -> "Enter a server URL and username."
        password.isBlank() && savedConnectionForLogin == null -> "Enter a password for first-time setup."
        else -> null
    }

fun desktopConnectionTlsSettings(
    insecureSkipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificateKeyStorePath: String,
    clientCertificateKeyStorePassword: String,
): NavidromeTlsSettings =
    NavidromeTlsSettings(
        insecureSkipTlsVerification = insecureSkipTlsVerification,
        customCertificatePath = customCertificatePath.trim().takeIf {
            !insecureSkipTlsVerification && it.isNotEmpty()
        },
        clientCertificateKeyStorePath = clientCertificateKeyStorePath.trim().takeIf { it.isNotEmpty() },
        clientCertificateKeyStorePassword = clientCertificateKeyStorePassword
            .takeIf { clientCertificateKeyStorePath.trim().isNotEmpty() },
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

fun desktopDeletedConnectionUpdate(
    source: SavedMediaSource,
    connectedSourceId: String?,
    savedConnectionForLogin: NavidromeConnection?,
): DesktopDeletedConnectionUpdate =
    DesktopDeletedConnectionUpdate(
        clearConnectedSource = connectedSourceId == source.id,
        clearSavedConnectionForLogin = savedConnectionForLogin?.baseUrl == source.baseUrl &&
            savedConnectionForLogin.username == source.username,
        status = "Deleted ${source.displayName}.",
    )

fun desktopConnectionSuccessStatus(
    validation: ConnectionValidation,
    connection: NavidromeConnection,
    password: String,
    smartPlaylistAuthWarning: String?,
): String =
    buildString {
        append("Connected")
        validation.serverVersion?.let { append(" to Navidrome $it") }
        append(".")
        when {
            connection.nativeToken?.isNotBlank() == true -> append(" Smart playlist saves are enabled.")
            smartPlaylistAuthWarning != null -> {
                append(" Smart playlist saves are not enabled: ")
                append(smartPlaylistAuthWarning)
            }
            password.isBlank() -> append(" Smart playlist saves require editing this connection and entering your password.")
        }
    }
