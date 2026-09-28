package app.naviamp.domain.settings

import app.naviamp.domain.provider.providerDescriptor
import app.naviamp.domain.source.SubsonicAuthApiKey

fun connectionFormError(
    serverUrl: String,
    username: String,
    password: String,
    hasSavedConnectionForLogin: Boolean,
    authenticationMode: String = app.naviamp.domain.source.SubsonicAuthToken,
    apiKey: String = "",
): String? =
    when {
        authenticationMode == SubsonicAuthApiKey && serverUrl.isBlank() -> "connection_server_url_required"
        authenticationMode == SubsonicAuthApiKey && apiKey.isBlank() && !hasSavedConnectionForLogin -> "connection_api_key_required"
        authenticationMode == SubsonicAuthApiKey -> null
        serverUrl.isBlank() || username.isBlank() -> "Enter a server URL and username."
        password.isBlank() && !hasSavedConnectionForLogin -> "Enter a password for first-time setup."
        else -> null
    }

fun connectionFormError(
    form: ConnectionFormState,
    hasSavedConnectionForLogin: Boolean,
): String? {
    val provider = providerDescriptor(form.providerId)
    if (!provider.selectable) return "${provider.displayName} support is not available yet."
    return connectionFormError(
        serverUrl = form.serverUrl,
        username = form.username,
        password = form.password,
        hasSavedConnectionForLogin = hasSavedConnectionForLogin,
        authenticationMode = form.authenticationMode,
        apiKey = form.apiKey,
    )
}
