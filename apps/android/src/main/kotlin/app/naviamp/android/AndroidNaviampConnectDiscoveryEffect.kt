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
    private var listener: NaviampConnectDiscoveryListener? = null
    private var discovering = false
    private var resolving = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            synchronized(this@AndroidNaviampConnectDiscoveryEffect) {
                if (!discovering || serviceInfo.serviceType.trimEnd('.') != NaviampConnectServiceType) return
                pendingResolutions.addLast(serviceInfo)
                resolveNextLocked()
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            synchronized(this@AndroidNaviampConnectDiscoveryEffect) {
                pendingResolutions.removeAll { it.serviceName == serviceInfo.serviceName }
                listener?.onServiceLost(serviceInfo.serviceName)
            }
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            failDiscovery("Android could not start local target discovery (error $errorCode).",
                permissionDenied = errorCode == AndroidNsdPermissionDenied)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            failDiscovery("Android could not stop local target discovery (error $errorCode).")
        }

        override fun onDiscoveryStopped(serviceType: String) = Unit
    }

    @Synchronized
    override fun start(listener: NaviampConnectDiscoveryListener): NaviampConnectDiscoveryStartResult {
        if (discovering) return NaviampConnectDiscoveryStartResult.Started
        this.listener = listener
        discovering = true
        return try {
            nsdManager.discoverServices(
                NaviampConnectServiceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener,
            )
            NaviampConnectDiscoveryStartResult.Started
        } catch (_: SecurityException) {
            clearLocked()
            NaviampConnectDiscoveryStartResult.PermissionDenied
        } catch (error: RuntimeException) {
            clearLocked()
            NaviampConnectDiscoveryStartResult.Unavailable(
                error.message ?: "Android network service discovery is unavailable.",
            )
        }
    }

    @Synchronized
    override fun stop() {
        if (!discovering) {
            clearLocked()
            return
        }
        discovering = false
        pendingResolutions.clear()
        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        clearLocked()
    }

    @Suppress("DEPRECATION")
    private fun resolveNextLocked() {
        if (!discovering || resolving) return
        val service = pendingResolutions.removeFirstOrNull() ?: return
        resolving = true
        try {
            nsdManager.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (errorCode == AndroidNsdPermissionDenied) {
                            failDiscovery("Permission denied", permissionDenied = true)
                        } else {
                            finishResolution(null)
                        }
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        finishResolution(serviceInfo.toNaviampConnectResolvedService())
                    }
                },
            )
        } catch (_: SecurityException) {
            failDiscovery("Permission denied", permissionDenied = true)
        } catch (_: RuntimeException) {
            resolving = false
            resolveNextLocked()
        }
    }

    @Synchronized
    private fun finishResolution(service: NaviampConnectResolvedService?) {
        resolving = false
        if (discovering && service != null) listener?.onServiceResolved(service)
        resolveNextLocked()
    }

    @Synchronized
    private fun failDiscovery(message: String, permissionDenied: Boolean = false) {
        if (!discovering) return
        val currentListener = listener
        stop()
        if (permissionDenied) currentListener?.onPermissionDenied() else currentListener?.onDiscoveryFailed(message)
    }

    private fun clearLocked() {
        discovering = false
        resolving = false
        pendingResolutions.clear()
        listener = null
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
