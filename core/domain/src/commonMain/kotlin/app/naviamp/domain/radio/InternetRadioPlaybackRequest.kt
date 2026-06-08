package app.naviamp.domain.radio

import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.ReplayGainMode

data class InternetRadioPlaybackRequestPlan(
    val request: PlaybackRequest,
)

fun planInternetRadioPlaybackRequest(
    startPlan: InternetRadioStartPlan,
    streamUrl: String,
    replayGainMode: ReplayGainMode,
): InternetRadioPlaybackRequestPlan =
    InternetRadioPlaybackRequestPlan(
        request = PlaybackRequest(
            url = streamUrl,
            mediaId = startPlan.engineMediaId,
            replayGainMode = if (startPlan.replayGainOff) ReplayGainMode.Off else replayGainMode,
        ),
    )
