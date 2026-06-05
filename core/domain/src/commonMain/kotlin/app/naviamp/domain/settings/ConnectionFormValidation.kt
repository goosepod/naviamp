package app.naviamp.domain.settings

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
): String? =
    connectionFormError(
        serverUrl = form.serverUrl,
        username = form.username,
        password = form.password,
        hasSavedConnectionForLogin = hasSavedConnectionForLogin,
    )
