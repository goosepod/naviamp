package app.naviamp.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.app.NaviampConnectAdvertisingListener
import app.naviamp.app.NaviampConnectAdvertisingStartResult
import app.naviamp.app.NaviampConnectDiscoveryListener
import app.naviamp.app.NaviampConnectDiscoveryStartResult
import app.naviamp.app.NaviampConnectRegistrationService
import app.naviamp.app.NaviampConnectResolvedService
import app.naviamp.domain.connect.NaviampConnectAdvertisement
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDiscoveryMetadata
import app.naviamp.domain.connect.NaviampConnectProtocolRange
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNaviampConnectCrossDeviceInstrumentedTest {
    @Test
    fun advertiseOrDiscoverAccordingToTheRequestedDeviceRole() {
        when (InstrumentationRegistry.getArguments().getString("connectRole")) {
            "target" -> advertiseTarget()
            "controller" -> discoverTarget()
            else -> error("The cross-device Connect test requires -e connectRole target|controller.")
        }
    }

    private fun advertiseTarget() {
        val registered = CountDownLatch(1)
        var failure: String? = null
        val effect = AndroidNaviampConnectAdvertisingEffect(ApplicationProvider.getApplicationContext())
        val result = effect.start(
            targetRegistration(),
            object : NaviampConnectAdvertisingListener {
                override fun onServiceRegistered(registeredServiceName: String) {
                    registered.countDown()
                }

                override fun onRegistrationFailed(message: String) {
                    failure = message
                    registered.countDown()
                }
            },
        )
        try {
            assertEquals(NaviampConnectAdvertisingStartResult.Started, result)
            assertTrue(registered.await(5, TimeUnit.SECONDS), "TV target registration timed out.")
            assertEquals(null, failure)
            CountDownLatch(1).await(20, TimeUnit.SECONDS)
        } finally {
            effect.stop()
        }
    }

    private fun discoverTarget() {
        val discovered = CountDownLatch(1)
        var failure: String? = null
        val effect = AndroidNaviampConnectDiscoveryEffect(ApplicationProvider.getApplicationContext())
        val result = effect.start(
            object : NaviampConnectDiscoveryListener {
                override fun onServiceResolved(service: NaviampConnectResolvedService) {
                    val advertisement = NaviampConnectDiscoveryMetadata.decode(
                        attributes = service.textAttributes,
                        port = service.port,
                        expiresAtEpochMillis = Long.MAX_VALUE,
                    )
                    if (advertisement?.instanceId == CrossDeviceInstanceId) discovered.countDown()
                }

                override fun onServiceLost(serviceName: String) = Unit

                override fun onDiscoveryFailed(message: String) {
                    failure = message
                    discovered.countDown()
                }
            },
        )
        try {
            assertEquals(NaviampConnectDiscoveryStartResult.Started, result)
            assertTrue(
                discovered.await(15, TimeUnit.SECONDS),
                "Pixel did not discover the TV emulator target within 15 seconds.",
            )
            assertEquals(null, failure)
        } finally {
            effect.stop()
        }
    }

    private fun targetRegistration(): NaviampConnectRegistrationService {
        val advertisement = NaviampConnectAdvertisement(
            instanceId = CrossDeviceInstanceId,
            displayName = "Android TV Emulator",
            protocolRange = NaviampConnectProtocolRange(),
            capabilities = setOf(NaviampConnectCapability.TransportControls),
            port = 42_424,
            identityFingerprint = "cross-device-test-fingerprint",
            expiresAtEpochMillis = Long.MAX_VALUE,
        )
        return NaviampConnectRegistrationService(
            serviceName = "Naviamp Android TV Emulator",
            port = advertisement.port,
            textAttributes = NaviampConnectDiscoveryMetadata.encode(advertisement),
        )
    }
}

private const val CrossDeviceInstanceId = "naviamp-cross-device-tv"
