package app.naviamp.provider.navidrome

import kotlin.random.Random

data class NavidromeConnection(
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
    val displayName: String? = null,
    val tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings(),
) {
    val normalizedBaseUrl: String =
        baseUrl.trim().trimEnd('/')

    companion object {
        fun fromPassword(
            baseUrl: String,
            username: String,
            password: String,
            salt: String = randomSalt(),
            displayName: String? = null,
            tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings(),
        ): NavidromeConnection =
            NavidromeConnection(
                baseUrl = baseUrl,
                username = username,
                token = navidromeMd5(password + salt),
                salt = salt,
                displayName = displayName,
                tlsSettings = tlsSettings,
            )

        private fun randomSalt(): String =
            Random.Default.nextBytes(16).joinToString(separator = "") { byte ->
                byte.toUByte().toString(radix = 16).padStart(2, '0')
            }

    }
}

data class NavidromeTlsSettings(
    val insecureSkipTlsVerification: Boolean = false,
    val customCertificatePath: String? = null,
    val clientCertificateKeyStorePath: String? = null,
    val clientCertificateKeyStorePassword: String? = null,
) {
    val hasCustomCertificate: Boolean
        get() = !customCertificatePath.isNullOrBlank()

    val hasClientCertificate: Boolean
        get() = !clientCertificateKeyStorePath.isNullOrBlank()
}
