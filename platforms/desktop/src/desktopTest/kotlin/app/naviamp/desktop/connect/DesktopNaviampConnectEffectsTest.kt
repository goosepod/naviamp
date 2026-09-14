package app.naviamp.desktop.connect

import app.naviamp.app.NaviampConnectAdvertisingListener
import app.naviamp.app.NaviampConnectAdvertisingStartResult
import app.naviamp.app.NaviampConnectDiscoveryListener
import app.naviamp.app.NaviampConnectDiscoveryStartResult
import app.naviamp.app.NaviampConnectRegistrationService
import app.naviamp.app.NaviampConnectResolvedService
import java.lang.reflect.Proxy
import javax.jmdns.JmmDNS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopNaviampConnectEffectsTest {
    @Test
    fun registrationMapsTheSharedServiceWithoutAddingProductData() {
        val service = registrationService()

        val mapped = service.toDesktopJmDnsServiceInfo()

        assertEquals("_naviamp-connect._tcp.local.", mapped.type)
        assertEquals(service.serviceName, mapped.name)
        assertEquals(service.port, mapped.port)
        assertEquals("device", mapped.getPropertyString("id"))
        assertEquals("playback", mapped.getPropertyString("modes"))
    }

    @Test
    fun browsingAndAdvertisingShareOneNativeLifetime() {
        val calls = mutableListOf<String>()
        val jmDns = Proxy.newProxyInstance(
            JmmDNS::class.java.classLoader,
            arrayOf(JmmDNS::class.java),
        ) { _, method, _ ->
            calls += method.name
            null
        } as JmmDNS
        var closeCount = 0
        val network = DesktopNaviampConnectNetwork(
            createJmDns = { jmDns },
            closeJmDns = { closeCount += 1 },
        )
        val discovery = DesktopNaviampConnectDiscoveryEffect(network)
        val advertising = DesktopNaviampConnectAdvertisingEffect(network)
        var registeredName: String? = null

        assertEquals(
            NaviampConnectDiscoveryStartResult.Started,
            discovery.start(object : NaviampConnectDiscoveryListener {
                override fun onServiceResolved(service: NaviampConnectResolvedService) = Unit
                override fun onServiceLost(serviceName: String) = Unit
                override fun onDiscoveryFailed(message: String) = Unit
            }),
        )
        assertEquals(
            NaviampConnectAdvertisingStartResult.Started,
            advertising.start(
                registrationService(),
                object : NaviampConnectAdvertisingListener {
                    override fun onServiceRegistered(registeredServiceName: String) {
                        registeredName = registeredServiceName
                    }

                    override fun onRegistrationFailed(message: String) = Unit
                },
            ),
        )

        assertEquals("Naviamp Office", registeredName)
        assertTrue("addServiceListener" in calls)
        assertTrue("registerService" in calls)
        advertising.stop()
        assertTrue("unregisterService" in calls)
        assertEquals(0, closeCount)

        discovery.stop()
        assertTrue("removeServiceListener" in calls)
        assertEquals(1, closeCount)
        assertFalse(calls.isEmpty())
    }

    private fun registrationService() = NaviampConnectRegistrationService(
        serviceName = "Naviamp Office",
        port = 42_425,
        textAttributes = mapOf(
            "id" to "device",
            "modes" to "playback",
        ),
    )
}
