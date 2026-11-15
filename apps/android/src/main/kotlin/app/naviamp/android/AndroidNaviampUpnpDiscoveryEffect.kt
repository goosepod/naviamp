package app.naviamp.android

import android.content.Context
import android.net.wifi.WifiManager
import app.naviamp.app.*

/** Android Wi-Fi multicast lock lifetime; UDP/interface operations delegate to the JVM ABI adapter. */
class AndroidNaviampUpnpDiscoveryEffect(context: Context) : NaviampUpnpDiscoveryEffect {
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val udp = JvmNaviampUpnpDiscoveryEffect()
    private var lock: WifiManager.MulticastLock? = null
    override fun start(search: String, receive: (NaviampUpnpDiscoveryPacket) -> Unit) {
        stop()
        try {
            lock = wifi?.createMulticastLock("naviamp-upnp")?.apply { setReferenceCounted(false); acquire() }
            udp.start(search, receive)
        } catch (failure: Exception) { stop(); throw failure }
    }
    override fun refresh(search: String) = udp.refresh(search)
    override fun stop() {
        try { udp.stop() } finally { lock?.let { if (it.isHeld) it.release() }; lock = null }
    }
}
