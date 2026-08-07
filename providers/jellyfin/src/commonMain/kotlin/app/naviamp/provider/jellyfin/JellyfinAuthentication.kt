package app.naviamp.provider.jellyfin

import app.naviamp.domain.network.NaviampUserAgent
import app.naviamp.domain.network.urlEncodedParameter
import app.naviamp.domain.provider.ProviderIdJellyfin
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.normalizedBaseUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class JellyfinClientIdentity(
    val deviceId: String,
    val deviceName: String,
    val clientName: String = "Naviamp",
    val clientVersion: String,
) {
    init {
        require(deviceId.isNotBlank()) { "A stable device identifier is required for Jellyfin." }
        require(deviceName.isNotBlank()) { "A device name is required for Jellyfin." }
        require(clientName.isNotBlank()) { "A client name is required for Jellyfin." }
        require(clientVersion.isNotBlank()) { "A client version is required for Jellyfin." }
    }

    fun authorizationHeader(accessToken: String? = null): String =
        buildList {
            add("Client=\"${clientName.jellyfinHeaderValue()}\"")
            add("Device=\"${deviceName.jellyfinHeaderValue()}\"")
            add("DeviceId=\"${deviceId.jellyfinHeaderValue()}\"")
            add("Version=\"${clientVersion.jellyfinHeaderValue()}\"")
            accessToken?.takeIf { it.isNotBlank() }?.let {
                add("Token=\"${it.jellyfinHeaderValue()}\"")
            }
        }.joinToString(prefix = "MediaBrowser ", separator = ", ")
}

data class JellyfinHttpResponse(
    val statusCode: Int,
    val body: String,
) {
    val successful: Boolean
        get() = statusCode in 200..299
}

data class JellyfinBinaryResponse(
    val statusCode: Int,
    val body: ByteArray,
) {
    val successful: Boolean
        get() = statusCode in 200..299
}

interface JellyfinHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): JellyfinHttpResponse

    suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): JellyfinHttpResponse

    suspend fun post(url: String, headers: Map<String, String> = emptyMap()): JellyfinHttpResponse =
        throw UnsupportedOperationException("POST is not supported by this Jellyfin HTTP client.")

    suspend fun delete(url: String, headers: Map<String, String> = emptyMap()): JellyfinHttpResponse =
        throw UnsupportedOperationException("DELETE is not supported by this Jellyfin HTTP client.")

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): JellyfinBinaryResponse =
        throw UnsupportedOperationException("Binary GET is not supported by this Jellyfin HTTP client.")

    suspend fun download(
        url: String,
        headers: Map<String, String> = emptyMap(),
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean = throw UnsupportedOperationException("Streaming download is not supported by this Jellyfin HTTP client.")
}

data class JellyfinAuthenticationRequest(
    val baseUrl: String,
    val username: String,
    val password: String,
    val displayName: String? = null,
    val tlsSettings: ConnectionTlsSettings = ConnectionTlsSettings(),
    val secondaryUrls: List<ConnectionSecondaryUrl> = emptyList(),
    val customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
    val selectedMusicFolderIds: List<String> = emptyList(),
)

