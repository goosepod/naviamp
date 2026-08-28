package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectTrustRecord
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

    private fun trust(id: String, name: String, pairedAt: Long) = NaviampConnectTrustRecord(
        trustedDeviceId = id,
        peerDevice = NaviampConnectDevice("device-$id", name, NaviampConnectDeviceRole.Controller),
        identityFingerprint = "fingerprint-$id",
        publicKeyBase64 = "public-key-$id",
        pairedAtEpochMillis = pairedAt,
    )
}
