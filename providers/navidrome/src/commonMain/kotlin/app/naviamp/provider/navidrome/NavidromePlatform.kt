package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient

expect fun navidromeMd5(value: String): String

expect fun String.urlEncode(): String

expect fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient

expect fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings): HttpClient

internal expect fun navidromeCurrentTimeMillis(): Long

