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
    return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }.take(32)
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

expect fun sha256(bytes: ByteArray): ByteArray
