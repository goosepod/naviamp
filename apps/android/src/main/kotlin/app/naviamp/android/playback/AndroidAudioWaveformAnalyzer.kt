package app.naviamp.android.playback

import android.content.Context
import android.net.Uri
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveformAnalyzer
import app.naviamp.domain.waveform.analyzeBassFloatPcmWaveform
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidAudioWaveformAnalyzer(
    context: Context,
    private val bass: BassAudioBackend,
) : AudioWaveformAnalyzer {
    private val cacheDirectory = File(context.cacheDir, "waveforms").apply { mkdirs() }
    private var tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings()

    fun applyTlsSettings(tlsSettings: NavidromeTlsSettings) {
        this.tlsSettings = tlsSettings
    }

    override suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform? =
        analyze(trackId = source.cacheKey, streamUrl = source.streamUrl, bucketCount = source.bucketCount)

    suspend fun analyze(trackId: String, streamUrl: String, bucketCount: Int): AudioWaveform? =
        withContext(Dispatchers.IO) {
            val cached = cachedWaveform(trackId, bucketCount)
            if (cached != null) return@withContext cached
            bass.setVerifyNet(!tlsSettings.insecureSkipTlsVerification)
                .getOrElse { return@withContext null }
            bass.configureInternetStreams()
                .getOrElse { return@withContext null }
            val stream = createDecodeStream(streamUrl)
                .getOrElse { return@withContext null }
            try {
                val waveform = analyzeBassFloatPcmWaveform(
                    bass = bass,
                    stream = stream,
                    bucketCount = bucketCount,
                ) ?: return@withContext null
                waveform.also { cacheWaveform(trackId, bucketCount, it) }
            } finally {
                bass.freeStream(stream)
            }
        }

    private fun createDecodeStream(streamUrl: String): Result<BassStreamHandle> {
        val localFile = localFileFromUrl(streamUrl)
        return if (localFile != null) {
            bass.createFileDecodeStream(localFile.absolutePath)
        } else {
            bass.createUrlDecodeStream(streamUrl)
        }
    }

    private fun cachedWaveform(trackId: String, bucketCount: Int): AudioWaveform? {
        val cacheFile = cacheFile(trackId, bucketCount)
        if (!cacheFile.isFile) return null
        val amplitudes = cacheFile.readText()
            .split(',')
            .mapNotNull { it.toFloatOrNull()?.coerceIn(0f, 1f) }
        return amplitudes.takeIf { it.isNotEmpty() }?.let(::AudioWaveform)
    }

    private fun cacheWaveform(trackId: String, bucketCount: Int, waveform: AudioWaveform) {
        cacheFile(trackId, bucketCount)
            .writeText(waveform.amplitudes.joinToString(","))
    }

    private fun cacheFile(trackId: String, bucketCount: Int): File =
        File(cacheDirectory, "${trackId.safeFileName()}.$WaveformCacheVersion.$bucketCount.waveform")
}

private fun localFileFromUrl(url: String): File? =
    runCatching {
        val uri = Uri.parse(url)
        if (uri.scheme == "file") File(requireNotNull(uri.path)) else null
    }.getOrNull()

private fun String.safeFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "track" }

private const val WaveformCacheVersion = "bass-v9"
