@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.platform

import app.naviamp.app.NaviampCastSecureTokenSource
import app.naviamp.app.NaviampRandomBytesSecureTokenSource
import app.naviamp.app.NaviampUpnpHttpRequest
import app.naviamp.app.NaviampUpnpHttpResponse
import app.naviamp.app.NaviampUpnpHttpTransport
import app.naviamp.app.NaviampUpnpKtorTransport
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.*
import platform.darwin.ifaddrs
import platform.darwin.getifaddrs
import platform.darwin.freeifaddrs
import platform.Foundation.NSBundle
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault
import platform.posix.*

/** Dedicated NSURLSession engine; no provider cookies, cached credentials or TLS overrides. */
class IosNaviampUpnpHttpEffect : NaviampUpnpHttpTransport {
    private val engine = Darwin.create {
        configureSession {
            URLCredentialStorage = null
            URLCache = null
            HTTPCookieStorage = null
            HTTPShouldSetCookies = false
        }
    }
    private val transport = NaviampUpnpKtorTransport(engine)
    override suspend fun exchange(request: NaviampUpnpHttpRequest): NaviampUpnpHttpResponse = transport.exchange(request)
    fun close() { transport.close(); engine.close() }
}

/** SecRandomCopyBytes is the native entropy boundary; token encoding remains shared. */
class IosNaviampSecureTokenSource : NaviampCastSecureTokenSource by NaviampRandomBytesSecureTokenSource({ bytes ->
    val result = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, bytes.size.convert(), it.addressOf(0)) }
    check(result == errSecSuccess) { "OS secure random source unavailable" }
})

/** Build/signing fact only. The normal app build remains multicast-disabled until explicitly signed. */
fun iosUpnpMulticastAvailable(): Boolean =
    NSBundle.mainBundle.objectForInfoDictionaryKey("NaviampUpnpMulticastAvailable") == "YES"

/** getifaddrs/getnameinfo expose usable IPv4 interfaces to the shared UDP executor. No scan policy. */
fun iosUpnpLocalAddresses(): List<String> = memScoped {
    val head = alloc<CPointerVar<ifaddrs>>()
    check(getifaddrs(head.ptr) == 0)
    try {
        val addresses = mutableListOf<String>()
        var cursor = head.value
        while (cursor != null) {
            val entry = cursor.pointed
            val address = entry.ifa_addr
            val flags = entry.ifa_flags.toInt()
            if (address != null && address.pointed.sa_family.toInt() == AF_INET &&
                flags and IFF_UP != 0 && flags and IFF_LOOPBACK == 0 && flags and IFF_MULTICAST != 0) {
                val buffer = allocArray<ByteVar>(INET_ADDRSTRLEN)
                if (getnameinfo(address, sizeOf<sockaddr_in>().convert(), buffer, INET_ADDRSTRLEN.convert(),
                        null, 0.convert(), NI_NUMERICHOST) == 0) {
                    addresses += buffer.toKString()
                }
            }
            cursor = entry.ifa_next
        }
        addresses
    } finally { freeifaddrs(head.value) }
}
