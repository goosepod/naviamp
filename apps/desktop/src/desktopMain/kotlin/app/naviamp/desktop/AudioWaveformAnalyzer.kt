package app.naviamp.desktop

import app.naviamp.desktop.playback.MpvExecutableResolver
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

class AudioWaveformAnalyzer(
    private val mpvExecutable: Path? = MpvExecutableResolver().resolve()?.toPath(),
    private val bucketCount: Int = DefaultBucketCount,
) {
    fun analyze(audioPath: Path): AudioWaveform? {
        val mpv = mpvExecutable ?: return null
        if (!audioPath.exists()) return null

        val tempDirectory = Files.createTempDirectory("naviamp-waveform-")
        val wavPath = tempDirectory.resolve("decoded.wav")
        return try {
            val process = ProcessBuilder(
                mpv.toAbsolutePath().toString(),
                "--no-config",
                "--no-video",
                "--really-quiet",
                "--untimed",
                "--ao=pcm",
                "--ao-pcm-file=${wavPath.toAbsolutePath()}",
                "--audio-format=s16",
                "--audio-channels=mono",
                "--audio-samplerate=8000",
                audioPath.toAbsolutePath().toString(),
            )
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            if (process.exitValue() != 0 || !wavPath.exists()) return null

            parseWaveform(wavPath, bucketCount)
        } catch (_: Exception) {
            null
        } finally {
            runCatching {
                Files.walk(tempDirectory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                }
            }
        }
    }
}

private fun parseWaveform(wavPath: Path, bucketCount: Int): AudioWaveform? {
    val bytes = Files.readAllBytes(wavPath)
    if (bytes.size < WavHeaderMinimumBytes) return null
    if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF") return null
    if (String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE") return null

    var offset = 12
    var bitsPerSample: Int? = null
    var dataOffset: Int? = null
    var dataSize: Int? = null

    while (offset + 8 <= bytes.size) {
        val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
        val chunkSize = bytes.intLe(offset + 4)
        val chunkDataOffset = offset + 8
        if (chunkDataOffset + chunkSize > bytes.size) break

        when (chunkId) {
            "fmt " -> {
                if (chunkSize >= 16) {
                    bitsPerSample = bytes.shortLe(chunkDataOffset + 14).toInt()
                }
            }
            "data" -> {
                dataOffset = chunkDataOffset
                dataSize = chunkSize
            }
        }

        offset = chunkDataOffset + chunkSize + (chunkSize % 2)
    }

    if (bitsPerSample != 16) return null
    val start = dataOffset ?: return null
    val size = dataSize ?: return null
    val sampleCount = size / 2
    if (sampleCount <= 0) return null

    val bucketAmplitudes = FloatArray(bucketCount)
    var maxBucketAmplitude = 0f

    repeat(bucketCount) { bucket ->
        val sampleStart = ((bucket / bucketCount.toFloat()) * sampleCount).toInt()
        val sampleEnd = ceil(((bucket + 1) / bucketCount.toFloat()) * sampleCount).toInt()
            .coerceAtMost(sampleCount)
        if (sampleStart >= sampleEnd) return@repeat

        var sumSquares = 0.0
        var peak = 0f
        var count = 0
        var sampleIndex = sampleStart
        while (sampleIndex < sampleEnd) {
            val sampleOffset = start + sampleIndex * 2
            val amplitude = abs(bytes.shortLe(sampleOffset).toInt()) / Short.MAX_VALUE.toFloat()
            sumSquares += (amplitude * amplitude).toDouble()
            if (amplitude > peak) peak = amplitude
            count += 1
            sampleIndex += 1
        }

        if (count > 0) {
            val rms = sqrt(sumSquares / count).toFloat()
            val blended = rms * 0.82f + peak * 0.18f
            bucketAmplitudes[bucket] = blended
            if (blended > maxBucketAmplitude) {
                maxBucketAmplitude = blended
            }
        }
    }

    if (maxBucketAmplitude <= 0f) {
        return AudioWaveform(List(bucketCount) { 0f })
    }

    return AudioWaveform(
        amplitudes = bucketAmplitudes.map { (it / maxBucketAmplitude).coerceIn(0f, 1f) },
    )
}

private fun ByteArray.shortLe(offset: Int): Short =
    ByteBuffer.wrap(this, offset, 2)
        .order(ByteOrder.LITTLE_ENDIAN)
        .short

private fun ByteArray.intLe(offset: Int): Int =
    ByteBuffer.wrap(this, offset, 4)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int

private const val DefaultBucketCount = 180
private const val WavHeaderMinimumBytes = 44
