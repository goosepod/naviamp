package app.naviamp.android

import android.util.Base64
import app.naviamp.app.NaviampCastSecureTokenSource
import java.security.SecureRandom

/** Android cryptographic random source for short-lived receiver access tokens. */
class AndroidNaviampCastSecureTokenSource(
    private val random: SecureRandom = SecureRandom(),
) : NaviampCastSecureTokenSource {
    override fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
