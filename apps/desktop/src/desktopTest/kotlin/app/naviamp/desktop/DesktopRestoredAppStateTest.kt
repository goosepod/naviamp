package app.naviamp.desktop

import app.naviamp.provider.navidrome.NavidromeConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopRestoredAppStateTest {
    @Test
    fun `missing native token is restored from matching saved connection`() {
        val current = connection(nativeToken = null)

        val restored = current.withNativeTokenFrom(connection(nativeToken = "native-token"))

        assertEquals("native-token", restored.nativeToken)
    }

    @Test
    fun `native token is not copied from a different account`() {
        val current = connection(nativeToken = null)
        val differentAccount = connection(username = "someone-else", nativeToken = "native-token")

        val restored = current.withNativeTokenFrom(differentAccount)

        assertNull(restored.nativeToken)
    }

    @Test
    fun `existing native token remains authoritative`() {
        val current = connection(nativeToken = "current-token")

        val restored = current.withNativeTokenFrom(connection(nativeToken = "fallback-token"))

        assertEquals("current-token", restored.nativeToken)
    }

    private fun connection(
        username: String = "listener",
        nativeToken: String?,
    ) = NavidromeConnection(
        baseUrl = "https://music.example",
        username = username,
        token = "token",
        salt = "salt",
        nativeToken = nativeToken,
    )
}
