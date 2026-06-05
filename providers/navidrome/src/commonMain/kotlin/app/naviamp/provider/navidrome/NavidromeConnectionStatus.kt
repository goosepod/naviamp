package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ConnectionValidation

fun navidromeConnectionSuccessStatus(
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
