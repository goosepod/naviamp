package app.naviamp.provider.navidrome

data class NavidromeConnection(
    val baseUrl: String,
    val username: String,
) {
    val normalizedBaseUrl: String =
        baseUrl.trim().trimEnd('/')
}

