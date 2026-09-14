package app.naviamp.android

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import app.naviamp.app.NaviampConnectAdvertisingEffect
import app.naviamp.app.NaviampConnectAdvertisingListener
import app.naviamp.app.NaviampConnectAdvertisingStartResult
import app.naviamp.app.NaviampConnectRegistrationService
import app.naviamp.domain.connect.NaviampConnectServiceType

/** Android DNS-SD registration adapter. Pairing lifetime and advertised metadata remain in Core. */
class AndroidNaviampConnectAdvertisingEffect(context: Context) : NaviampConnectAdvertisingEffect {
    private val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)
    // NsdManager uses listener identity as the native registration handle. Never reuse it while
    // unregister callbacks from a previous request may still be in flight.
    private var registration: NsdManager.RegistrationListener? = null

    @Synchronized
    override fun start(
        service: NaviampConnectRegistrationService,
        listener: NaviampConnectAdvertisingListener,
    ): NaviampConnectAdvertisingStartResult {
        if (registration != null) return NaviampConnectAdvertisingStartResult.Started
        val request = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                synchronized(this@AndroidNaviampConnectAdvertisingEffect) {
                    if (registration !== this) {
                        runCatching { nsdManager.unregisterService(this) }
                    } else listener.onServiceRegistered(serviceInfo.serviceName)
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                synchronized(this@AndroidNaviampConnectAdvertisingEffect) {
                    if (registration !== this) return
                    registration = null
                    listener.onRegistrationFailed("Android could not advertise this Naviamp target (error $errorCode).")
                }
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // This callback belongs to this native handle, even after a replacement starts.
                listener.onRegistrationFailed("Android could not stop advertising this Naviamp target (error $errorCode).")
            }
        }
        registration = request
        return try {
            nsdManager.registerService(service.toAndroidNsdServiceInfo(), NsdManager.PROTOCOL_DNS_SD, request)
            NaviampConnectAdvertisingStartResult.Started
        } catch (_: SecurityException) {
            registration = null
            NaviampConnectAdvertisingStartResult.PermissionDenied
        } catch (error: RuntimeException) {
            registration = null
            NaviampConnectAdvertisingStartResult.Unavailable(
                error.message ?: "Android network service registration is unavailable.",
            )
        }
    }

    @Synchronized
    override fun stop() {
        val request = registration ?: return
        registration = null
        runCatching { nsdManager.unregisterService(request) }
    }
}

internal fun NaviampConnectRegistrationService.toAndroidNsdServiceInfo(): NsdServiceInfo =
    NsdServiceInfo().also { serviceInfo ->
        serviceInfo.serviceName = serviceName
        serviceInfo.serviceType = NaviampConnectServiceType
        serviceInfo.port = port
        textAttributes.forEach(serviceInfo::setAttribute)
    }
