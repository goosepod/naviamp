package app.naviamp.desktop.cast

import app.naviamp.app.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** JVM interfaces and JmDNS callback boundary. Core retains/interprets/merges/expires every record. */
class DesktopNaviampCastDiscoveryEffect(private val ownerScope: CoroutineScope) : NaviampCastDiscoveryEffect {
    private class Browser(val dns: JmDNS, val type: String, val listener: ServiceListener)
    private class Resources(val listener: NaviampCastDiscoveryListener) {
        private var closed = false
        private val browsers = mutableListOf<Browser>()
        @Synchronized fun add(browser: Browser): Boolean {
            if (closed) return false
            browsers += browser
            return true
        }
        @Synchronized fun snapshot(): List<Browser> = browsers.toList()
        @Synchronized fun take(): List<Browser> { closed = true; return browsers.toList().also { browsers.clear() } }
    }
    private var opening: Job? = null
    private var resources: Resources? = null

    override fun start(serviceType: String, listener: NaviampCastDiscoveryListener) {
        val active = Resources(listener)
        resources = active
        opening = ownerScope.launch(Dispatchers.IO) {
            try {
                val addresses = NetworkInterface.getNetworkInterfaces().toList()
                    .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
                    .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().distinct()
                    addresses.forEach { address ->
                        val dns = JmDNS.create(address, "Naviamp Cast")
                        val callback = object : ServiceListener {
                            private fun key(event: ServiceEvent) = "${address.hostAddress}/${event.type}/${event.name}"
                            override fun serviceAdded(event: ServiceEvent) { dns.requestServiceInfo(event.type, event.name, true) }
                            override fun serviceRemoved(event: ServiceEvent) {
                                ownerScope.launch { listener.onServiceLost(key(event)) }
                            }
                            override fun serviceResolved(event: ServiceEvent) {
                                val info = event.info
                                val attributes = buildMap {
                                    info.propertyNames.toList().forEach { name -> info.getPropertyString(name)?.let { put(name, it) } }
                                }
                                val service = NaviampCastResolvedService(key(event), info.inetAddresses.map { it.hostAddress },
                                    info.port, attributes, localAddress = address.hostAddress)
                                ownerScope.launch { listener.onServiceResolved(service) }
                            }
                        }
                        if (!active.add(Browser(dns, serviceType, callback))) {
                            dns.close()
                            return@launch
                        }
                        dns.addServiceListener(serviceType, callback)
                    }
                if (active.snapshot().isEmpty()) ownerScope.launch { listener.onDiscoveryFailed(NaviampCastDiscoveryProblem.Unavailable) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                release(active)
                throw cancelled
            } catch (_: Exception) {
                release(active)
                ownerScope.launch { listener.onDiscoveryFailed(NaviampCastDiscoveryProblem.Unavailable) }
            }
        }
    }

    override fun refresh() {
        val native = resources ?: return
        val active = native.snapshot()
        ownerScope.launch(Dispatchers.IO) {
            try {
                active.forEach { browser ->
                    browser.dns.list(browser.type, 1).forEach { info -> browser.dns.requestServiceInfo(browser.type, info.name, true) }
                }
            } catch (_: Exception) {
                ownerScope.launch { native.listener.onDiscoveryFailed(NaviampCastDiscoveryProblem.Failed) }
            }
        }
    }

    override fun stop() {
        opening?.cancel(); opening = null
        resources?.let(::release)
        resources = null
    }

    private fun release(active: Resources) {
        val previous = active.take()
        DnsCloser.execute {
            previous.forEach { browser ->
                runCatching { browser.dns.removeServiceListener(browser.type, browser.listener); browser.dns.close() }
            }
        }
    }

    companion object {
        private val DnsCloser = Executors.newCachedThreadPool { task -> Thread(task, "naviamp-cast-dns-close").apply { isDaemon = true } }
    }
}
