package app.naviamp.domain.provider

import app.naviamp.domain.AudioCodec
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.connectionFormError
import app.naviamp.domain.settings.selectProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProviderCatalogTest {
    @Test
    fun catalogSeparatesProviderIdentityFromSharedProtocolFamily() {
        val navidrome = providerDescriptor(ProviderIdNavidrome)
        val subsonic = providerDescriptor(ProviderIdSubsonic)
        val jellyfin = providerDescriptor(ProviderIdJellyfin)
        val bandcamp = providerDescriptor(ProviderIdBandcamp)

        assertEquals(ProviderProtocolFamily.Subsonic, navidrome.protocolFamily)
        assertEquals(ProviderProtocolFamily.Subsonic, subsonic.protocolFamily)
        assertEquals(ProviderProtocolFamily.Jellyfin, jellyfin.protocolFamily)
        assertEquals(ProviderProtocolFamily.Subsonic, bandcamp.protocolFamily)
        assertEquals(BandcampSubsonicServerUrl, bandcamp.fixedServerUrl)
    }

    @Test
    fun connectionFormDefaultsLegacyConnectionsToNavidrome() {
        val form = ConnectionFormState()

        assertEquals(ProviderIdNavidrome, form.providerId)
        assertTrue(providerDescriptor(form.providerId).selectable)
    }

    @Test
    fun genericSubsonicSelectionClearsProviderSpecificSessionInputs() {
        val original = ConnectionFormState(
            serverUrl = "https://music.example",
            username = "demo",
            password = "secret",
            selectedMusicFolderIds = listOf("music"),
        )

        val result = original.selectProvider(ProviderIdSubsonic)

        assertEquals(ProviderIdSubsonic, result.providerId)
        assertEquals("https://music.example", result.serverUrl)
        assertEquals("demo", result.username)
        assertEquals("", result.password)
        assertTrue(result.selectedMusicFolderIds.isEmpty())
        assertTrue(providerDescriptor(result.providerId).selectable)
    }

    @Test
    fun bandcampUsesItsFixedBetaEndpointAndAcceptsGeneratedCredentials() {
        val form = ConnectionFormState(
            providerId = ProviderIdNavidrome,
            serverUrl = "https://previous.example",
            username = "demo",
            password = "secret",
        ).selectProvider(ProviderIdBandcamp)

        assertEquals(ProviderIdBandcamp, form.providerId)
        assertEquals(BandcampSubsonicServerUrl, form.serverUrl)
        assertTrue(providerDescriptor(form.providerId).selectable)

        val edited = form.copy(
            serverUrl = "https://bandcamp-proxy.example/subsonic",
            password = "generated-secret",
        )
        assertEquals("https://bandcamp-proxy.example/subsonic", edited.serverUrl)
        assertEquals(null, connectionFormError(edited, hasSavedConnectionForLogin = false))
    }

    @Test
    fun unsupportedProviderTranscodingFallsBackToOriginalQuality() {
        val capabilities = ProviderCapabilities(
            supportsStreamingTranscode = false,
            supportsDownloadTranscode = false,
            supportsArtistRadio = false,
            supportsAlbumRadio = false,
            supportsTrackRadio = false,
        )
        val requested = StreamQuality.Transcoded(AudioCodec.Opus, 128)

        assertEquals(StreamQuality.Original, capabilities.effectiveStreamingQuality(requested))
        assertEquals(StreamQuality.Original, capabilities.effectiveDownloadQuality(requested))
    }

    @Test
    fun unknownPersistedProviderDoesNotFallBackToNavidrome() {
        val provider = providerDescriptor("future-provider")
        val form = ConnectionFormState(
            providerId = provider.id,
            serverUrl = "https://future.example",
            username = "demo",
            password = "secret",
        )

        assertEquals(ProviderProtocolFamily.Unknown, provider.protocolFamily)
        assertFalse(provider.selectable)
        assertEquals(
            "future-provider support is not available yet.",
            connectionFormError(form, hasSavedConnectionForLogin = false),
        )
    }
}
