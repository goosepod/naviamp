package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ProviderIdNavidrome
import app.naviamp.domain.provider.ProviderIdSubsonic
import app.naviamp.domain.provider.ProviderIdBandcamp
import app.naviamp.domain.provider.ProviderProtocolFamily
import app.naviamp.domain.provider.providerDescriptor

enum class SubsonicTokenSaltFormat {
    Hex32,
    AlphaNumeric12,
}

/** Protocol-engine policy that keeps branded provider behavior out of standard Subsonic sessions. */
data class SubsonicProviderProfile(
    val providerId: String,
    val displayName: String,
    val apiVersion: String,
    val tokenSaltFormat: SubsonicTokenSaltFormat,
    val trimGeneratedCredentialWhitespace: Boolean,
    val requiresMusicFolderSelection: Boolean,
    val serialPlaylistTrackMutations: Boolean,
    val nativeAuthentication: Boolean,
    val nativeSmartPlaylists: Boolean,
    val canonicalIdMigration: Boolean,
    val streamingTranscode: Boolean,
    val downloadTranscode: Boolean,
    val generatedRadio: Boolean,
    val favorites: Boolean,
    val ratings: Boolean,
    val playReporting: Boolean,
)

fun subsonicProviderProfile(providerId: String): SubsonicProviderProfile {
    val descriptor = providerDescriptor(providerId)
    require(descriptor.protocolFamily == ProviderProtocolFamily.Subsonic) {
        "${descriptor.displayName} is not a Subsonic provider."
    }
    return when (descriptor.id) {
        ProviderIdNavidrome -> navidromeProviderProfile(descriptor.displayName)
        ProviderIdSubsonic -> genericSubsonicProviderProfile(descriptor.displayName)
        ProviderIdBandcamp -> bandcampProviderProfile(descriptor.displayName)
        else -> SubsonicProviderProfile(
            providerId = descriptor.id,
            displayName = descriptor.displayName,
            apiVersion = "1.16.1",
            tokenSaltFormat = SubsonicTokenSaltFormat.Hex32,
            trimGeneratedCredentialWhitespace = false,
            requiresMusicFolderSelection = false,
            serialPlaylistTrackMutations = false,
            nativeAuthentication = false,
            nativeSmartPlaylists = false,
            canonicalIdMigration = false,
            streamingTranscode = false,
            downloadTranscode = false,
            generatedRadio = false,
            favorites = false,
            ratings = false,
            playReporting = false,
        )
    }
}

private fun navidromeProviderProfile(displayName: String): SubsonicProviderProfile =
    SubsonicProviderProfile(
        providerId = ProviderIdNavidrome,
        displayName = displayName,
        apiVersion = "1.16.1",
        tokenSaltFormat = SubsonicTokenSaltFormat.Hex32,
        trimGeneratedCredentialWhitespace = false,
        requiresMusicFolderSelection = false,
        serialPlaylistTrackMutations = false,
        nativeAuthentication = true,
        nativeSmartPlaylists = true,
        canonicalIdMigration = true,
        streamingTranscode = true,
        downloadTranscode = true,
        generatedRadio = true,
        favorites = true,
        ratings = true,
        playReporting = true,
    )

private fun genericSubsonicProviderProfile(displayName: String): SubsonicProviderProfile =
    SubsonicProviderProfile(
        providerId = ProviderIdSubsonic,
        displayName = displayName,
        apiVersion = "1.16.1",
        tokenSaltFormat = SubsonicTokenSaltFormat.Hex32,
        trimGeneratedCredentialWhitespace = false,
        requiresMusicFolderSelection = false,
        serialPlaylistTrackMutations = false,
        nativeAuthentication = false,
        nativeSmartPlaylists = false,
        canonicalIdMigration = false,
        streamingTranscode = true,
        downloadTranscode = true,
        generatedRadio = true,
        favorites = true,
        ratings = true,
        playReporting = true,
    )
