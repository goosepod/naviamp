package app.naviamp.android.playback

import android.content.Context
import android.net.Uri
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.DefaultWaveformBucketCount
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidAudioWaveformAnalyzer(
    context: Context,
    private val bass: AndroidBassJni,
    private val bucketCount: Int = DefaultWaveformBucketCount,
) {
    private val cacheDirectory = File(context.cacheDir, "waveforms").apply { mkdirs() }
    private var tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings()

    fun applyTlsSettings(tlsSettings: NavidromeTlsSettings) {
        this.tlsSettings = tlsSettings
    }

    suspend fun analyze(trackId: String, streamUrl: String): AudioWaveform? =
        withContext(Dispatchers.IO) {
            val cached = cachedWaveform(trackId)
            if (cached != null) return@withContext cached
            bass.setVerifyNet(!tlsSettings.insecureSkipTlsVerification)
            val stream = createDecodeStream(streamUrl)
            if (stream == 0) return@withContext null
            try {
                val amplitudes = bass.decodeWaveform(stream, bucketCount)
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.coerceIn(0f, 1f) }
                    ?: return@withContext null
                AudioWaveform(amplitudes).also { cacheWaveform(trackId, it) }
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
