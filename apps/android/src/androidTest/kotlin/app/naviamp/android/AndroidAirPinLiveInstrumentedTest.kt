package app.naviamp.android

import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.app.*
import app.naviamp.presentation.*
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.NaviampLibraryView
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.URI
import kotlin.test.*

/** Opt-in real sender acceptance: uses the installed app's saved provider and native Core graph. */
class AndroidAirPinLiveInstrumentedTest {
    @Test fun nativeWifiRouteDiagnostic() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveAirPinRoutes") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = CIO.create(); val http = NaviampUpnpKtorTransport(engine)
        try { record("description HTTP status=${http.exchange(NaviampUpnpHttpRequest("GET", checkNotNull(args.getString("airPinLocation")))).status}") }
        finally { http.close(); engine.close() }
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        val wifi = connectivity.allNetworks.first { connectivity.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true }
        val address = connectivity.getLinkProperties(wifi)!!.linkAddresses.map { it.address }.filterIsInstance<java.net.Inet4Address>().first()
        val manager = context.getSystemService(android.net.wifi.WifiManager::class.java)
        val lock = manager.createMulticastLock("naviamp-upnp-route-test").apply { acquire() }
        try {
            java.net.MulticastSocket(null).use { socket ->
                socket.networkInterface = java.net.NetworkInterface.getByInetAddress(address)
                socket.bind(java.net.InetSocketAddress(address, 0)); socket.soTimeout = 4_000
                val bytes = naviampUpnpSearchDatagram().encodeToByteArray()
                socket.send(java.net.DatagramPacket(bytes, bytes.size, java.net.InetAddress.getByName("239.255.255.250"), 1900))
                val packet = java.net.DatagramPacket(ByteArray(16_384), 16_384)
                val received = try { socket.receive(packet); true } catch (_: java.net.SocketTimeoutException) { false }
                record("multicastInterfacePinned=true response=$received")
            }
            for (pin in listOf(false, true)) {
                for (multicast in listOf(true, false)) {
                    java.net.DatagramSocket(null).use { socket ->
                        if (pin) wifi.bindSocket(socket)
                        socket.bind(java.net.InetSocketAddress(address, 0)); socket.soTimeout = 4_000
                        val bytes = naviampUpnpSearchDatagram().encodeToByteArray()
                        val host = if (multicast) "239.255.255.250" else URI(checkNotNull(args.getString("airPinLocation"))).host
                        socket.send(java.net.DatagramPacket(bytes, bytes.size, java.net.InetAddress.getByName(host), 1900))
                        val packet = java.net.DatagramPacket(ByteArray(16_384), 16_384)
                        val received = try { socket.receive(packet); true } catch (_: java.net.SocketTimeoutException) { false }
                        record("wifiPinned=$pin multicast=$multicast response=$received")
                    }
                }
            }
        } finally { lock.release() }
    }

    @Test fun savedProviderTrackThroughSharedPickerAndAndroidRelay() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveAirPin") == "true")
        val location = checkNotNull(args.getString("airPinLocation"))
        val udn = checkNotNull(args.getString("airPinUdn"))
        val name = args.getString("airPinName") ?: "APTV_223"
        val direct = args.getString("airPinDirect") == "true"
        val engine = CIO.create()
        val http = NaviampUpnpKtorTransport(engine)
        var core: NaviampCore? = null
        try {
            if (!direct) {
                val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
                val native = NaviampUpnpDiscoveryController(probeScope,
                    AndroidNaviampUpnpDiscoveryEffect(InstrumentationRegistry.getInstrumentation().targetContext), http,
                    System::currentTimeMillis, Dispatchers.IO)
                try {
                    withContext(Dispatchers.Main) { native.start() }
                    waitFor("native discovery on injected I/O context") { native.state.value.targets.any { it.renderer.name == name } }
                } catch (failure: Exception) {
                    record("native startup failure=${failure.javaClass.name}")
                    throw failure
                } finally { withContext(Dispatchers.Main) { native.stop() }; delay(250); probeScope.cancel() }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { core = AndroidNaviampApplicationRuntime.get(it).core }
                val app = checkNotNull(core)
                val playback = app.externalPlaybackBridge()
                waitFor("saved provider restoration") { app.state.value.shell.connectionSettings.connection.connected }
                val form = app.state.value.shell.connectionSettings.connection.form
                record("saved provider kind=${form.providerId}; loopback=${runCatching { URI(form.serverUrl).host in setOf("localhost", "127.0.0.1", "::1") }.getOrDefault(false)}")
                withContext(Dispatchers.Main) {
                    app.execute(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs))
                    app.execute(NaviampCoreCommand.Library.Refresh)
                }
                waitFor("saved provider library") { app.state.value.shell.library.songs.tracks.isNotEmpty() }
                val track = app.state.value.shell.library.songs.tracks.first { row ->
                    row.durationLabel.split(':').fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: 0) } > 90
                }
                withContext(Dispatchers.Main) { app.dispatch(NaviampCoreCommand.Library.TrackAction(SharedTrackRowActionRequest(track, SharedTrackRowAction.Select))) }
                waitFor("finite provider track") { !playback.snapshot().isLive && (playback.snapshot().durationMillis ?: 0) > 90_000 }
                record("finite queue ready; audio=${app.state.value.shell.nowPlaying?.audioInfo}")
                withContext(Dispatchers.Main) { playback.play() }
                if (direct) {
                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                    val wifi = connectivity.allNetworks.first { connectivity.getNetworkCapabilities(it)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true }
                    val address = connectivity.getLinkProperties(wifi)!!.linkAddresses.map { it.address }.filterIsInstance<java.net.Inet4Address>().first().hostAddress!!
                    record("directRendererDiagnostic=true; discoverySkipped=true")
                    withContext(Dispatchers.Main) { assertTrue(app.selectUpnpRenderer(location, udn, address)) }
                } else {
                    withContext(Dispatchers.Main) { app.showCastPicker() }
                    waitFor("Android multicast discovery") { app.castPickerState?.value?.targets?.any { it.displayName == name } == true }
                    val target = app.castPickerState!!.value.targets.first { it.displayName == name }
                    withContext(Dispatchers.Main) { app.selectCastTarget(target.id) }
                }
                waitFor("shared remote authority") { (app.playbackOutputState.value as? NaviampPlaybackOutputSelection.Remote)?.let {
                    it.target.kind == NaviampRemoteOutputKind.Upnp && it.target.displayName == name && it.playbackAuthorityActive
                } == true }
                record("shared remote authority active; describing receiver")
                val receiver = NaviampUpnpRendererClient.describe(http, location, udn)
                record("receiver described; probing capabilities")
                val caps = receiver.probe()
                waitFor("receiver playing") { receiver.transportState() == NaviampUpnpTransportState.PLAYING }
                val initial = receiver.position()
                val host = URI(initial.trackUri).host
                assertTrue(java.net.NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }.any { it.hostAddress == host }, "Media must be hosted by this Pixel, not a desktop relay")
                record("Pixel relay confirmed; audio=${app.state.value.shell.nowPlaying?.audioInfo}; supportsNext=${caps.supportsNext}; duration=${initial.durationMillis}; canSeek=${playback.snapshot().canSeek}")
                assertTrue((playback.snapshot().durationMillis ?: 0) > 90_000, "A zero renderer duration must preserve the saved finite track length")
                assertTrue(playback.snapshot().canSeek, "Shared external controls must expose seek for this finite track")
                withContext(Dispatchers.Main) { app.dismissCastPicker(); playback.pause() }
                waitFor("receiver paused") { receiver.transportState() == NaviampUpnpTransportState.PAUSED_PLAYBACK }
                withContext(Dispatchers.Main) { playback.seekTo(30_000) }
                delay(2_000)
                record("paused seek target=30000 observed=${receiver.position().positionMillis}")
                withContext(Dispatchers.Main) { playback.play() }
                waitFor("receiver resumed") { receiver.transportState() == NaviampUpnpTransportState.PLAYING }
                delay(3_000)
                record("resumed position=${receiver.position().positionMillis}; appPosition=${app.playbackProgress.value.positionSeconds}")
                withContext(Dispatchers.Main) { playback.seekTo(60_000) }
                delay(3_000)
                record("playing seek target=60000 observed=${receiver.position().positionMillis}; appPosition=${app.playbackProgress.value.positionSeconds}")
                withContext(Dispatchers.Main) { playback.stop() }
                waitFor("receiver stopped") { receiver.transportState() == NaviampUpnpTransportState.STOPPED }
                record("direct=$direct; Pixel relay/pause/resume/stop passed; discovery/picker checked only in non-direct mode; seek positions recorded separately")
            }
        } catch (failure: Exception) {
            val output = core?.playbackOutputState?.value as? NaviampPlaybackOutputSelection.Remote
            record("failure outputKind=${output?.target?.kind} phase=${output?.phase} authority=${output?.playbackAuthorityActive}")
            throw failure
        } finally {
            withContext(Dispatchers.Main) { core?.externalPlaybackBridge()?.stop(); core?.dismissCastPicker() }
            delay(500)
            withContext(Dispatchers.Main) {
                val output = core?.playbackOutputState?.value as? NaviampPlaybackOutputSelection.Remote
                if (output?.target?.kind == NaviampRemoteOutputKind.Upnp && output.target.id == udn) core?.selectLocalPlayback()
            }
            delay(500)
            http.close(); engine.close()
        }
    }

    private suspend fun waitFor(label: String, predicate: suspend () -> Boolean) {
        check(withTimeoutOrNull(45_000) { while (!predicate()) delay(250); true } == true) { "Timed out: $label" }
    }

    private fun record(message: String) = InstrumentationRegistry.getInstrumentation().sendStatus(2,
        Bundle().apply { putString("stream", "\nAIRPIN_ANDROID $message\n") })
}
