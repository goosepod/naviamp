package app.naviamp.provider.navidrome

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val NativeAuthJson = Json { ignoreUnknownKeys = true }

suspend fun NavidromeConnection.withNativeTokenFromPassword(
    password: String,
    httpClient: NavidromeHttpClient = createDefaultNavidromeHttpClient(tlsSettings),
    required: Boolean = false,
): NavidromeConnection {
    val tokenResult = runCatching {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val response = httpClient.postJson(
            url = "$normalizedBaseUrl/auth/login",
            body = NativeAuthJson.encodeToString(JsonObject.serializer(), body),
        )
        NativeAuthJson.parseToJsonElement(response).jsonObject.stringValue("token")
            ?: throw NavidromeException("Navidrome native login did not return a token.")
    }
    if (required && tokenResult.isFailure) {
        throw NavidromeException(
            tokenResult.exceptionOrNull()?.message ?: "Could not authenticate with Navidrome's native API.",
        )
    }

    return copy(nativeToken = tokenResult.getOrNull())
}

private fun JsonObject.stringValue(key: String): String? =
    this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