class JellyfinSessionService(
    private val httpClient: JellyfinHttpClient,
    private val identity: JellyfinClientIdentity,
) {
    suspend fun authenticate(request: JellyfinAuthenticationRequest): JellyfinConnection {
        val baseUrl = normalizedBaseUrl(request.baseUrl)
        require(baseUrl.isNotEmpty()) { "Enter a Jellyfin server URL." }
        require(request.username.isNotBlank()) { "Enter a Jellyfin username." }
        require(request.password.isNotEmpty()) { "Enter a Jellyfin password." }

        val response = httpClient.postJson(
            url = "$baseUrl/Users/AuthenticateByName",
            body = buildJsonObject {
                put("Username", request.username.trim())
                put("Pw", request.password)
            }.toString(),
            headers = requestHeaders(customHeaders = request.customHeaders),
        )
        response.requireSuccess("Jellyfin authentication failed", authenticationAttempt = true)
        val payload = response.jsonObject("Jellyfin returned an invalid authentication response")
        val accessToken = payload.string("AccessToken")
            ?: throw JellyfinException("Jellyfin did not return an access token.")
        val user = payload["User"] as? JsonObject
            ?: throw JellyfinException("Jellyfin did not return the authenticated user.")
        val userId = user.string("Id")
            ?: throw JellyfinException("Jellyfin did not return a user identifier.")
        val username = user.string("Name") ?: request.username.trim()
        val session = payload["SessionInfo"] as? JsonObject

        return JellyfinConnection(
            baseUrl = baseUrl,
            username = username,
            accessToken = accessToken,
            userId = userId,
            deviceId = identity.deviceId,
            serverId = session?.string("ServerId"),
            displayName = request.displayName,
            tlsSettings = request.tlsSettings,
            secondaryUrls = request.secondaryUrls.mapNotNull { it.normalized() },
            customHeaders = request.customHeaders.mapNotNull { it.normalized() },
            selectedMusicFolderIds = request.selectedMusicFolderIds.map(String::trim).filter(String::isNotEmpty).distinct(),
        ).withValidatedServerInfo()
    }

    suspend fun restore(connection: JellyfinConnection): JellyfinConnection {
        val resolved = if (connection.userId == PendingJellyfinUserId) {
            val response = httpClient.get(
                url = "${connection.normalizedBaseUrl}/Users/Me",
                headers = requestHeaders(connection.accessToken, connection.customHeaders),
            )
            response.requireSuccess("The saved Jellyfin session is no longer valid")
            val user = response.jsonObject("Jellyfin returned invalid user information")
            connection.copy(
                userId = user.string("Id")
                    ?: throw JellyfinException("Jellyfin did not return a user identifier."),
                username = user.string("Name") ?: connection.username,
            )
        } else {
            connection
        }
        return resolved.withValidatedServerInfo()
    }

    internal suspend fun getJson(
        connection: JellyfinConnection,
        path: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ): JsonObject {
        val response = httpClient.get(
            url = connection.apiUrl(path, parameters),
            headers = requestHeaders(connection.accessToken, connection.customHeaders),
        )
        response.requireSuccess("Jellyfin request failed")
        return response.jsonObject("Jellyfin returned an invalid response")
    }

    internal suspend fun getBytes(connection: JellyfinConnection, url: String): ByteArray? {
        if (!connection.ownsUrl(url)) return null
        val response = httpClient.getBytes(
            url = url,
            headers = requestHeaders(connection.accessToken, connection.customHeaders),
        )
        return response.body.takeIf { response.successful }
    }

    internal suspend fun download(
        connection: JellyfinConnection,
        url: String,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean {
        if (!connection.ownsUrl(url)) return false
        return httpClient.download(
            url = url,
            headers = requestHeaders(connection.accessToken, connection.customHeaders),
            writeChunk = writeChunk,
        )
    }

    internal suspend fun postJson(
        connection: JellyfinConnection,
        path: String,
        body: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ): JsonObject {
        val response = httpClient.postJson(
            url = connection.apiUrl(path, parameters),
            body = body,
            headers = requestHeaders(connection.accessToken, connection.customHeaders),
        )
        response.requireSuccess("Jellyfin request failed")
        return response.jsonObject("Jellyfin returned an invalid response")
    }

    internal suspend fun postJsonWithoutResponse(
        connection: JellyfinConnection,
        path: String,
        body: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ) {
        val response = httpClient.postJson(
            url = connection.apiUrl(path, parameters),
            body = body,
            headers = requestHeaders(connection.accessToken, connection.customHeaders),
        )
        response.requireSuccess("Jellyfin request failed")
    }

    internal suspend fun post(
        connection: JellyfinConnection,
        path: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ) {
        val response = httpClient.post(
            url = connection.apiUrl(path, parameters),
            headers = requestHeaders(connection.accessToken, connection.customHeaders),
        )
        response.requireSuccess("Jellyfin request failed")
    }

    internal suspend fun delete(
        connection: JellyfinConnection,
        path: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ) {
        val response = httpClient.delete(
            url = connection.apiUrl(path, parameters),
            headers = requestHeaders(connection.accessToken, connection.customHeaders),
        )
        response.requireSuccess("Jellyfin request failed")
    }

    internal suspend fun setFavorite(
        connection: JellyfinConnection,
        itemId: String,
        favorite: Boolean,
    ) {
        val path = "UserFavoriteItems/$itemId"
        val parameters = listOf("userId" to connection.userId)
        if (favorite) post(connection, path, parameters) else delete(connection, path, parameters)
    }

    /** Restores a persisted Jellyfin token and resolves the user id instead of persisting it. */
    suspend fun restore(saved: SavedMediaSource): JellyfinConnection {
        require(saved.providerId == ProviderIdJellyfin) { "Saved connection is not a Jellyfin connection." }
        val accessToken = saved.nativeToken?.takeIf { it.isNotBlank() }
            ?: throw JellyfinException("The saved Jellyfin session has no access token.")
        val baseUrl = normalizedBaseUrl(saved.baseUrl)
        val response = httpClient.get(
            url = "$baseUrl/Users/Me",
            headers = requestHeaders(accessToken, saved.customHeaders),
        )
        response.requireSuccess("The saved Jellyfin session is no longer valid")
        val user = response.jsonObject("Jellyfin returned invalid user information")
        val userId = user.string("Id")
            ?: throw JellyfinException("Jellyfin did not return a user identifier.")
        return saved.toJellyfinConnection(
            deviceId = identity.deviceId,
            userId = userId,
            resolvedUsername = user.string("Name") ?: saved.username,
        ).withValidatedServerInfo()
    }

    private suspend fun JellyfinConnection.withValidatedServerInfo(): JellyfinConnection {
        val response = httpClient.get(
            url = "$normalizedBaseUrl/System/Info",
            headers = requestHeaders(accessToken, customHeaders),
        )
        response.requireSuccess("The saved Jellyfin session is no longer valid")
        val payload = response.jsonObject("Jellyfin returned invalid server information")
        return copy(
            serverId = payload.string("Id") ?: serverId,
            serverVersion = payload.string("Version") ?: serverVersion,
        )
    }

    private fun requestHeaders(
        accessToken: String? = null,
        customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
    ): Map<String, String> =
        customHeaders.mapNotNull { header ->
            val normalized = header.normalized() ?: return@mapNotNull null
            val value = normalized.value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            normalized.name to value
        }.toMap() + mapOf(
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "User-Agent" to NaviampUserAgent,
            "Authorization" to identity.authorizationHeader(accessToken),
        )
}

class JellyfinException(message: String) : RuntimeException(message)

private fun JellyfinHttpResponse.requireSuccess(
    action: String,
    authenticationAttempt: Boolean = false,
) {
    if (!successful) {
        val message = when (statusCode) {
            401 -> if (authenticationAttempt) {
                "$action. Check the username and password."
            } else {
                "Your Jellyfin session is no longer valid. Open Connections and reconnect with your password."
            }
            403 -> "$action. Jellyfin denied this action; check the server permissions."
            404 -> "$action. Check that the server address points to Jellyfin."
            else -> "$action (HTTP $statusCode)."
        }
        throw JellyfinException(message)
    }
}

private fun JellyfinHttpResponse.jsonObject(action: String): JsonObject =
    runCatching { Json.parseToJsonElement(body).jsonObject }
        .getOrElse { throw JellyfinException("$action.") }

private fun JsonObject.string(name: String): String? =
    runCatching { this[name]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

private fun String.jellyfinHeaderValue(): String {
    require(none { it == '\r' || it == '\n' }) { "Jellyfin client identity contains an invalid header character." }
    return replace("\\", "\\\\").replace("\"", "\\\"")
}

internal fun JellyfinConnection.apiUrl(
    path: String,
    parameters: List<Pair<String, String>> = emptyList(),
): String = buildString {
    append(normalizedBaseUrl)
    append('/')
    append(path.trimStart('/'))
    if (parameters.isNotEmpty()) {
        append('?')
        append(parameters.joinToString("&") { (name, value) ->
            "${name.jellyfinUrlEncode()}=${value.jellyfinUrlEncode()}"
        })
    }
}

internal fun JellyfinConnection.ownsUrl(url: String): Boolean =
    url == normalizedBaseUrl || url.startsWith("$normalizedBaseUrl/")

private fun String.jellyfinUrlEncode(): String = urlEncodedParameter()
