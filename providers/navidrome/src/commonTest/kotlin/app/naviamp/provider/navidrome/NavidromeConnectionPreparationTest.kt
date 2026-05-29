package app.naviamp.provider.navidrome

import app.naviamp.domain.source.ConnectionTlsSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavidromeConnectionPreparationTest {
    @Test
    fun reusesSavedCredentialsWhenPasswordIsBlank() = runTest {
        val savedConnection = navidromeConnection(token = "saved-token", nativeToken = "native")
        val prepared = prepareNavidromeConnection(
            NavidromeConnectionLoginRequest(
                baseUrl = "https://music.example.test",
                username = "demo",
                password = "",
                displayName = "Home Music",
                tlsSettings = ConnectionTlsSettings(customCertificatePath = "/cert.pem"),
                savedConnectionForLogin = savedConnection,
            ),
        )

        assertEquals("saved-token", prepared.connection.token)
        assertEquals("native", prepared.connection.nativeToken)
        assertEquals("Home Music", prepared.connection.displayName)
        assertEquals("/cert.pem", prepared.connection.tlsSettings.customCertificatePath)
        assertNull(prepared.nativeAuthErrorMessage)
    }

    @Test
    fun keepsPasswordConnectionWhenNativeAuthFails() = runTest {
        val prepared = prepareNavidromeConnection(
            NavidromeConnectionLoginRequest(
                baseUrl = "https://music.example.test",
                username = "demo",
                password = "secret",
                displayName = "Home Music",
                tlsSettings = ConnectionTlsSettings(),
                savedConnectionForLogin = null,
                nativeAuthRequired = true,
            ),
            nativeTokenFromPassword = { _, _, _ -> error("native auth unavailable") },
        )

        assertNull(prepared.connection.nativeToken)
        assertEquals("native auth unavailable", prepared.nativeAuthErrorMessage)
    }

    @Test
    fun storesNativeTokenWhenAuthSucceeds() = runTest {
        val prepared = prepareNavidromeConnection(
            NavidromeConnectionLoginRequest(
                baseUrl = "https://music.example.test",
                username = "demo",
                password = "secret",
                displayName = "Home Music",
                tlsSettings = ConnectionTlsSettings(),
                savedConnectionForLogin = null,
            ),
            nativeTokenFromPassword = { connection, _, _ -> connection.copy(nativeToken = "native") },
        )

        assertEquals("native", prepared.connection.nativeToken)
        assertNull(prepared.nativeAuthErrorMessage)
    }
}

private fun navidromeConnection(
    token: String = "token",
    nativeToken: String? = null,
): NavidromeConnection =
    NavidromeConnection(
        baseUrl = "https://music.example.test",
        username = "demo",
        token = token,
        salt = "salt",
        nativeToken = nativeToken,
    )
