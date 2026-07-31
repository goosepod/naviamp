package app.naviamp.domain.cache

import app.naviamp.domain.TrackId
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioByteStoreService(
    private val store: AudioByteStore,
    private val httpClient: SharedHttpClient,
) {
    private val inFlightMutex = Mutex()
    private val inFlightWrites = mutableMapOf<String, CompletableDeferred<Result<StoredAudioBytes>>>()

    suspend fun writeProviderAudio(
        sourceId: String,
        trackId: TrackId,
        qualityKey: String,
        contentType: String?,
        provider: MediaProvider,
        streamUrl: String,
        errorMessage: String,
    ): StoredAudioBytes {
        val inFlightKey = "$sourceId:${trackId.value}:$qualityKey"
        var ownsWrite = false
        val writeResult = inFlightMutex.withLock {
            inFlightWrites[inFlightKey] ?: CompletableDeferred<Result<StoredAudioBytes>>()
                .also { deferred ->
                    inFlightWrites[inFlightKey] = deferred
                    ownsWrite = true
                }
        }
        if (!ownsWrite) {
            return writeResult.await().getOrThrow()
        }

        val result = runCatching {
            writeProviderAudioUncoordinated(
                sourceId = sourceId,
                trackId = trackId,
                qualityKey = qualityKey,
                contentType = contentType,
                provider = provider,
                streamUrl = streamUrl,
                errorMessage = errorMessage,
            )
        }
        writeResult.complete(result)
        inFlightMutex.withLock {
            if (inFlightWrites[inFlightKey] === writeResult) {
                inFlightWrites.remove(inFlightKey)
            }
        }
        return result.getOrThrow()
    }

    private suspend fun writeProviderAudioUncoordinated(
        sourceId: String,
        trackId: TrackId,
        qualityKey: String,
        contentType: String?,
        provider: MediaProvider,
        streamUrl: String,
        errorMessage: String,
    ): StoredAudioBytes =
        store.writeAudioBytes(
            fileName = stableAudioFileName(sourceId, trackId.value, qualityKey) + contentType.audioExtension(),
            errorMessage = errorMessage,
            writeBytes = { writer ->
                provider.downloadStream(streamUrl, httpClient) { bytes, count ->
                    writer.write(bytes, count)
                }
            },
        ).also { stored ->
            if (stored.sizeBytes <= 0L) {
                store.deleteAudioBytes(stored.filePath)
                throw IllegalStateException(errorMessage)
            }
        }

    fun deleteAudio(filePath: String) {
        store.deleteAudioBytes(filePath)
    }
}

fun stableAudioFileName(sourceId: String, trackId: String, qualityKey: String): String {
    val digest = sha256("$sourceId:$trackId:$qualityKey".encodeToByteArray())
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        for (byte in digest) {
            val value = byte.toInt() and 0xff
            append(hex[value shr 4])
            append(hex[value and 0x0f])
        }
    }.take(32)
}

/** Defense-in-depth check used before deleting an audio path recorded in Naviamp storage. */
fun isNaviampOwnedAudioFileName(fileName: String): Boolean {
    val extensionIndex = fileName.lastIndexOf('.')
    if (extensionIndex <= 0) return false
    val stem = fileName.substring(0, extensionIndex)
    val extension = fileName.substring(extensionIndex).lowercase()
    return stem.length in setOf(32, 40, 64) &&
        stem.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } &&
        extension in setOf(".mp3", ".aac", ".flac", ".ogg", ".opus", ".m4a", ".wav", ".audio")
}

fun String?.audioExtension(): String =
    when (this?.lowercase()?.substringBefore(";")?.trim()) {
        "audio/mpeg", "audio/mp3" -> ".mp3"
        "audio/aac", "audio/aacp" -> ".aac"
        "audio/flac", "audio/x-flac" -> ".flac"
        "audio/ogg", "application/ogg" -> ".ogg"
        "audio/opus" -> ".opus"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> ".m4a"
        "audio/wav", "audio/wave", "audio/x-wav" -> ".wav"
        else -> ".audio"
    }

fun sha256(bytes: ByteArray): ByteArray = NaviampSha256.digest(bytes)
