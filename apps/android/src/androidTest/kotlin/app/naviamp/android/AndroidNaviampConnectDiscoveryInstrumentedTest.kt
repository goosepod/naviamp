package app.naviamp.android

import android.net.ConnectivityManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.SystemClock
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
import kotlin.test.fail
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
        val registration = RegistrationOutcome()
        val effect: NaviampConnectAdvertisingEffect = AndroidNaviampConnectAdvertisingEffect(
            ApplicationProvider.getApplicationContext(),
        )

        val result = effect.start(
            registrationService("Naviamp Registration Test"), registration,
        )

        try {
            assertEquals(NaviampConnectAdvertisingStartResult.Started, result)
            registration.awaitSuccess("initial registration")
        } finally {
            effect.stop()
        }
    }

    @Test
    fun nativeRegistrationCanRestartBeforeUnregisterCallbacksFinish() {
        val effect = AndroidNaviampConnectAdvertisingEffect(ApplicationProvider.getApplicationContext())
        try {
            repeat(3) { index ->
                val registration = RegistrationOutcome()
                assertEquals(
                    NaviampConnectAdvertisingStartResult.Started,
                    effect.start(registrationService("Naviamp Restart Test"), registration),
                    "restart cycle ${index + 1} did not start: ${registration.diagnostics()}",
                )
                registration.awaitSuccess("restart cycle ${index + 1}")
                // Start the next cycle immediately, while this unregister callback may still be pending.
                effect.stop()
            }
            val finalRegistration = RegistrationOutcome()
            assertEquals(NaviampConnectAdvertisingStartResult.Started,
                effect.start(registrationService("Naviamp Restart Test"), finalRegistration))
            finalRegistration.awaitSuccess("final restart")
        } finally { effect.stop() }
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
            if (Build.VERSION.SDK_INT >= 34) {
                hostAddresses = listOf(InetAddress.getByName("192.0.2.10"))
            } else {
                @Suppress("DEPRECATION")
                host = InetAddress.getByName("192.0.2.10")
            }
            NaviampConnectDiscoveryMetadata.encode(advertisement).forEach(::setAttribute)
        }

        val translated = assertNotNull(serviceInfo.toNaviampConnectResolvedService())

        assertEquals("Naviamp Living Room", translated.serviceName)
        assertEquals(listOf("192.0.2.10"), translated.addresses)
        assertEquals(42_424, translated.port)
        assertEquals(NaviampConnectDiscoveryMetadata.encode(advertisement), translated.textAttributes)
    }

    private class RegistrationOutcome : NaviampConnectAdvertisingListener {
        private val completed = CountDownLatch(1)
        private val startedAt = SystemClock.elapsedRealtime()
        @Volatile private var serviceName: String? = null
        @Volatile private var failure: String? = null

        override fun onServiceRegistered(registeredServiceName: String) {
            serviceName = registeredServiceName
            completed.countDown()
        }

        override fun onRegistrationFailed(message: String) {
            failure = message
            completed.countDown()
        }

        fun awaitSuccess(stage: String) {
            if (!completed.await(30, TimeUnit.SECONDS)) {
                fail("$stage timed out waiting for Android DNS-SD registration: ${diagnostics()}")
            }
            failure?.let { fail("$stage failed: ${diagnostics()}") }
            if (serviceName == null) fail("$stage returned no registered service name: ${diagnostics()}")
        }

        @Suppress("DEPRECATION")
        fun diagnostics(): String {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val networks = runCatching {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                connectivity.allNetworks.joinToString { network ->
                    "$network:${connectivity.getNetworkCapabilities(network)}"
                }
            }.getOrElse { error -> "unavailable (${error.javaClass.simpleName}: ${error.message})" }
            return "sdk=${Build.VERSION.SDK_INT}, elapsedMs=${SystemClock.elapsedRealtime() - startedAt}, " +
                "registered=$serviceName, failure=$failure, networks=[$networks]"
        }
    }

    private fun registrationService(serviceName: String = "Naviamp Instrumented TV"): NaviampConnectRegistrationService {
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
            serviceName = serviceName,
            port = advertisement.port,
            textAttributes = NaviampConnectDiscoveryMetadata.encode(advertisement),
        )
    }
}
