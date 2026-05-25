package app.naviamp.provider.navidrome

import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.normalizedBaseUrl
import app.naviamp.domain.source.resolvedConnectionDisplayName
import kotlin.random.Random

typealias NavidromeTlsSettings = ConnectionTlsSettings

data class NavidromeConnection(
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
    val nativeToken: String? = null,
    val displayName: String? = null,
    val tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings(),
) {
    val normalizedBaseUrl: String =
        normalizedBaseUrl(baseUrl)

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
                nativeToken = null,
                displayName = displayName,
                tlsSettings = tlsSettings,
            )

        private fun randomSalt(): String =
            Random.Default.nextBytes(16).joinToString(separator = "") { byte ->
                byte.toUByte().toString(radix = 16).padStart(2, '0')
            }

    }
}

fun NavidromeConnection.resolvedDisplayName(): String =
    resolvedConnectionDisplayName(displayName, normalizedBaseUrl)

fun SavedMediaSource.toNavidromeConnection(): NavidromeConnection =
    NavidromeConnection(
        baseUrl = baseUrl,
        username = username,
        token = token,
        salt = salt,
        nativeToken = nativeToken,
        displayName = displayName,
        tlsSettings = tlsSettings,
    )
