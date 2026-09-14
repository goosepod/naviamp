package app.naviamp.android

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import app.naviamp.app.NaviampConnectDiscoveryEffect
import app.naviamp.app.NaviampConnectDiscoveryListener
import app.naviamp.app.NaviampConnectDiscoveryStartResult
import app.naviamp.app.NaviampConnectResolvedService
import app.naviamp.domain.connect.NaviampConnectServiceType

/** Android DNS-SD callback adapter. Discovery policy and result interpretation remain in Core. */
class AndroidNaviampConnectDiscoveryEffect(context: Context) : NaviampConnectDiscoveryEffect {
    private val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)
    private val pendingResolutions = ArrayDeque<NsdServiceInfo>()
    private var discovery: NativeDiscovery? = null
    // Legacy NsdManager.resolveService permits only one in-flight resolution. Keep that native
    // lifetime across browse restarts; its callback releases the slot for the current request.
    private var resolving = false

    private inner class NativeDiscovery(val listener: NaviampConnectDiscoveryListener) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            synchronized(this@AndroidNaviampConnectDiscoveryEffect) {
                if (discovery !== this || serviceInfo.serviceType.trimEnd('.') != NaviampConnectServiceType) return
                pendingResolutions.addLast(serviceInfo)
                resolveNextLocked()
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(this@AndroidNaviampConnectDiscoveryEffect) {
                if (discovery !== this) return
                pendingResolutions.removeAll { it.serviceName == serviceInfo.serviceName }
                listener.onServiceLost(serviceInfo.serviceName)
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            failDiscovery(this, "Android could not start local target discovery (error $errorCode).",
                permissionDenied = errorCode == AndroidNsdPermissionDenied)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            failDiscovery(this, "Android could not stop local target discovery (error $errorCode).")
        }
    }

    @Synchronized
    override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
        if (discovery != null) return NaviampConnectDiscoveryStartResult.Started
        val request = NativeDiscovery(listener)
        discovery = request
        return try {
            nsdManager.discoverServices(NaviampConnectServiceType, NsdManager.PROTOCOL_DNS_SD, request)
            NaviampConnectDiscoveryStartResult.Started
        } catch (_: SecurityException) {
            discovery = null
            NaviampConnectDiscoveryStartResult.PermissionDenied
        } catch (error: RuntimeException) {
            discovery = null
            NaviampConnectDiscoveryStartResult.Unavailable(
                error.message ?: "Android network service discovery is unavailable.",
            )
        }
    }

    @Synchronized
    override fun stop() {
        val request = discovery
        discovery = null
        pendingResolutions.clear()
        if (request != null) runCatching { nsdManager.stopServiceDiscovery(request) }
    }

    @Suppress("DEPRECATION")
    private fun resolveNextLocked() {
        val request = discovery ?: return
        if (resolving) return
        val service = pendingResolutions.removeFirstOrNull() ?: return
        resolving = true
        try {
            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(this@AndroidNaviampConnectDiscoveryEffect) {
                        if (errorCode == AndroidNsdPermissionDenied) {
                            failDiscovery(request, "Permission denied", permissionDenied = true)
                        }
                        finishResolution(request, null)
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    finishResolution(request, serviceInfo.toNaviampConnectResolvedService())
                }
            })
        } catch (_: SecurityException) {
            failDiscovery(request, "Permission denied", permissionDenied = true)
            finishResolution(request, null)
        } catch (_: RuntimeException) {
            finishResolution(request, null)
        }
    }

    @Synchronized
    private fun finishResolution(request: NativeDiscovery, service: NaviampConnectResolvedService?) {
        resolving = false
        if (discovery === request && service != null) request.listener.onServiceResolved(service)
        resolveNextLocked()
    }

    @Synchronized
    private fun failDiscovery(request: NativeDiscovery, message: String, permissionDenied: Boolean = false) {
        if (discovery !== request) return
        stop()
        if (permissionDenied) request.listener.onPermissionDenied() else request.listener.onDiscoveryFailed(message)
    }
}

internal fun NsdServiceInfo.toNaviampConnectResolvedService(): NaviampConnectResolvedService? {
    val resolvedAddresses = if (Build.VERSION.SDK_INT >= 34) {
        hostAddresses.mapNotNull { it.hostAddress }
    } else {
        @Suppress("DEPRECATION")
        listOfNotNull(host?.hostAddress)
    }.distinct()
    if (resolvedAddresses.isEmpty()) return null
    val decodedAttributes = attributes.mapValues { (_, value) -> value.toString(Charsets.UTF_8) }
    return runCatching {
        NaviampConnectResolvedService(
            serviceName = serviceName,
            addresses = resolvedAddresses,
            port = port,
            textAttributes = decodedAttributes,
        )
    }.getOrNull()
}

// NsdManager.FAILURE_PERMISSION_DENIED (API 37 / T Extensions 22). Keep the native value
// while compiling against SDK 36; this is an OS error translation, not permission policy.
private const val AndroidNsdPermissionDenied = 7
