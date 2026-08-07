package app.naviamp.provider.navidrome

import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.normalizedMusicFolderIds
import app.naviamp.domain.source.normalizedBaseUrl
import app.naviamp.domain.source.resolvedConnectionDisplayName
import app.naviamp.domain.provider.ProviderIdNavidrome
import kotlin.random.Random

typealias NavidromeTlsSettings = ConnectionTlsSettings

data class NavidromeConnection(
    val providerId: String = ProviderIdNavidrome,
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
    val nativeToken: String? = null,
    val displayName: String? = null,
    val tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings(),
    val secondaryUrls: List<ConnectionSecondaryUrl> = emptyList(),
    val customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
    val selectedMusicFolderIds: List<String> = emptyList(),
) {
    val normalizedBaseUrl: String =
        normalizedBaseUrl(baseUrl)

    companion object {
        fun fromPassword(
            providerId: String = ProviderIdNavidrome,
            baseUrl: String,
            username: String,
            password: String,
            salt: String = randomSalt(providerId),
            displayName: String? = null,
            tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings(),
            secondaryUrls: List<ConnectionSecondaryUrl> = emptyList(),
            customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
            selectedMusicFolderIds: List<String> = emptyList(),
        ): NavidromeConnection =
            subsonicProviderProfile(providerId).let { profile ->
                val normalizedUsername = if (profile.trimGeneratedCredentialWhitespace) username.trim() else username
                val normalizedPassword = if (profile.trimGeneratedCredentialWhitespace) password.trim() else password
                NavidromeConnection(
                    providerId = providerId,
                    baseUrl = baseUrl,
                    username = normalizedUsername,
                    token = navidromeMd5(normalizedPassword + salt),
                    salt = salt,
                    nativeToken = null,
                    displayName = displayName,
                    tlsSettings = tlsSettings,
                    secondaryUrls = secondaryUrls,
                    customHeaders = customHeaders,
                    selectedMusicFolderIds = selectedMusicFolderIds,
                )
            }

        private fun randomSalt(providerId: String): String {
            val profile = subsonicProviderProfile(providerId)
            return when (profile.tokenSaltFormat) {
                SubsonicTokenSaltFormat.AlphaNumeric12 -> {
                    val characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                    buildString {
                        repeat(12) {
                            append(characters[Random.Default.nextInt(characters.length)])
                        }
                    }
                }
                SubsonicTokenSaltFormat.Hex32 ->
                    Random.Default.nextBytes(16).joinToString(separator = "") { byte ->
                        byte.toUByte().toString(radix = 16).padStart(2, '0')
                    }
            }
        }

    }
}

fun NavidromeConnection.resolvedDisplayName(): String =
    resolvedConnectionDisplayName(displayName, normalizedBaseUrl)

fun SavedMediaSource.toNavidromeConnection(): NavidromeConnection =
    NavidromeConnection(
        providerId = providerId,
        baseUrl = baseUrl,
        username = username,
        token = token,
        salt = salt,
        nativeToken = nativeToken,
        displayName = displayName,
        tlsSettings = tlsSettings,
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = normalizedMusicFolderIds(selectedMusicFolderIds),
    )
