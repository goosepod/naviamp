package app.naviamp.provider.navidrome

import kotlin.test.Test
import kotlin.test.assertEquals

class NavidromeApiCallDiagnosticsTest {
    @Test
    fun nativeNetworkErrorsCannotExposeUrlsOrCredentials() {
        val nativeTlsFailure =
            "Exception in http request: Error Domain=NSURLErrorDomain Code=-1200 " +
                "UserInfo={NSErrorFailingURLKey=https://server/rest/ping.view?u=user&t=secret}"

        assertEquals("TLS request failed.", nativeTlsFailure.sanitizedNavidromeErrorMessage())
        assertEquals(
            "Request failed.",
            "Native failure at https://server/path?token=secret".sanitizedNavidromeErrorMessage(),
        )
        assertEquals("Navidrome returned HTTP 401.", "Navidrome returned HTTP 401.".sanitizedNavidromeErrorMessage())
    }
}
