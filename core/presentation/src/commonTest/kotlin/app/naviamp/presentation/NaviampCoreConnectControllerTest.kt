package app.naviamp.presentation

import app.naviamp.app.NaviampConnectAdvertisingEffect
import app.naviamp.app.NaviampConnectAdvertisingListener
import app.naviamp.app.NaviampConnectAdvertisingStartResult
import app.naviamp.app.NaviampConnectAuthenticatedCipher
import app.naviamp.app.NaviampConnectAuthenticatedCipherFactory
import app.naviamp.app.NaviampConnectDeviceIdentity
import app.naviamp.app.NaviampConnectDeviceIdentityEffect
import app.naviamp.app.NaviampConnectIdentityVerifier
import app.naviamp.app.NaviampConnectPakeFactory
import app.naviamp.app.NaviampConnectPakeRole
import app.naviamp.app.NaviampConnectPakeSession
import app.naviamp.app.NaviampConnectRegistrationService
import app.naviamp.app.NaviampConnectSessionSecret
import app.naviamp.app.NaviampConnectTransportConnection
import app.naviamp.app.NaviampConnectTransportFactory
import app.naviamp.app.NaviampConnectTransportListener
import app.naviamp.app.NaviampConnectTrustRepository
import app.naviamp.app.NaviampConnectTrustStorageEffect
import app.naviamp.ui.NaviampConnectPairingUiPhase
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NaviampCoreConnectControllerTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun targetStartsRealListenerAdvertisesItsPortAndPublishesCode() = runTest {
        val advertising = RecordingAdvertisingEffect()
        val listener = WaitingListener(42425)
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreConnectController(
            scope = this,
            stateStore = store,
            services = NaviampCoreConnectServices(
                role = NaviampCoreConnectRole.Target,
                displayName = "Living Room TV",
                identity = FakeIdentity,
                identityVerifier = FakeIdentityVerifier,
                transport = object : NaviampConnectTransportFactory {
                    override suspend fun connect(host: String, port: Int) = error("unused")
                    override fun listen(port: Int) = listener
                },
                pake = UnusedPakeFactory,
                cipher = UnusedCipherFactory,
                trust = NaviampConnectTrustRepository(NaviampConnectTrustStorageEffect {}),
                advertising = advertising,
                newOpaqueId = sequenceOf("instance", "session").iterator()::next,
                newPairingCode = { "123456" },
                nowEpochMillis = { 1_000L },
            ),
        )

        controller.actions.onStartPairingMode()
        runCurrent()

        val service = assertNotNull(advertising.service)
        assertEquals(42425, service.port)
        assertTrue(service.serviceName.contains("Living Room TV"))
        assertEquals("123456", store.state.value.shell.connect.pairingCode)
        assertEquals(NaviampConnectPairingUiPhase.Advertising, store.state.value.shell.connect.pairingPhase)
        assertTrue(service.textAttributes.isNotEmpty())

        controller.close()
        assertTrue(listener.closed)
    }
}

private class RecordingAdvertisingEffect : NaviampConnectAdvertisingEffect {
    var service: NaviampConnectRegistrationService? = null

    override fun start(
        service: NaviampConnectRegistrationService,
        listener: NaviampConnectAdvertisingListener,
    ): NaviampConnectAdvertisingStartResult {
        this.service = service
        listener.onServiceRegistered(service.serviceName)
        return NaviampConnectAdvertisingStartResult.Started
    }

    override fun stop() = Unit
}

private class WaitingListener(override val port: Int) : NaviampConnectTransportListener {
    var closed = false
    override suspend fun accept(): NaviampConnectTransportConnection = awaitCancellation()
    override fun close() { closed = true }
}

private object FakeIdentity : NaviampConnectDeviceIdentityEffect {
    override fun loadOrCreate() = NaviampConnectDeviceIdentity("target-id", "fingerprint", "public-key")
    override fun sign(payload: ByteArray) = byteArrayOf(1)
}

private object FakeIdentityVerifier : NaviampConnectIdentityVerifier {
    override fun fingerprint(publicKeyBase64: String) = "fingerprint"
    override fun verify(publicKeyBase64: String, payload: ByteArray, signature: ByteArray) = true
}

private object UnusedPakeFactory : NaviampConnectPakeFactory {
    override fun create(
        pairingSessionId: String,
        protocolVersion: Int,
        localRole: NaviampConnectPakeRole,
        localDeviceId: String,
        remoteDeviceId: String,
        pairingCode: CharArray,
    ): NaviampConnectPakeSession = error("unused")
}

private object UnusedCipherFactory : NaviampConnectAuthenticatedCipherFactory {
    override fun create(
        sessionSecret: NaviampConnectSessionSecret,
        role: NaviampConnectPakeRole,
        protocolVersion: Int,
        sessionId: String,
    ): NaviampConnectAuthenticatedCipher = error("unused")
}
