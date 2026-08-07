package app.naviamp.domain.settings

import app.naviamp.domain.provider.providerDescriptor

fun connectionFormError(
    serverUrl: String,
    username: String,
    password: String,
    hasSavedConnectionForLogin: Boolean,
): String? =
    when {
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
    )
}
