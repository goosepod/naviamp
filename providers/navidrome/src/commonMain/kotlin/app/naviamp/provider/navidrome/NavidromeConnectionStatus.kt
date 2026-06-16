package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ConnectionValidation

fun navidromeConnectionSuccessStatus(
    validation: ConnectionValidation,
): String =
    buildString {
        append("Connected")
        validation.serverVersion?.let { append(" to Navidrome $it") }
        append(".")
    }
