package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import app.naviamp.domain.connect.NaviampConnectTrustedEndpoint
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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

    @Test
    fun persistsSelfNameAndReadsLegacyTrustLists() {
        var stored: String? = null
        val storage = object : NaviampConnectTrustStorageEffect {
            override fun read() = stored
            override fun write(value: String) { stored = value }
        }
        val repository = NaviampConnectTrustRepository(storage)
        repository.upsert(trust("trust-a", "TV", 10))

        assertEquals("Kitchen tablet", repository.setSelfName("  Kitchen tablet  "))
        assertEquals("Kitchen tablet", NaviampConnectTrustRepository(storage).selfName())
        assertEquals("TV", repository.load().single().displayName)

        stored = Json.encodeToString(listOf(trust("legacy", "Legacy TV", 1)))
        assertEquals("Legacy TV", repository.load().single().displayName)
        assertNull(repository.selfName())
    }

    @Test
    fun localAliasSurvivesPeerRenameAndCanBeCleared() {
        var stored: String? = null
        val repository = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = stored
            override fun write(value: String) { stored = value }
        })
        val original = trust("trust-a", "TV", 10)
        repository.upsert(original)
        repository.setAlias("trust-a", "Living room")

        repository.upsert(original.copy(peerDevice = original.peerDevice.copy(displayName = "Television")))
        assertEquals("Living room", repository.load().single().localAlias)

        repository.setAlias("trust-a", "   ")
        assertNull(repository.load().single().localAlias)
    }

    @Test
    fun rejectsOverlongNamesWithoutDamagingExistingName() {
        var stored: String? = null
        val repository = NaviampConnectTrustRepository(object : NaviampConnectTrustStorageEffect {
            override fun read() = stored
            override fun write(value: String) { stored = value }
        })
        repository.setSelfName("Phone 🎵")

        assertFailsWith<IllegalArgumentException> { repository.setSelfName("x".repeat(65)) }
        assertEquals("Phone 🎵", repository.selfName())
    }

    private fun trust(id: String, name: String, pairedAt: Long) = NaviampConnectTrustRecord(
        trustedDeviceId = id,
        peerDevice = NaviampConnectDevice("device-$id", name, NaviampConnectDeviceRole.Controller),
        identityFingerprint = "fingerprint-$id",
        publicKeyBase64 = "public-key-$id",
        pairedAtEpochMillis = pairedAt,
    )
}
