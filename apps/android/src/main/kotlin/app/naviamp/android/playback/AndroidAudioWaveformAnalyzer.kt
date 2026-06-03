package app.naviamp.android.playback

import android.content.Context
import android.net.Uri
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveformAnalyzer
import app.naviamp.domain.waveform.DefaultWaveformBucketCount
import app.naviamp.domain.waveform.normalizeFloatPcmWaveform
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidAudioWaveformAnalyzer(
    context: Context,
    private val bass: AndroidBassJni,
    private val bucketCount: Int = DefaultWaveformBucketCount,
) : AudioWaveformAnalyzer {
    private val cacheDirectory = File(context.cacheDir, "waveforms").apply { mkdirs() }
    private var tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings()

    fun applyTlsSettings(tlsSettings: NavidromeTlsSettings) {
        this.tlsSettings = tlsSettings
    }

    override suspend fun analyze(source: AudioWaveformAnalysisSource): AudioWaveform? =
        analyze(trackId = source.cacheKey, streamUrl = source.streamUrl)

    suspend fun analyze(trackId: String, streamUrl: String): AudioWaveform? =
        withContext(Dispatchers.IO) {
            val cached = cachedWaveform(trackId)
            if (cached != null) return@withContext cached
            bass.setVerifyNet(!tlsSettings.insecureSkipTlsVerification)
            val stream = createDecodeStream(streamUrl)
            if (stream == 0) return@withContext null
            try {
                val totalSamples = bass.lengthBytes(stream)
                    ?.let { it / Float.SIZE_BYTES }
                    ?: return@withContext null
                var readError = false
                val waveform = normalizeFloatPcmWaveform(
                    totalSamples = totalSamples,
                    bucketCount = bucketCount,
                    readSamples = { buffer ->
                        bass.readFloatData(stream, buffer).also { read ->
                            if (read < 0) readError = true
                        }
                    },
                ) ?: return@withContext null
                if (readError) return@withContext null
                waveform.also { cacheWaveform(trackId, it) }
            } finally {
                bass.freeStream(stream)
            }
        }

    private fun createDecodeStream(streamUrl: String): Int {
        val localFile = localFileFromUrl(streamUrl)
        return if (localFile != null) {
            bass.createFileDecodeStream(localFile.absolutePath)
        } else {
            bass.createUrlDecodeStream(streamUrl)
        }
    }

    private fun cachedWaveform(trackId: String): AudioWaveform? {
        val cacheFile = File(cacheDirectory, "${trackId.safeFileName()}.$WaveformCacheVersion.waveform")
        if (!cacheFile.isFile) return null
        val amplitudes = cacheFile.readText()
            .split(',')
            .mapNotNull { it.toFloatOrNull()?.coerceIn(0f, 1f) }
        return amplitudes.takeIf { it.isNotEmpty() }?.let(::AudioWaveform)
    }

    private fun cacheWaveform(trackId: String, waveform: AudioWaveform) {
        File(cacheDirectory, "${trackId.safeFileName()}.$WaveformCacheVersion.waveform")
            .writeText(waveform.amplitudes.joinToString(","))
    }
}

private fun localFileFromUrl(url: String): File? =
    runCatching {
        val uri = Uri.parse(url)
        if (uri.scheme == "file") File(requireNotNull(uri.path)) else null
    }.getOrNull()

private fun String.safeFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "track" }

private const val WaveformCacheVersion = "bass-v4"
