package app.naviamp.desktop

import app.naviamp.desktop.playback.bass.loadDesktopBassAudioBackend
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.BassAudioWaveformAnalyzer
import app.naviamp.domain.waveform.AudioWaveformAnalyzer as DomainAudioWaveformAnalyzer
import java.net.URI

/** Desktop native-library loading and file-URI translation for Core's shared BASS analyzer. */
class DesktopAudioWaveformAnalyzer(
    private val backendResult: Result<BassAudioBackend> = loadDesktopBassAudioBackend(),
) : DomainAudioWaveformAnalyzer {
    private val delegate = backendResult.getOrNull()?.let { bass ->
        BassAudioWaveformAnalyzer(
            bass = bass,
            localFilePath = ::localPathFromUrl,
        )
    }

    suspend fun prepare() {
        backendResult.getOrThrow().init().getOrThrow()
    }

    override suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform? =
        delegate?.analyze(source)
}

private fun localPathFromUrl(url: String): String? =
    runCatching {
        val uri = URI(url)
        if (uri.scheme == "file") java.nio.file.Path.of(uri).toString() else null
    }.getOrNull()
