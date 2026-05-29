package app.naviamp.provider.navidrome

fun navidromeTlsSettingsFromForm(
    insecureSkipTlsVerification: Boolean,
    customCertificatePath: String,
    clientCertificateKeyStorePath: String,
    clientCertificateKeyStorePassword: String,
): NavidromeTlsSettings {
    val normalizedCustomCertificatePath = customCertificatePath.trim()
    val normalizedClientCertificateKeyStorePath = clientCertificateKeyStorePath.trim()
    return NavidromeTlsSettings(
        insecureSkipTlsVerification = insecureSkipTlsVerification,
        customCertificatePath = normalizedCustomCertificatePath.takeIf {
            !insecureSkipTlsVerification && it.isNotEmpty()
        },
        clientCertificateKeyStorePath = normalizedClientCertificateKeyStorePath.takeIf { it.isNotEmpty() },
        clientCertificateKeyStorePassword = clientCertificateKeyStorePassword
            .takeIf { normalizedClientCertificateKeyStorePath.isNotEmpty() },
    )
}
