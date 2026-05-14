package app.naviamp.android

import android.content.Context
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.provider.navidrome.NavidromeConnection

class AndroidSettingsStore(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun loadConnection(savedConnection: NavidromeConnection? = null): ConnectionFormState =
        ConnectionFormState(
            serverUrl = savedConnection?.baseUrl ?: preferences.getString(KeyServerUrl, "").orEmpty(),
            username = savedConnection?.username ?: preferences.getString(KeyUsername, "").orEmpty(),
            password = preferences.getString(KeyPassword, "").orEmpty(),
            skipTlsVerification = savedConnection?.tlsSettings?.insecureSkipTlsVerification
                ?: preferences.getBoolean(KeySkipTlsVerification, false),
            customCertificatePath = savedConnection?.tlsSettings?.customCertificatePath
                ?: preferences.getString(KeyCustomCertificatePath, "").orEmpty(),
            clientCertificatePath = savedConnection?.tlsSettings?.clientCertificateKeyStorePath
                ?: preferences.getString(KeyClientCertificatePath, "").orEmpty(),
            clientCertificatePassword = savedConnection?.tlsSettings?.clientCertificateKeyStorePassword
                ?: preferences.getString(KeyClientCertificatePassword, "").orEmpty(),
        )

    fun saveConnection(connection: ConnectionFormState) {
        preferences.edit()
            .putString(KeyServerUrl, connection.serverUrl.trim())
            .putString(KeyUsername, connection.username)
            .putString(KeyPassword, connection.password)
            .putBoolean(KeySkipTlsVerification, connection.skipTlsVerification)
            .putString(KeyCustomCertificatePath, connection.customCertificatePath.trim())
            .putString(KeyClientCertificatePath, connection.clientCertificatePath.trim())
            .putString(KeyClientCertificatePassword, connection.clientCertificatePassword)
            .apply()
    }
}

private const val PreferencesName = "naviamp_android_settings"
private const val KeyServerUrl = "server_url"
private const val KeyUsername = "username"
private const val KeyPassword = "password"
private const val KeySkipTlsVerification = "skip_tls_verification"
private const val KeyCustomCertificatePath = "custom_certificate_path"
private const val KeyClientCertificatePath = "client_certificate_path"
private const val KeyClientCertificatePassword = "client_certificate_password"
