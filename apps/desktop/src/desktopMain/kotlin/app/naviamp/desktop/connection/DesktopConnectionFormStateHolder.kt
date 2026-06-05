package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.provider.navidrome.NavidromeConnection

class DesktopConnectionFormStateHolder(
    initialConnection: NavidromeConnection?,
) {
    var savedConnectionForLogin by mutableStateOf(initialConnection)
    var serverUrl by mutableStateOf(initialConnection?.baseUrl.orEmpty())
    var connectionName by mutableStateOf(initialConnection?.displayName.orEmpty())
    var username by mutableStateOf(initialConnection?.username.orEmpty())
    var password by mutableStateOf("")
    var insecureSkipTlsVerification by mutableStateOf(
        initialConnection?.tlsSettings?.insecureSkipTlsVerification ?: false,
    )
    var customCertificatePath by mutableStateOf(initialConnection?.tlsSettings?.customCertificatePath.orEmpty())
    var clientCertificateKeyStorePath by mutableStateOf(
        initialConnection?.tlsSettings?.clientCertificateKeyStorePath.orEmpty(),
    )
    var clientCertificateKeyStorePassword by mutableStateOf(
        initialConnection?.tlsSettings?.clientCertificateKeyStorePassword.orEmpty(),
    )
    var isOpen by mutableStateOf(false)

    fun apply(formState: DesktopConnectionFormState) {
        savedConnectionForLogin = formState.savedConnectionForLogin
        serverUrl = formState.serverUrl
        connectionName = formState.connectionName
        username = formState.username
        password = formState.password
        insecureSkipTlsVerification = formState.insecureSkipTlsVerification
        customCertificatePath = formState.customCertificatePath
        clientCertificateKeyStorePath = formState.clientCertificateKeyStorePath
        clientCertificateKeyStorePassword = formState.clientCertificateKeyStorePassword
    }

    fun clearPassword() {
        password = ""
    }

    fun updateServerUrl(value: String) {
        serverUrl = value
        if (savedConnectionForLogin?.baseUrl != value) {
            savedConnectionForLogin = null
        }
    }

    fun updateUsername(value: String) {
        username = value
        if (savedConnectionForLogin?.username != value) {
            savedConnectionForLogin = null
        }
    }
}
