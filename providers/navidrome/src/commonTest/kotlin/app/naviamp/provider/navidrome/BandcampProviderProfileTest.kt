package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ProviderIdBandcamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BandcampProviderProfileTest {
    @Test
    fun profileOwnsBandcampBetaCompatibilityPolicy() {
        val profile = subsonicProviderProfile(ProviderIdBandcamp)

        assertEquals("Bandcamp", profile.displayName)
        assertEquals("1.16.1", profile.apiVersion)
        assertEquals(SubsonicTokenSaltFormat.AlphaNumeric12, profile.tokenSaltFormat)
        assertTrue(profile.trimGeneratedCredentialWhitespace)
        assertTrue(profile.requiresMusicFolderSelection)
        assertTrue(profile.serialPlaylistTrackMutations)
        assertFalse(profile.nativeAuthentication)
        assertFalse(profile.nativeSmartPlaylists)
        assertFalse(profile.streamingTranscode)
        assertFalse(profile.downloadTranscode)
        assertFalse(profile.generatedRadio)
        assertFalse(profile.favorites)
        assertFalse(profile.ratings)
        assertFalse(profile.playReporting)
    }

    @Test
    fun passwordUsesBandcampTokenSaltPolicy() {
        val connection = NavidromeConnection.fromPassword(
            providerId = ProviderIdBandcamp,
            baseUrl = "https://bandcamp.com/api/subsonic",
            username = "  fan  ",
            password = "  generated-password  ",
        )

        assertEquals("fan", connection.username)
        assertEquals(12, connection.salt.length)
        assertTrue(connection.salt.all { it.isLetterOrDigit() })
        assertEquals(
            navidromeMd5("generated-password${connection.salt}"),
            connection.token,
        )
    }
}
