package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ConnectionValidation

fun navidromeConnectionSuccessStatus(
    validation: ConnectionValidation,
    activeUrl: String? = null,
    primaryUrl: String? = null,
): String =
    buildString {
        append("Connected")
        validation.serverVersion?.let { append(" to Navidrome $it") }
        val normalizedPrimary = primaryUrl?.trim()?.trimEnd('/')
        val normalizedActive = activeUrl?.trim()?.trimEnd('/')
        if (!normalizedActive.isNullOrBlank() && normalizedActive != normalizedPrimary) {
            append(" via $normalizedActive")
        }
        append(".")
    }
