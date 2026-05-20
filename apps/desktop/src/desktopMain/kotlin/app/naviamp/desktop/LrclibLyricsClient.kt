package app.naviamp.desktop

import app.naviamp.domain.lyrics.LrclibLyricsProvider
import java.net.http.HttpClient

class LrclibLyricsClient(
    httpClient: HttpClient = HttpClient.newHttpClient(),
    baseUrl: String = "https://lrclib.net",
) : LrclibLyricsProvider(DesktopSharedHttpClient(httpClient), baseUrl)
