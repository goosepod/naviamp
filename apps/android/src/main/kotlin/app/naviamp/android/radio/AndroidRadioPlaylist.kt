package app.naviamp.android

import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.network.NaviampUserAgent
import app.naviamp.domain.radio.InternetRadioStreamResolver

suspend fun resolveInternetRadioStreamUrl(stationUrl: String): String =
    AndroidInternetRadioStreamResolver.resolve(stationUrl)

private val AndroidInternetRadioStreamResolver = InternetRadioStreamResolver(
    KtorSharedHttpClient(
        defaultHeaders = mapOf(
            "User-Agent" to NaviampUserAgent,
            "Icy-MetaData" to "1",
        ),
    ),
)
