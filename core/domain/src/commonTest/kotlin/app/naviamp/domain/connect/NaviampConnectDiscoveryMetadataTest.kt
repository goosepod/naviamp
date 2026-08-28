package app.naviamp.domain.connect

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaviampConnectDiscoveryMetadataTest {
    @Test
    fun metadataRoundTripPreservesOnlyAdvertisedIdentityAndCapabilities() {
        val advertisement = NaviampConnectAdvertisement(
            instanceId = "random-instance",
            displayName = "Living Room",
            protocolRange = NaviampConnectProtocolRange(1, 2),
            capabilities = setOf(
                NaviampConnectCapability.QueueRead,
                NaviampConnectCapability.TransportControls,
            ),
            port = 42_424,
            identityFingerprint = "identity-fingerprint",
            expiresAtEpochMillis = 5_000,
        )

        val attributes = NaviampConnectDiscoveryMetadata.encode(advertisement)
        val decoded = NaviampConnectDiscoveryMetadata.decode(attributes, port = 43_434, expiresAtEpochMillis = 9_000)

        assertEquals(advertisement.copy(port = 43_434, expiresAtEpochMillis = 9_000), decoded)
        assertEquals(setOf("id", "name", "pmin", "pmax", "caps", "fp"), attributes.keys)
    }

    @Test
    fun unknownFutureCapabilitiesAreIgnored() {
        val attributes = validAttributes().toMutableMap().apply {
            this["caps"] = "transport,future_capability"
        }

        val decoded = NaviampConnectDiscoveryMetadata.decode(attributes, port = 42_424, expiresAtEpochMillis = 5_000)

        assertEquals(setOf(NaviampConnectCapability.TransportControls), decoded?.capabilities)
    }

    @Test
    fun malformedRequiredMetadataIsRejected() {
        assertNull(
            NaviampConnectDiscoveryMetadata.decode(
                validAttributes() - "fp",
                port = 42_424,
                expiresAtEpochMillis = 5_000,
            ),
        )
    }

    private fun validAttributes() = mapOf(
        "id" to "target",
        "name" to "Living Room",
        "pmin" to "1",
        "pmax" to "1",
        "caps" to "transport",
        "fp" to "fingerprint",
    )
}
