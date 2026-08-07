package app.naviamp.provider.jellyfin

import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.ConnectionHeaderDefinition
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JellyfinSessionServiceTest {
    @Test
    fun authenticationUsesCurrentJellyfinContractAndValidatesTheSession() = runTest {
        val http = RecordingJellyfinHttpClient(
            postResponse = JellyfinHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "AccessToken": "access-token",
                      "User": { "Id": "user-id", "Name": "Alice" },
                      "SessionInfo": { "ServerId": "session-server" }
                    }
                """.trimIndent(),
            ),
            getResponse = JellyfinHttpResponse(
                statusCode = 200,
                body = """{"Id":"server-id","ServerName":"Music","Version":"10.11.11"}""",
            ),
        )
        val service = JellyfinSessionService(http, identity())

        val connection = service.authenticate(
            JellyfinAuthenticationRequest(
                baseUrl = " https://music.example.test/ ",
                username = " alice ",
                password = "secret",
                displayName = "Home",
                customHeaders = listOf(ConnectionHeaderDefinition("X-Reverse-Proxy", "allowed")),
            ),
        )

        assertEquals("https://music.example.test/Users/AuthenticateByName", http.postUrl)
        val body = Json.parseToJsonElement(http.postBody!!).jsonObject
        assertEquals("alice", body.getValue("Username").jsonPrimitive.content)
        assertEquals("secret", body.getValue("Pw").jsonPrimitive.content)
        assertEquals(
            "MediaBrowser Client=\"Naviamp\", Device=\"Mac\", DeviceId=\"device-id\", Version=\"1.0\"",
            http.postHeaders.getValue("Authorization"),
        )
        assertEquals("allowed", http.postHeaders["X-Reverse-Proxy"])
        assertEquals("https://music.example.test/System/Info", http.getUrl)
        assertTrue(http.getHeaders.single().getValue("Authorization").contains("Token=\"access-token\""))
        assertEquals("allowed", http.getHeaders.single()["X-Reverse-Proxy"])
        assertEquals("Alice", connection.username)
        assertEquals("user-id", connection.userId)
        assertEquals("server-id", connection.serverId)
        assertEquals("10.11.11", connection.serverVersion)
    }

    @Test
    fun restoreValidatesTheSavedTokenWithoutRequestingThePasswordAgain() = runTest {
        val http = RecordingJellyfinHttpClient(
            getResponses = listOf(
                JellyfinHttpResponse(200, """{"Id":"user-id","Name":"Alice"}"""),
                JellyfinHttpResponse(200, """{"Id":"server-id","Version":"10.11.11"}"""),
            ),
        )
        val saved = SavedMediaSource(
            id = "source",
            providerId = "jellyfin",
            cacheNamespace = "jellyfin:server-id:user-id",
            displayName = "Home",
            baseUrl = "https://music.example.test",
            username = "alice",
            token = "",
            salt = "",
            nativeToken = "saved-token",
            createdAtEpochMillis = 1,
            lastConnectedAtEpochMillis = null,
            lastSyncStartedAtEpochMillis = null,
            lastSyncCompletedAtEpochMillis = null,
        )

        val restored = JellyfinSessionService(http, identity()).restore(saved)

        assertEquals("10.11.11", restored.serverVersion)
        assertEquals("user-id", restored.userId)
        assertEquals("Alice", restored.username)
        assertEquals(null, http.postUrl)
        assertEquals(
            listOf(
                "https://music.example.test/Users/Me",
                "https://music.example.test/System/Info",
            ),
            http.getUrls,
        )
        assertTrue(http.getHeaders.last().getValue("Authorization").contains("Token=\"saved-token\""))
    }

    @Test
    fun failedAuthenticationReturnsAPlainActionableError() = runTest {
        val service = JellyfinSessionService(
            RecordingJellyfinHttpClient(postResponse = JellyfinHttpResponse(401, "sensitive server response")),
            identity(),
        )

        val error = assertFailsWith<JellyfinException> {
            service.authenticate(
                JellyfinAuthenticationRequest("https://music.example.test", "alice", "wrong"),
            )
        }

        assertTrue(error.message.orEmpty().contains("Check the username"))
        assertFalse(error.message.orEmpty().contains("sensitive server response"))
    }

    @Test
    fun revokedSavedTokenRequestsDirectTheUserToReconnect() = runTest {
        val service = JellyfinSessionService(
            RecordingJellyfinHttpClient(getResponse = JellyfinHttpResponse(401, "sensitive server response")),
            identity(),
        )

        val error = assertFailsWith<JellyfinException> {
            service.getJson(
                connection = JellyfinConnection(
                    baseUrl = "https://music.example.test",
                    username = "alice",
                    accessToken = "revoked-token",
                    userId = "user-id",
                    deviceId = "device-id",
                ),
                path = "Items/album-1",
            )
        }

        assertEquals(
            "Your Jellyfin session is no longer valid. Open Connections and reconnect with your password.",
            error.message,
        )
        assertFalse(error.message.orEmpty().contains("sensitive server response"))
    }

    @Test
    fun persistedConnectionUsesOnlyTheOpaqueTokenSlot() {
        val connection = JellyfinConnection(
            baseUrl = "https://music.example.test/",
            username = "alice",
            accessToken = "saved-token",
            userId = "user-id",
            deviceId = "device-id",
        )

        val persisted = connection.toProviderMediaSourceConnection()

        assertEquals("", persisted.token)
        assertEquals("", persisted.salt)
        assertEquals("saved-token", persisted.nativeToken)
    }

    @Test
    fun savedConnectionRejectsTheWrongProviderType() {
        val saved = SavedMediaSource(
            id = "source",
            providerId = "navidrome",
            cacheNamespace = "cache",
            displayName = "Server",
            baseUrl = "https://music.example.test",
            username = "alice",
            token = "token",
            salt = "salt",
            createdAtEpochMillis = 1,
            lastConnectedAtEpochMillis = null,
            lastSyncStartedAtEpochMillis = null,
            lastSyncCompletedAtEpochMillis = null,
        )

        assertFailsWith<IllegalArgumentException> {
            saved.toJellyfinConnection(deviceId = "device", userId = "user")
        }
    }

    private fun identity(): JellyfinClientIdentity =
        JellyfinClientIdentity(
            deviceId = "device-id",
            deviceName = "Mac",
            clientVersion = "1.0",
        )
}

private class RecordingJellyfinHttpClient(
    getResponse: JellyfinHttpResponse = JellyfinHttpResponse(500, ""),
    getResponses: List<JellyfinHttpResponse> = listOf(getResponse),
    private val postResponse: JellyfinHttpResponse = JellyfinHttpResponse(500, ""),
) : JellyfinHttpClient {
    private val pendingGetResponses = getResponses.toMutableList()
    var getUrl: String? = null
    val getUrls = mutableListOf<String>()
    val getHeaders = mutableListOf<Map<String, String>>()
    var postUrl: String? = null
    var postBody: String? = null
    var postHeaders: Map<String, String> = emptyMap()

    override suspend fun get(url: String, headers: Map<String, String>): JellyfinHttpResponse {
        getUrl = url
        getUrls += url
        getHeaders += headers
        return pendingGetResponses.removeFirstOrNull() ?: JellyfinHttpResponse(500, "")
    }

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): JellyfinHttpResponse {
        postUrl = url
        postBody = body
        postHeaders = headers
        return postResponse
    }
}
