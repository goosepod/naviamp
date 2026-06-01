package app.naviamp.desktop

import app.naviamp.domain.playback.PlaybackSource

fun shouldReplayCurrentForSeek(playbackSource: PlaybackSource): Boolean =
    playbackSource == PlaybackSource.ProviderStream ||
        playbackSource == PlaybackSource.ProviderStreamCacheDisabled
