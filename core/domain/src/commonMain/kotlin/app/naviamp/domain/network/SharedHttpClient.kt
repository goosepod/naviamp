package app.naviamp.domain.network

interface SharedHttpClient {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String?
}

expect fun String.urlEncodedParameter(): String
