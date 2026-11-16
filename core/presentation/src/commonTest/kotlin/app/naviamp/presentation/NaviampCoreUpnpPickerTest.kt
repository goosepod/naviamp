package app.naviamp.presentation

import app.naviamp.app.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreUpnpPickerTest {
    @Test fun missingNativeMulticastCapabilityDoesNotExposeAnUnusablePicker() = runTest {
        val discovery = object : NaviampUpnpDiscoveryEffect {
            override fun start(search: String, receive: (NaviampUpnpDiscoveryPacket) -> Unit) = error("Unavailable capability must not start")
            override fun refresh(search: String) = Unit
            override fun stop() = Unit
        }
        val server = object : NaviampCastHttpServerEffect {
            override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String = error("No selection")
            override suspend fun stop() = Unit
        }
        val core = NaviampCore.create(backgroundScope, fakeCoreServices().copy(upnp = NaviampCoreUpnpServices(
            NaviampUpnpHttpTransport { error("No network request") }, server,
            NaviampCastSecureTokenSource { "capability-test-token-0000000000000000" }, discovery = discovery, multicastDiscoveryAvailable = false)))
        try { runCurrent(); assertFalse(core.castAvailable); core.showCastPicker(); runCurrent(); assertNull(core.castPickerState) }
        finally { core.shutdown() }
    }

    @Test fun upnpOnlyPickerDiscoversSelectsAndDismissesWithoutChangingActiveOutput() = runTest {
        lateinit var receive: (NaviampUpnpDiscoveryPacket) -> Unit
        var stops = 0
        val effect = object : NaviampUpnpDiscoveryEffect {
            override fun start(search: String, callback: (NaviampUpnpDiscoveryPacket) -> Unit) { receive = callback }
            override fun refresh(search: String) = Unit
            override fun stop() { stops++ }
        }
        val xml = "<root xmlns=\"urn:schemas-upnp-org:device-1-0\"><device><UDN>uuid:fixture</UDN><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType><friendlyName>Renderer</friendlyName><serviceList>" +
            listOf("AVTransport", "ConnectionManager").joinToString("") { "<service><serviceType>urn:schemas-upnp-org:service:$it:1</serviceType><controlURL>/control</controlURL><eventSubURL>/event</eventSubURL><SCPDURL>/scpd</SCPDURL></service>" } + "</serviceList></device></root>"
        val discovery = NaviampUpnpDiscoveryController(backgroundScope, effect,
            NaviampUpnpHttpTransport { NaviampUpnpHttpResponse(200, emptyMap(), xml) }, { 0L })
        val outputs = NaviampPlaybackOutputSelectionController()
        val selected = outputs.select(NaviampRemoteOutputTarget(NaviampRemoteOutputKind.Upnp, "uuid:fixture", "Renderer"))
        outputs.connected(selected)
        var selections = 0
        val picker = NaviampCoreCastPickerController(backgroundScope, null, null, outputs, discovery, {
            assertEquals("192.0.2.1", it.localAddress); selections++; true
        })
        picker.show(); runCurrent()
        receive(NaviampUpnpDiscoveryPacket("192.0.2.2", "192.0.2.1", "HTTP/1.1 200 OK\r\nST: $NAVIAMP_UPNP_RENDERER_TYPE\r\nUSN: uuid:fixture::$NAVIAMP_UPNP_RENDERER_TYPE\r\nLOCATION: http://192.0.2.2/device.xml\r\nCACHE-CONTROL: max-age=10\r\n\r\n"))
        runCurrent()
        val target = picker.state.value.targets.single()
        assertEquals(target.id, picker.state.value.selectedTargetId)
        picker.select(target.id); runCurrent()
        assertEquals(1, selections)
        assertFalse(picker.state.value.visible)
        assertTrue(stops > 0)
        assertEquals(selected, (outputs.state.value as NaviampPlaybackOutputSelection.Remote).selectionId)
        picker.close()
    }
}
