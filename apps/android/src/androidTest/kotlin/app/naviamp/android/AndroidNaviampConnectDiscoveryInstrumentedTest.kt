package app.naviamp.android

import android.net.nsd.NsdServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.naviamp.app.NaviampConnectDiscoveryListener
import app.naviamp.app.NaviampConnectDiscoveryStartResult
import app.naviamp.app.NaviampConnectAdvertisingEffect
import app.naviamp.app.NaviampConnectAdvertisingListener
import app.naviamp.app.NaviampConnectAdvertisingStartResult
import app.naviamp.app.NaviampConnectRegistrationService
import app.naviamp.app.NaviampConnectResolvedService
import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDiscoveryMetadata
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNaviampConnectDiscoveryInstrumentedTest {
    @Test
    fun nativeRegistrationPreservesSharedServiceDefinition() {
        val service = registrationService()

        val serviceInfo = service.toAndroidNsdServiceInfo()

        assertEquals(service.serviceName, serviceInfo.serviceName)
        assertEquals("_naviamp-connect._tcp", serviceInfo.serviceType)
        assertEquals(service.port, serviceInfo.port)
        assertEquals(
            service.textAttributes,
            serviceInfo.attributes.mapValues { (_, value) -> value.toString(Charsets.UTF_8) },
        )
    }

    @Test
    fun nativeDnsSdRegistrationCanStartAndStop() {
        val registered = CountDownLatch(1)
        val effect: NaviampConnectAdvertisingEffect = AndroidNaviampConnectAdvertisingEffect(
            ApplicationProvider.getApplicationContext(),
        )

        val result = effect.start(
            registrationService(),
            object : NaviampConnectAdvertisingListener {
                override fun onServiceRegistered(registeredServiceName: String) {
                    registered.countDown()
                }

                override fun onRegistrationFailed(message: String) = Unit
            },
        )

        assertEquals(NaviampConnectAdvertisingStartResult.Started, result)
        assertTrue(registered.await(5, TimeUnit.SECONDS), "Android did not confirm DNS-SD registration.")
        effect.stop()
    }

    @Test
    fun nativeDnsSdDiscoveryCanStartAndStop() {
        val effect = AndroidNaviampConnectDiscoveryEffect(ApplicationProvider.getApplicationContext())

        val result = effect.start(
            object : NaviampConnectDiscoveryListener {
                override fun onServiceResolved(service: NaviampConnectResolvedService) = Unit
                override fun onServiceLost(serviceName: String) = Unit
                override fun onDiscoveryFailed(message: String) = Unit
            },
        )
        effect.stop()

        assertEquals(NaviampConnectDiscoveryStartResult.Started, result)
    }

    @Test
    fun nativeServiceInfoIsTranslatedWithoutInterpretingConnectMetadata() {
        val advertisement = NaviampConnectAdvertisement(
            instanceId = "target-instance",
            displayName = "Living Room",
            protocolRange = NaviampConnectProtocolRange(),
            capabilities = setOf(NaviampConnectCapability.TransportControls),
            port = 42_424,
            identityFingerprint = "target-fingerprint",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Naviamp Living Room"
            serviceType = "_naviamp-connect._tcp"
            port = 42_424
            hostAddresses = listOf(InetAddress.getByName("192.0.2.10"))
            NaviampConnectDiscoveryMetadata.encode(advertisement).forEach(::setAttribute)
        }

        val translated = assertNotNull(serviceInfo.toNaviampConnectResolvedService())

        assertEquals("Naviamp Living Room", translated.serviceName)
        assertEquals(listOf("192.0.2.10"), translated.addresses)
        assertEquals(42_424, translated.port)
        assertEquals(NaviampConnectDiscoveryMetadata.encode(advertisement), translated.textAttributes)
    }

    private fun registrationService(): NaviampConnectRegistrationService {
        val advertisement = NaviampConnectAdvertisement(
            instanceId = "instrumented-target",
            displayName = "Instrumented TV",
            protocolRange = NaviampConnectProtocolRange(),
            capabilities = setOf(NaviampConnectCapability.TransportControls),
            port = 42_424,
            identityFingerprint = "instrumented-fingerprint",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        return NaviampConnectRegistrationService(
            serviceName = "Naviamp Instrumented TV",
            port = advertisement.port,
            textAttributes = NaviampConnectDiscoveryMetadata.encode(advertisement),
        )
    }
}
