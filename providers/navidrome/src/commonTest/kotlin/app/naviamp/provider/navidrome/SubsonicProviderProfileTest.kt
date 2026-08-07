package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ProviderIdJellyfin
import app.naviamp.domain.provider.ProviderIdNavidrome
import app.naviamp.domain.provider.ProviderIdSubsonic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubsonicProviderProfileTest {
    @Test
    fun navidromeProfileOwnsOnlyNavidromeNativeFeatures() {
        val profile = subsonicProviderProfile(ProviderIdNavidrome)

        assertEquals("Navidrome", profile.displayName)
        assertEquals("1.16.1", profile.apiVersion)
        assertEquals(SubsonicTokenSaltFormat.Hex32, profile.tokenSaltFormat)
        assertFalse(profile.trimGeneratedCredentialWhitespace)
        assertTrue(profile.nativeAuthentication)
        assertTrue(profile.nativeSmartPlaylists)
        assertTrue(profile.canonicalIdMigration)
    }

    @Test
    fun genericProfileUsesOnlyThePortableSubsonicSurface() {
        val profile = subsonicProviderProfile(ProviderIdSubsonic)

        assertEquals("Subsonic", profile.displayName)
        assertEquals("1.16.1", profile.apiVersion)
        assertEquals(SubsonicTokenSaltFormat.Hex32, profile.tokenSaltFormat)
        assertFalse(profile.trimGeneratedCredentialWhitespace)
        assertFalse(profile.nativeAuthentication)
        assertFalse(profile.nativeSmartPlaylists)
        assertFalse(profile.canonicalIdMigration)
    }

    @Test
    fun nonSubsonicProviderCannotEnterTheSubsonicEngine() {
        val failure = assertFailsWith<IllegalArgumentException> {
            subsonicProviderProfile(ProviderIdJellyfin)
        }

        assertEquals("Jellyfin is not a Subsonic provider.", failure.message)
    }
}
