package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ConnectionValidation
import kotlin.test.Test
import kotlin.test.assertEquals

class NavidromeConnectionStatusTest {
    @Test
    fun successStatusIncludesServerVersionWithoutSmartPlaylistNoise() {
        val validation = ConnectionValidation(serverVersion = "0.55.0", apiVersion = "1.16.1")

        assertEquals(
            "Connected to Navidrome 0.55.0.",
            navidromeConnectionSuccessStatus(
                validation = validation,
            ),
        )
        assertEquals(
            "Connected to Navidrome 0.55.0.",
            navidromeConnectionSuccessStatus(
                validation = validation,
            ),
        )
        assertEquals(
            "Connected to Navidrome 0.55.0.",
            navidromeConnectionSuccessStatus(
                validation = validation,
            ),
        )
    }
}
