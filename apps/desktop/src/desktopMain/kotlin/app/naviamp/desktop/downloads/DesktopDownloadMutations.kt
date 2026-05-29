package app.naviamp.desktop

import app.naviamp.domain.cache.downloadTracksForPlayback

fun desktopDownloadTracksForPlayback(downloads: List<DownloadedTrack>, index: Int) =
    downloadTracksForPlayback(downloads, index) { it.track }
