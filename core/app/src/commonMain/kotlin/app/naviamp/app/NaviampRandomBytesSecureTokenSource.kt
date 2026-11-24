package app.naviamp.app

import kotlin.io.encoding.Base64

/** Shared opaque-token encoding. The host must fill every byte using OS cryptographic randomness. */
class NaviampRandomBytesSecureTokenSource(private val fillSecureBytes: (ByteArray) -> Unit) : NaviampCastSecureTokenSource {
    override fun newToken(): String {
        val bytes = ByteArray(32)
        fillSecureBytes(bytes)
        return Base64.UrlSafe.encode(bytes).trimEnd('=')
    }
}
