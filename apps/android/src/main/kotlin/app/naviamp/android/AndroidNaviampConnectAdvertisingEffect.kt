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
    private var listener: NaviampConnectAdvertisingListener? = null
    private var registrationRequested = false
    private var active = false

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            synchronized(this@AndroidNaviampConnectAdvertisingEffect) {
                if (!active) {
                    runCatching { nsdManager.unregisterService(this) }
                    return
                }
                listener?.onServiceRegistered(serviceInfo.serviceName)
            }
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            failRegistration("Android could not advertise this Naviamp target (error $errorCode).")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            synchronized(this@AndroidNaviampConnectAdvertisingEffect) {
                if (active) {
                    failRegistration("Android could not stop advertising this Naviamp target (error $errorCode).")
                }
            }
        }
    }

    @Synchronized
    override fun start(
        service: NaviampConnectRegistrationService,
        listener: NaviampConnectAdvertisingListener,
    ): NaviampConnectAdvertisingStartResult {
        if (active) return NaviampConnectAdvertisingStartResult.Started
        val serviceInfo = service.toAndroidNsdServiceInfo()
        this.listener = listener
        active = true
        registrationRequested = true
        return try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener,
            )
            NaviampConnectAdvertisingStartResult.Started
        } catch (_: SecurityException) {
            clearLocked()
            NaviampConnectAdvertisingStartResult.PermissionDenied
        } catch (error: RuntimeException) {
            clearLocked()
            NaviampConnectAdvertisingStartResult.Unavailable(
                error.message ?: "Android network service registration is unavailable.",
            )
        }
    }

    @Synchronized
    override fun stop() {
        active = false
        listener = null
        if (registrationRequested) runCatching { nsdManager.unregisterService(registrationListener) }
        registrationRequested = false
    }

    @Synchronized
    private fun failRegistration(message: String) {
        if (!active) return
        val currentListener = listener
        clearLocked()
        currentListener?.onRegistrationFailed(message)
    }

    private fun clearLocked() {
        active = false
        registrationRequested = false
        listener = null
    }
}

internal fun NaviampConnectRegistrationService.toAndroidNsdServiceInfo(): NsdServiceInfo =
    NsdServiceInfo().also { serviceInfo ->
        serviceInfo.serviceName = serviceName
        serviceInfo.serviceType = NaviampConnectServiceType
        serviceInfo.port = port
        textAttributes.forEach(serviceInfo::setAttribute)
    }
