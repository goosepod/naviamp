package app.naviamp.provider.navidrome

import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.ConnectionSecondaryUrl
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
            validateConnection = {},
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
            validateConnection = {},
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
            validateConnection = {},
            nativeTokenFromPassword = { connection, _, _ -> connection.copy(nativeToken = "native") },
        )

        assertEquals("native", prepared.connection.nativeToken)
        assertNull(prepared.nativeAuthErrorMessage)
    }

    @Test
    fun choosesFirstReachableFallbackUrl() = runTest {
        val attempts = mutableListOf<String>()
        val prepared = prepareNavidromeConnection(
            NavidromeConnectionLoginRequest(
                baseUrl = "https://primary.example.test",
                secondaryUrls = listOf(
                    ConnectionSecondaryUrl(url = "https://fallback.example.test"),
                ),
                username = "demo",
                password = "secret",
                displayName = "Home Music",
                tlsSettings = ConnectionTlsSettings(),
                savedConnectionForLogin = null,
            ),
            validateConnection = { connection ->
                attempts += connection.baseUrl
                if (connection.baseUrl == "https://primary.example.test") error("primary unavailable")
            },
            nativeTokenFromPassword = { connection, _, _ -> connection },
        )

        assertEquals(listOf("https://primary.example.test", "https://fallback.example.test"), attempts)
        assertEquals("https://fallback.example.test", prepared.connection.baseUrl)
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
