package app.naviamp.desktop

import app.naviamp.desktop.playback.PlaybackSource

fun shouldReplayCurrentForSeek(playbackSource: PlaybackSource): Boolean =
    playbackSource == PlaybackSource.ProviderStream ||
        playbackSource == PlaybackSource.ProviderStreamCacheDisabled
