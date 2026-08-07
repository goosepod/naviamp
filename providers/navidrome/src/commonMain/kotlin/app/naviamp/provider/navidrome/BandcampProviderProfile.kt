package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ProviderIdBandcamp

/** Bandcamp beta compatibility policy layered on the shared Subsonic protocol engine. */
internal fun bandcampProviderProfile(displayName: String): SubsonicProviderProfile =
    SubsonicProviderProfile(
        providerId = ProviderIdBandcamp,
        displayName = displayName,
        apiVersion = "1.16.1",
        tokenSaltFormat = SubsonicTokenSaltFormat.AlphaNumeric12,
        trimGeneratedCredentialWhitespace = true,
        requiresMusicFolderSelection = true,
        serialPlaylistTrackMutations = true,
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
