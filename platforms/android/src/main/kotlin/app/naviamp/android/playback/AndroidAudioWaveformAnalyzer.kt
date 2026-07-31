package app.naviamp.android.playback

import android.net.Uri
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveformAnalyzer
import app.naviamp.domain.waveform.BassAudioWaveformAnalyzer

/** Android URI translation and mutable TLS effect for Core's shared BASS analyzer. */
class AndroidAudioWaveformAnalyzer(
    bass: BassAudioBackend,
) : AudioWaveformAnalyzer {
    private val delegate = BassAudioWaveformAnalyzer(
        bass = bass,
        localFilePath = ::localFileFromUrl,
    )

    override suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform? =
        delegate.analyze(source)
}

private fun localFileFromUrl(url: String): String? =
    runCatching {
        val uri = Uri.parse(url)
        if (uri.scheme == "file") requireNotNull(uri.path) else null
    }.getOrNull()
