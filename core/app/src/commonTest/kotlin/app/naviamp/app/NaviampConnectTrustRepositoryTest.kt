package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import app.naviamp.domain.connect.NaviampConnectTrustedEndpoint
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampConnectTrustRepositoryTest {
    @Test
    fun persistsReplacesAndRemovesTrustWithCoreOwnedSchema() {
        var stored: String? = null
        val repository = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = stored
            override fun write(value: String) { stored = value }
        })
        val older = trust("trust-a", "phone", 10)
        val newer = trust("trust-b", "desktop", 20)

        repository.upsert(older)
        assertEquals(listOf(newer, older), repository.upsert(newer))
        assertEquals(listOf(newer.copy(displayName = "renamed"), older), repository.upsert(newer.copy(displayName = "renamed")))
        assertEquals(listOf(older), repository.remove("trust-b"))
        assertEquals(listOf(older), NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = stored
            override fun write(value: String) { stored = value }
        }).load())
    }

    @Test
    fun repairingPairingReplacesTheExistingRecordForTheSamePeer() {
        var stored: String? = null
        val repository = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = stored
            override fun write(value: String) { stored = value }
        })
        val original = trust("trust-old", "TV", 10)
        val repaired = trust("trust-new", "TV", 20).copy(peerDevice = original.peerDevice)

        repository.upsert(original)

        assertEquals(listOf(repaired), repository.upsert(repaired))
        assertEquals(listOf(repaired), repository.load())
    }

    @Test
    fun persistsTheLastAuthenticatedEndpointAcrossRepositoryInstances() {
        var stored: String? = null
        val storage = object : NaviampConnectTrustStorageEffect {
            override fun read() = stored
            override fun write(value: String) { stored = value }
        }
        val endpoint = NaviampConnectTrustedEndpoint(
            addresses = listOf("192.0.2.10"),
            advertisement = NaviampConnectAdvertisement(
                instanceId = "tv-instance",
                displayName = "TV",
                protocolRange = NaviampConnectProtocolRange(),
                capabilities = emptySet(),
                port = 42_425,
                identityFingerprint = "fingerprint-trust-a",
                expiresAtEpochMillis = 2_000L,
            ),
        )

        NaviampConnectTrustRepository(storage).upsert(
            trust("trust-a", "TV", 10).copy(lastKnownEndpoint = endpoint),
        )

        assertEquals(endpoint, NaviampConnectTrustRepository(storage).load().single().lastKnownEndpoint)
    }

    private fun trust(id: String, name: String, pairedAt: Long) = NaviampConnectTrustRecord(
        trustedDeviceId = id,
        peerDevice = NaviampConnectDevice("device-$id", name, NaviampConnectDeviceRole.Controller),
        identityFingerprint = "fingerprint-$id",
        publicKeyBase64 = "public-key-$id",
        pairedAtEpochMillis = pairedAt,
    )
}
