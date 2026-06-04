package app.naviamp.desktop

import app.naviamp.domain.Lyrics
import app.naviamp.domain.waveform.AudioWaveform

data class DesktopNowPlayingAnalysis(
    val waveform: AudioWaveform?,
    val waveformStatus: String,
    val audioTags: List<AudioTag>,
    val lyrics: Lyrics?,
)
