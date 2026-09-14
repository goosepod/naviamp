package app.naviamp.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.naviamp.app.BouncyCastleNaviampConnectPakeFactory
import app.naviamp.app.NaviampConnectPakeProgress
import app.naviamp.app.NaviampConnectPakeRole
import app.naviamp.app.NaviampConnectPakeSession
import app.naviamp.domain.connect.NaviampConnectPairingHandshake
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNaviampConnectPakeInstrumentedTest {
    @Test
    fun matchingCodeCompletesMutuallyConfirmedExchange() {
        val controller = session(NaviampConnectPakeRole.Controller, "controller", "target")
        val target = session(NaviampConnectPakeRole.Target, "target", "controller")
        val controllerRound1 = controller.start()
        val targetRound1 = target.start()
        val controllerRound2 = controller.receive(targetRound1).sent()
        val targetRound2 = target.receive(controllerRound1).sent()
        val controllerRound3 = controller.receive(targetRound2).sent()
        val targetRound3 = target.receive(controllerRound2).sent()
        val controllerSecret = controller.receive(targetRound3).completed()
        val targetSecret = target.receive(controllerRound3).completed()

        assertEquals(32, controllerSecret.size)
        assertContentEquals(controllerSecret, targetSecret)
    }

    private fun session(
        role: NaviampConnectPakeRole,
        localDeviceId: String,
        remoteDeviceId: String,
    ): NaviampConnectPakeSession = BouncyCastleNaviampConnectPakeFactory.create(
        pairingSessionId = "android-pairing-test",
        protocolVersion = 1,
        localRole = role,
        localDeviceId = localDeviceId,
        remoteDeviceId = remoteDeviceId,
        pairingCode = "482913".toCharArray(),
    )

    private fun NaviampConnectPakeProgress.sent(): NaviampConnectPairingHandshake =
        (this as NaviampConnectPakeProgress.Send).handshake

    private fun NaviampConnectPakeProgress.completed(): ByteArray =
        (this as NaviampConnectPakeProgress.Complete).sessionSecret.copyBytes()
}
