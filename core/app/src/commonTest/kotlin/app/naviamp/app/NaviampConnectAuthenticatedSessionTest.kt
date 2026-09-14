package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectPing
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NaviampConnectAuthenticatedSessionTest {
    @Test
    fun cancellationCannotInterruptAReservedSequenceBeforeItsTransportWrite() = runTest {
        val firstSendEntered = CompletableDeferred<Unit>()
        val releaseFirstSend = CompletableDeferred<Unit>()
        val sentSequences = mutableListOf<Long>()
        var sendCount = 0
        val connection = object : NaviampConnectTransportConnection {
            override val remoteAddress: String = "test"

            override suspend fun send(frame: ByteArray) {
                if (sendCount++ == 0) {
                    firstSendEntered.complete(Unit)
                    releaseFirstSend.await()
                }
                val packet = NaviampConnectTransportPacketCodec.decode(frame)
                    as NaviampConnectTransportPacket.Encrypted
                sentSequences += packet.frame.sequence
            }

            override suspend fun receive(): ByteArray? = null

            override fun close() = Unit
        }
        val session = NaviampConnectAuthenticatedSession(
            trust = null,
            connection = connection,
            channel = NaviampConnectAuthenticatedChannel(
                protocolVersion = 1,
                sessionId = "session",
                outboundDirection = NaviampConnectPakeRole.Target,
                cipher = PassthroughCipher,
            ),
            protocolVersion = 1,
            sessionId = "session",
        )

        val firstSend = launch { session.send(NaviampConnectPing(1)) }
        firstSendEntered.await()
        firstSend.cancel()
        assertFalse(firstSend.isCompleted)
        releaseFirstSend.complete(Unit)
        firstSend.cancelAndJoin()

        session.send(NaviampConnectPing(2))

        assertEquals(listOf(0L, 1L), sentSequences)
    }

    private object PassthroughCipher : NaviampConnectAuthenticatedCipher {
        override fun seal(sequence: Long, plaintext: ByteArray, authenticatedData: ByteArray): ByteArray =
            plaintext.copyOf()

        override fun open(sequence: Long, ciphertext: ByteArray, authenticatedData: ByteArray): ByteArray =
            ciphertext.copyOf()

        override fun destroy() = Unit
    }
}
