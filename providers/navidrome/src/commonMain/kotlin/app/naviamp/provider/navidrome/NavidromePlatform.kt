package app.naviamp.provider.navidrome

expect fun navidromeMd5(value: String): String

expect fun String.urlEncode(): String

expect fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient

