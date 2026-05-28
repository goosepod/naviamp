package app.naviamp.desktop

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.StreamQualityMode
import app.naviamp.domain.settings.streamQualityForNetwork

fun PlaybackSettings.streamQuality(playbackEngine: PlaybackEngine): StreamQuality =
    if (playbackEngine.prefersOriginalStream) {
        streamQualityForNetwork(isMobileData = false)
    } else {
        copy(wifiStreamingQuality = wifiStreamingQuality.copy(mode = StreamQualityMode.Transcode))
            .streamQualityForNetwork(isMobileData = false)
    }
