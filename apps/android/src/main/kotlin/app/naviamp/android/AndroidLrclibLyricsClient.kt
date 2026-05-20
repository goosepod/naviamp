package app.naviamp.android

import app.naviamp.domain.lyrics.LrclibLyricsProvider

class AndroidLrclibLyricsClient(
    baseUrl: String = "https://lrclib.net",
) : LrclibLyricsProvider(AndroidSharedHttpClient(), baseUrl)
