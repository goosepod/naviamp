package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectEnvelope
import app.naviamp.domain.connect.NaviampConnectPing
import app.naviamp.domain.connect.NaviampConnectPong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmNaviampConnectAuthenticatedChannelTest {
    @Test
    fun controllerAndTargetExchangeAuthenticatedEnvelopesInBothDirections() {
        val pair = channels()
        val controllerEnvelope = envelope(sequence = 0, message = NaviampConnectPing(42))
        val targetEnvelope = envelope(sequence = 0, message = NaviampConnectPong(42))

        val atTarget = assertIs<NaviampConnectSecureChannelOpenResult.Opened>(
            pair.target.open(pair.controller.seal(controllerEnvelope)),
        )
        val atController = assertIs<NaviampConnectSecureChannelOpenResult.Opened>(
            pair.controller.open(pair.target.seal(targetEnvelope)),
        )

        assertEquals(controllerEnvelope, atTarget.envelope)
        assertEquals(targetEnvelope, atController.envelope)
    }

    @Test
    fun tamperingFailsAuthenticationAndClosesTheChannel() {
        val pair = channels()
        val frame = pair.controller.seal(envelope(0, NaviampConnectPing(42)))
        val altered = frame.ciphertext.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        val rejected = assertIs<NaviampConnectSecureChannelOpenResult.Rejected>(
            pair.target.open(frame.copy(ciphertext = altered)),
        )

        assertEquals(NaviampConnectSecureChannelFailure.AuthenticationFailed, rejected.failure)
        assertEquals(
            NaviampConnectSecureChannelFailure.Closed,
            assertIs<NaviampConnectSecureChannelOpenResult.Rejected>(pair.target.open(frame)).failure,
        )
    }

    @Test
    fun replayAndSequenceGapsFailClosedBeforeDecryption() {
        val replayPair = channels()
        val first = replayPair.controller.seal(envelope(0, NaviampConnectPing(1)))
        assertIs<NaviampConnectSecureChannelOpenResult.Opened>(replayPair.target.open(first))
        assertEquals(
            NaviampConnectSecureChannelFailure.InvalidSequence,
            assertIs<NaviampConnectSecureChannelOpenResult.Rejected>(replayPair.target.open(first)).failure,
        )

        val gapPair = channels()
        val valid = gapPair.controller.seal(envelope(0, NaviampConnectPing(1)))
        val gap = valid.copy(sequence = 1)
        assertEquals(
            NaviampConnectSecureChannelFailure.InvalidSequence,
            assertIs<NaviampConnectSecureChannelOpenResult.Rejected>(gapPair.target.open(gap)).failure,
        )
    }

    @Test
    fun directionAndSessionAreCryptographicallyBound() {
        val controllerSecret = secret()
        val otherControllerSecret = secret()
        val firstController = channel(NaviampConnectPakeRole.Controller, controllerSecret)
        val secondController = channel(NaviampConnectPakeRole.Controller, otherControllerSecret)
        val frame = firstController.seal(envelope(0, NaviampConnectPing(1)))

        assertEquals(
            NaviampConnectSecureChannelFailure.AuthenticationFailed,
            assertIs<NaviampConnectSecureChannelOpenResult.Rejected>(secondController.open(frame)).failure,
        )

        val sessionPair = channels()
        val correct = sessionPair.controller.seal(envelope(0, NaviampConnectPing(1)))
        assertEquals(
            NaviampConnectSecureChannelFailure.InvalidSession,
            assertIs<NaviampConnectSecureChannelOpenResult.Rejected>(
                sessionPair.target.open(correct.copy(sessionId = "other-session")),
            ).failure,
        )
    }

    @Test
    fun cipherFactoryConsumesTheRootSecretAndClosePreventsReuse() {
        val root = secret()
        val channel = channel(NaviampConnectPakeRole.Controller, root)
        assertTrue(root.isDestroyed)
        channel.close()

        assertFailsWith<IllegalStateException> {
            channel.seal(envelope(0, NaviampConnectPing(1)))
        }
    }

    private fun channels(): Channels = Channels(
        controller = channel(NaviampConnectPakeRole.Controller, secret()),
        target = channel(NaviampConnectPakeRole.Target, secret()),
    )

    private fun channel(
        role: NaviampConnectPakeRole,
        root: NaviampConnectSessionSecret,
    ) = NaviampConnectAuthenticatedChannel(
        protocolVersion = PROTOCOL_VERSION,
        sessionId = SESSION_ID,
        outboundDirection = role,
        cipher = JvmNaviampConnectAuthenticatedCipherFactory.create(
            sessionSecret = root,
            role = role,
            protocolVersion = PROTOCOL_VERSION,
            sessionId = SESSION_ID,
        ),
    )

    private fun secret() = NaviampConnectSessionSecret(ByteArray(32) { (it + 1).toByte() })

    private fun envelope(sequence: Long, message: app.naviamp.domain.connect.NaviampConnectMessage) =
        NaviampConnectEnvelope(
            protocolVersion = PROTOCOL_VERSION,
            sessionId = SESSION_ID,
            sequence = sequence,
            message = message,
        )

    private data class Channels(
        val controller: NaviampConnectAuthenticatedChannel,
        val target: NaviampConnectAuthenticatedChannel,
    )

    private companion object {
        const val PROTOCOL_VERSION = 1
        const val SESSION_ID = "secure-session-1"
    }
}
