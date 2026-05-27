package app.naviamp.desktop

import app.naviamp.domain.source.resolvedConnectionDisplayName
import app.naviamp.provider.navidrome.NavidromeTlsSettings

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
