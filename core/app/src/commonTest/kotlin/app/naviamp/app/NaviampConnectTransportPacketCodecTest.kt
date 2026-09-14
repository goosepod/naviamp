package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectPing
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NaviampConnectTransportPacketCodecTest {
    @Test
    fun roundTripsPlaintextAndEncryptedPackets() {
        val envelope = NaviampConnectEnvelope(1, sequence = 0, message = NaviampConnectPing(42))
        val plaintext = assertIs<NaviampConnectTransportPacket.Plaintext>(
            NaviampConnectTransportPacketCodec.decode(
                NaviampConnectTransportPacketCodec.encode(NaviampConnectTransportPacket.Plaintext(envelope)),
            ),
        )
        assertEquals(envelope, plaintext.envelope)

        val frame = NaviampConnectEncryptedFrame(1, "session", 7, byteArrayOf(1, 2, 3))
        val encrypted = assertIs<NaviampConnectTransportPacket.Encrypted>(
            NaviampConnectTransportPacketCodec.decode(
                NaviampConnectTransportPacketCodec.encode(NaviampConnectTransportPacket.Encrypted(frame)),
            ),
        )
        assertEquals(frame.protocolVersion, encrypted.frame.protocolVersion)
        assertEquals(frame.sessionId, encrypted.frame.sessionId)
        assertEquals(frame.sequence, encrypted.frame.sequence)
        assertContentEquals(frame.ciphertext, encrypted.frame.ciphertext)
    }

    @Test
    fun rejectsUnknownAndTruncatedPackets() {
        assertFailsWith<IllegalArgumentException> { NaviampConnectTransportPacketCodec.decode(byteArrayOf()) }
        assertFailsWith<IllegalArgumentException> { NaviampConnectTransportPacketCodec.decode(byteArrayOf(99)) }
        assertFailsWith<IllegalArgumentException> { NaviampConnectTransportPacketCodec.decode(byteArrayOf(2, 0)) }
    }
}
