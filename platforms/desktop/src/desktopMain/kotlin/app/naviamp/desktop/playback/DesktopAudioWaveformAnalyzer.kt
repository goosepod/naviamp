package app.naviamp.desktop

import app.naviamp.desktop.playback.bass.loadDesktopBassAudioBackend
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalyzer as DomainAudioWaveformAnalyzer
import app.naviamp.domain.waveform.analyzeBassFloatPcmWaveform
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.exists

class DesktopAudioWaveformAnalyzer(
    private val backendResult: Result<BassAudioBackend> = loadDesktopBassAudioBackend(),
    private val bucketCount: Int = DefaultWaveformBucketCount,
) : DomainAudioWaveformAnalyzer {
    override suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform? =
        analyze(source.streamUrl, source.bucketCount)

    fun analyze(audioPath: Path, bucketCount: Int = this.bucketCount): AudioWaveform? {
        if (!audioPath.exists()) return null
        val bass = backendResult.getOrNull() ?: return null
        val stream = bass.createFileDecodeStream(audioPath.toString()).getOrNull() ?: return null
        return try {
            analyzeBassFloatPcmWaveform(
                bass = bass,
                stream = stream,
                bucketCount = bucketCount,
            )
        } finally {
            bass.freeStream(stream)
        }
    }

    private fun analyze(streamUrl: String, bucketCount: Int): AudioWaveform? {
        val localPath = localPathFromUrl(streamUrl)
        if (localPath != null) return analyze(localPath, bucketCount)
        val bass = backendResult.getOrNull() ?: return null
        bass.configureInternetStreams().getOrElse { return null }
        val stream = bass.createUrlDecodeStream(streamUrl).getOrNull() ?: return null
        return try {
            analyzeBassFloatPcmWaveform(
                bass = bass,
                stream = stream,
                bucketCount = bucketCount,
            )
        } finally {
            bass.freeStream(stream)
        }
    }
}

private fun localPathFromUrl(url: String): Path? =
    runCatching {
        val uri = URI(url)
        if (uri.scheme == "file") Path.of(uri) else null
    }.getOrNull()
