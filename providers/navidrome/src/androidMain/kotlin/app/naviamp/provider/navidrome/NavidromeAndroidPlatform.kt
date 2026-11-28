package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient

actual fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient =
    KtorNavidromeHttpClient(createDefaultNavidromeKtorClient(tlsSettings))

actual fun createScopedNavidromeKtorClient(tlsSettings: NavidromeTlsSettings): HttpClient =
    createNavidromeJvmKtorClient(tlsSettings)

actual fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings, onTlsChallenge: (NavidromeTlsChallengeEvent) -> Unit): HttpClient =
    createNavidromeJvmKtorClient(tlsSettings)
