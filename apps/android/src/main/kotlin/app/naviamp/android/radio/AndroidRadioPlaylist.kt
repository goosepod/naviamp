package app.naviamp.android

import app.naviamp.domain.radio.InternetRadioStreamResolver

suspend fun resolveInternetRadioStreamUrl(stationUrl: String): String =
    InternetRadioStreamResolver().resolve(stationUrl)
