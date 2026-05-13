package app.naviamp.android.playback

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AndroidAudioWaveformAnalyzer(
    context: Context,
    private val bucketCount: Int = DefaultBucketCount,
) {
    private val cacheDirectory = File(context.cacheDir, "waveforms").apply { mkdirs() }

    suspend fun analyze(trackId: String, streamUrl: String): List<Float>? =
        withContext(Dispatchers.IO) {
            val audioFile = downloadToCache(trackId, streamUrl) ?: return@withContext null
            decodePeaks(audioFile)
        }

    private fun downloadToCache(trackId: String, streamUrl: String): File? {
        val target = File(cacheDirectory, "${trackId.safeFileName()}.audio")
        if (target.exists() && target.length() > 0L) return target

        val temporary = File(cacheDirectory, "${target.name}.tmp")
        return runCatching {
            URL(streamUrl).openStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var totalBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MaxCachedAudioBytes) return@runCatching null
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (temporary.length() <= 0L) return@runCatching null
            temporary.renameTo(target)
            target
        }.getOrNull().also {
            if (it == null) temporary.delete()
        }
    }

    private fun decodePeaks(audioFile: File): List<Float>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(audioFile.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val peaks = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        codec.getOutputBuffer(outputIndex)?.let { outputBuffer ->
                            if (bufferInfo.size > 0) {
                                peaks += outputBuffer.peak(bufferInfo.offset, bufferInfo.size)
                            }
                        }
                        outputDone = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            peaks.toBuckets(bucketCount)
        } catch (_: Exception) {
            null
        } finally {
            codec?.runCatching { stop() }
            codec?.runCatching { release() }
            extractor.release()
        }
    }
}

private fun ByteBuffer.peak(offset: Int, size: Int): Float {
    val duplicate = duplicate()
        .order(ByteOrder.LITTLE_ENDIAN)
    duplicate.position(offset)
    duplicate.limit(offset + size)

    var peak = 0f
    while (duplicate.remaining() >= 2) {
        val sample = abs(duplicate.short.toInt()) / Short.MAX_VALUE.toFloat()
        if (sample > peak) peak = sample
    }
    return peak.coerceIn(0f, 1f)
}

private fun List<Float>.toBuckets(bucketCount: Int): List<Float>? {
    if (isEmpty()) return null
    val buckets = FloatArray(bucketCount)
    forEachIndexed { index, peak ->
        val bucket = ((index / lastIndex.coerceAtLeast(1).toFloat()) * (bucketCount - 1)).toInt()
        if (peak > buckets[bucket]) buckets[bucket] = peak
    }
    val max = buckets.maxOrNull() ?: return null
    if (max <= 0f) return List(bucketCount) { 0f }
    return buckets.map { (it / max).coerceIn(0f, 1f) }
}

private fun String.safeFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "track" }

private const val DefaultBucketCount = 180
private const val MaxCachedAudioBytes = 120L * 1024L * 1024L
