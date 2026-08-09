package app.naviamp.domain.lyrics

import app.naviamp.domain.network.SharedHttpClient
/** Complete shared online-lyrics provider catalog; hosts supply only their HTTP engine and clock. */
fun naviampOnlineLyricsProviders(
    httpClient: SharedHttpClient,
): List<LyricsProvider> = listOf(
    LrclibLyricsProvider(httpClient),
    LrcmuxLyricsProvider(httpClient),
)
