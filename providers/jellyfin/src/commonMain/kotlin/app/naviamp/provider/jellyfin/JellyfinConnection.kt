package app.naviamp.provider.jellyfin

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.provider.ProviderIdJellyfin
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.normalizedBaseUrl
import app.naviamp.domain.source.resolvedConnectionDisplayName

data class JellyfinConnection(
    val baseUrl: String,
    val username: String,
    val accessToken: String,
    val userId: String,
    val deviceId: String,
    val serverId: String? = null,
    val serverVersion: String? = null,
    val displayName: String? = null,
    val tlsSettings: ConnectionTlsSettings = ConnectionTlsSettings(),
    val secondaryUrls: List<ConnectionSecondaryUrl> = emptyList(),
    val customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
    val selectedMusicFolderIds: List<String> = emptyList(),
) {
    val normalizedBaseUrl: String = normalizedBaseUrl(baseUrl)

    init {
        require(normalizedBaseUrl.isNotEmpty()) { "Enter a Jellyfin server URL." }
        require(username.isNotBlank()) { "Enter a Jellyfin username." }
        require(accessToken.isNotBlank()) { "Jellyfin did not return an access token." }
        require(userId.isNotBlank()) { "Jellyfin did not return a user identifier." }
        require(deviceId.isNotBlank()) { "A stable device identifier is required for Jellyfin." }
    }
}

fun JellyfinConnection.resolvedDisplayName(): String =
    resolvedConnectionDisplayName(displayName, normalizedBaseUrl)

/**
 * The existing provider credential record has one opaque native-token slot. Jellyfin stores its
 * access token there; the generic token and salt fields remain empty because they are Subsonic
 * authentication material and must not be overloaded with Jellyfin identifiers.
 */
fun JellyfinConnection.toProviderMediaSourceConnection(): ProviderMediaSourceConnection =
    ProviderMediaSourceConnection(
        displayName = resolvedDisplayName(),
        baseUrl = normalizedBaseUrl,
        username = username.trim(),
        token = "",
        salt = "",
        nativeToken = accessToken,
        tlsSettings = tlsSettings,
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    )

internal fun SavedMediaSource.toJellyfinConnection(
    deviceId: String,
    userId: String,
    resolvedUsername: String = username,
): JellyfinConnection {
    require(providerId == ProviderIdJellyfin) { "Saved connection is not a Jellyfin connection." }
    return JellyfinConnection(
        baseUrl = baseUrl,
        username = resolvedUsername,
        accessToken = nativeToken.orEmpty(),
        userId = userId,
        deviceId = deviceId,
        displayName = displayName,
        tlsSettings = tlsSettings,
        secondaryUrls = secondaryUrls,
        customHeaders = customHeaders,
        selectedMusicFolderIds = selectedMusicFolderIds,
    )
}
