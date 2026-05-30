package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class KtorNavidromeHttpClient(
    private val client: HttpClient = createDefaultNavidromeKtorClient(NavidromeTlsSettings()),
) : NavidromeHttpClient {
    override suspend fun get(url: String): String =
        request(url = url, method = HttpMethod.Get)

    override suspend fun get(url: String, headers: Map<String, String>): String =
        request(url = url, method = HttpMethod.Get, headers = headers)

    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String =
        request(url = url, method = HttpMethod.Post, body = body, headers = headers)

    override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String =
        request(url = url, method = HttpMethod.Put, body = body, headers = headers)

    private suspend fun request(
        url: String,
        method: HttpMethod,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val startedAt = navidromeCurrentTimeMillis()
        return runCatching {
            val response = client.request(url) {
                this.method = method
                headers {
                    (DefaultNavidromeHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                throw NavidromeException("Navidrome returned HTTP $statusCode.")
            }
            response.body<String>().also {
                recordApiCall(
                    method = method.value,
                    url = url,
                    startedAt = startedAt,
                    success = true,
                    errorMessage = null,
                )
            }
        }.getOrElse { error ->
            recordApiCall(
                method = method.value,
                url = url,
                startedAt = startedAt,
                success = false,
                errorMessage = error.message ?: error::class.simpleName,
            )
            throw error
        }
    }

    private fun recordApiCall(
        method: String,
        url: String,
        startedAt: Long,
        success: Boolean,
        errorMessage: String?,
    ) {
        recordNavidromeApiCall(
            url = url,
            method = method,
            startedAt = startedAt,
            durationMillis = (navidromeCurrentTimeMillis() - startedAt).coerceAtLeast(0),
            success = success,
            errorMessage = errorMessage,
        )
    }
}

private val DefaultNavidromeHeaders = mapOf(
    HttpHeaders.Accept to "application/json",
    HttpHeaders.UserAgent to "Naviamp/0.9.0",
)
