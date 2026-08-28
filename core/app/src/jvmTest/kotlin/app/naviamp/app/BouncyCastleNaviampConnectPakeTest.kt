package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectPairingHandshake
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BouncyCastleNaviampConnectPakeTest {
    @Test
    fun matchingCodesCompleteMutualConfirmationAndDeriveTheSameSecret() {
        val sessions = sessions("482913", "482913")

        val secrets = exchange(sessions.controller, sessions.target)

        assertContentEquals(secrets.controller.copyBytes(), secrets.target.copyBytes())
        assertEquals(32, secrets.controller.copyBytes().size)
        assertFalse(secrets.controller.isDestroyed)
    }

    @Test
    fun wrongCodeFailsExplicitKeyConfirmation() {
        val sessions = sessions("482913", "482914")
        val controllerRound1 = sessions.controller.start()
        val targetRound1 = sessions.target.start()
        val controllerRound2 = sessions.controller.receive(targetRound1).sentHandshake()
        val targetRound2 = sessions.target.receive(controllerRound1).sentHandshake()
        val controllerRound3 = sessions.controller.receive(targetRound2).sentHandshake()
        val targetRound3 = sessions.target.receive(controllerRound2).sentHandshake()

        val controllerFailure = assertFailsWith<NaviampConnectPakeException> {
            sessions.controller.receive(targetRound3)
        }
        val targetFailure = assertFailsWith<NaviampConnectPakeException> {
            sessions.target.receive(controllerRound3)
        }

        assertEquals(NaviampConnectPakeFailure.AuthenticationFailed, controllerFailure.failure)
        assertEquals(NaviampConnectPakeFailure.AuthenticationFailed, targetFailure.failure)
    }

    @Test
    fun tamperedConfirmationIsRejected() {
        val sessions = sessions("482913", "482913")
        val controllerRound1 = sessions.controller.start()
        val targetRound1 = sessions.target.start()
        val controllerRound2 = sessions.controller.receive(targetRound1).sentHandshake()
        val targetRound2 = sessions.target.receive(controllerRound1).sentHandshake()
        sessions.controller.receive(targetRound2).sentHandshake()
        val targetRound3 = sessions.target.receive(controllerRound2).sentHandshake()
        val bytes = Base64.getDecoder().decode(targetRound3.payloadBase64)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()

        val failure = assertFailsWith<NaviampConnectPakeException> {
            sessions.controller.receive(
                targetRound3.copy(payloadBase64 = Base64.getEncoder().withoutPadding().encodeToString(bytes)),
            )
        }

        assertEquals(NaviampConnectPakeFailure.AuthenticationFailed, failure.failure)
    }

    @Test
    fun wrongPairingSessionAndOutOfOrderMessagesFailClosed() {
        val wrongSession = sessions("482913", "482913")
        wrongSession.controller.start()
        val targetRound1 = wrongSession.target.start()

        val sessionFailure = assertFailsWith<NaviampConnectPakeException> {
            wrongSession.controller.receive(targetRound1.copy(pairingSessionId = "another-session"))
        }
        assertEquals(NaviampConnectPakeFailure.InvalidSession, sessionFailure.failure)

        val outOfOrder = sessions("482913", "482913")
        outOfOrder.controller.start()
        val stepFailure = assertFailsWith<NaviampConnectPakeException> {
            outOfOrder.controller.receive(targetRound1.copy(step = 2))
        }
        assertEquals(NaviampConnectPakeFailure.InvalidState, stepFailure.failure)
    }

    @Test
    fun malformedPayloadFailsWithoutLeakingDecoderErrors() {
        val sessions = sessions("482913", "482913")
        sessions.controller.start()

        val failure = assertFailsWith<NaviampConnectPakeException> {
            sessions.controller.receive(
                NaviampConnectPairingHandshake(
                    pairingSessionId = PAIRING_SESSION_ID,
                    step = 1,
                    payloadBase64 = "not-base64!",
                ),
            )
        }

        assertEquals(NaviampConnectPakeFailure.InvalidPayload, failure.failure)
    }

    @Test
    fun factoryClearsCodeAndCompletedSecretCanBeDestroyed() {
        val code = "482913".toCharArray()
        val session = BouncyCastleNaviampConnectPakeFactory.create(
            pairingSessionId = PAIRING_SESSION_ID,
            protocolVersion = 1,
            localRole = NaviampConnectPakeRole.Controller,
            localDeviceId = CONTROLLER_ID,
            remoteDeviceId = TARGET_ID,
            pairingCode = code,
        )
        assertTrue(code.all { it == '\u0000' })
        session.destroy()

        val complete = sessions("482913", "482913").let { exchange(it.controller, it.target).controller }
        complete.destroy()
        assertTrue(complete.isDestroyed)
        assertFailsWith<IllegalStateException> { complete.copyBytes() }
    }

    private fun sessions(controllerCode: String, targetCode: String): Sessions = Sessions(
        controller = BouncyCastleNaviampConnectPakeFactory.create(
            pairingSessionId = PAIRING_SESSION_ID,
            protocolVersion = 1,
            localRole = NaviampConnectPakeRole.Controller,
            localDeviceId = CONTROLLER_ID,
            remoteDeviceId = TARGET_ID,
            pairingCode = controllerCode.toCharArray(),
        ),
        target = BouncyCastleNaviampConnectPakeFactory.create(
            pairingSessionId = PAIRING_SESSION_ID,
            protocolVersion = 1,
            localRole = NaviampConnectPakeRole.Target,
            localDeviceId = TARGET_ID,
            remoteDeviceId = CONTROLLER_ID,
            pairingCode = targetCode.toCharArray(),
        ),
    )

    private fun exchange(
        controller: NaviampConnectPakeSession,
        target: NaviampConnectPakeSession,
    ): Secrets {
        val controllerRound1 = controller.start()
        val targetRound1 = target.start()
        val controllerRound2 = controller.receive(targetRound1).sentHandshake()
        val targetRound2 = target.receive(controllerRound1).sentHandshake()
        val controllerRound3 = controller.receive(targetRound2).sentHandshake()
        val targetRound3 = target.receive(controllerRound2).sentHandshake()
        val controllerSecret = controller.receive(targetRound3).completedSecret()
        val targetSecret = target.receive(controllerRound3).completedSecret()
        return Secrets(controllerSecret, targetSecret)
    }

    private fun NaviampConnectPakeProgress.sentHandshake(): NaviampConnectPairingHandshake =
        (this as NaviampConnectPakeProgress.Send).handshake

    private fun NaviampConnectPakeProgress.completedSecret(): NaviampConnectSessionSecret =
        (this as NaviampConnectPakeProgress.Complete).sessionSecret

    private data class Sessions(
        val controller: NaviampConnectPakeSession,
        val target: NaviampConnectPakeSession,
    )

    private data class Secrets(
        val controller: NaviampConnectSessionSecret,
        val target: NaviampConnectSessionSecret,
    )

    private companion object {
        const val PAIRING_SESSION_ID = "pairing-session-1"
        const val CONTROLLER_ID = "controller-device"
        const val TARGET_ID = "target-device"
    }
}
