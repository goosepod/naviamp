package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ConnectionValidation
import kotlin.test.Test
import kotlin.test.assertEquals

class NavidromeConnectionStatusTest {
    @Test
    fun successStatusIncludesServerVersionAndSmartPlaylistState() {
        val validation = ConnectionValidation(serverVersion = "0.55.0", apiVersion = "1.16.1")

        assertEquals(
            "Connected to Navidrome 0.55.0. Smart playlist saves are enabled.",
            navidromeConnectionSuccessStatus(
                validation = validation,
                connection = navidromeConnection(nativeToken = "native"),
                password = "secret",
                smartPlaylistAuthWarning = null,
            ),
        )
        assertEquals(
            "Connected to Navidrome 0.55.0. Smart playlist saves are not enabled: native auth unavailable",
            navidromeConnectionSuccessStatus(
                validation = validation,
                connection = navidromeConnection(),
                password = "secret",
                smartPlaylistAuthWarning = "native auth unavailable",
            ),
        )
        assertEquals(
            "Connected to Navidrome 0.55.0. Smart playlist saves require editing this connection and entering your password.",
            navidromeConnectionSuccessStatus(
                validation = validation,
                connection = navidromeConnection(),
                password = "",
                smartPlaylistAuthWarning = null,
            ),
        )
    }
}

private fun navidromeConnection(nativeToken: String? = null): NavidromeConnection =
    NavidromeConnection(
        baseUrl = "https://music.example.test",
        username = "demo",
        token = "token",
        salt = "salt",
        nativeToken = nativeToken,
    )
